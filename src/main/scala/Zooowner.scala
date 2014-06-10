package com.ataraxer.zooowner

import com.ataraxer.zooowner.message._

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.Watcher.Event.{KeeperState, EventType}
import org.apache.zookeeper.CreateMode._
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.ZooDefs.Ids

import scala.concurrent.duration._
import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Exception._

import scala.language.implicitConversions


object Zooowner {
  type Action = () => Unit
  type Reaction[T] = PartialFunction[T, Unit]

  def default[T]: Reaction[T] = { case _ => }

  val AnyVersion = -1
  val AnyACL = Ids.OPEN_ACL_UNSAFE
  val Root = ""

  implicit def durationToInt(duration: FiniteDuration) =
    duration.toMillis.toInt

  implicit class SlashSeparatedPath(path: String) {
    def / (subpath: String) = path + "/" + subpath
  }

  def createMode(persistent: Boolean, sequential: Boolean) = {
    (persistent, sequential) match {
      case (true, true)   => PERSISTENT_SEQUENTIAL
      case (true, false)  => PERSISTENT
      case (false, true)  => EPHEMERAL_SEQUENTIAL
      case (false, false) => EPHEMERAL
    }
  }

  def parentPaths(path: String) = {
    var parentPath = ""
    var result = ArrayBuffer.empty[String]

    for (nextPart <- path.split("/")) {
      parentPath = (parentPath/nextPart).replaceAll("^/", "")
      if (parentPath != path) result += parentPath
    }

    result
  }
}


/**
 * ZooKeeper client that doesn't make you cry.
 *
 * @param servers Connection string, consisting of comma separated host:port
 * values.
 * @param timeout Connection timeout.
 * @param pathPrefix Default prefix for operations via that client instance.
 */
class Zooowner(servers: String,
               timeout: FiniteDuration,
               val pathPrefix: String)
{
  import Zooowner._

  require(pathPrefix matches "^\\w+$", "path prefix should be simple identifier")

  /*
   * Hook-function, that will be called when connection to ZooKeeper
   * server is established.
   */
  protected var connectionHook: Reaction[ConnectionEvent] =
    default[ConnectionEvent]

  /**
   * Internal watcher, that controls ZooKeeper connection life-cycle.
   */
  protected val watcher = StateWatcher {
    case KeeperState.SyncConnected => {
      assert { isConnected == true }

      ignoring(classOf[NodeExistsException]) {
        create(Root/pathPrefix, persistent = true)
      }

      connectionHook(Connected)
    }

    case KeeperState.Disconnected | KeeperState.Expired => {
      connectionHook(Disconnected)
      connect()
    }
  }

  /**
   * Internal ZooKeeper client, through which all interactions with ZK are
   * being performed.
   */
  protected var client: ZooKeeper = generateClient

  /**
   * Returns path prefixed with [[pathPrefix]]
   */
  protected def prefixedPath(path: String) = {
    require(!(path startsWith "/"), "path shouldn't start from slash")
    Root/pathPrefix/path
  }

  /**
   * Path resolver, that distincts between absolute paths starting with `/`
   * character and paths relative to [[pathPrefix]].
   */
  protected def resolvePath(path: String) =
    if (path startsWith "/") path else prefixedPath(path)

  /**
   * Initiates connection to ZooKeeper server.
   */
  protected def connect(): Unit = {
    disconnect()
    client = generateClient
  }

  /**
   * Generates new ZooKeeper client.
   */
  protected def generateClient =
    new ZooKeeper(servers, timeout.toMillis.toInt, watcher)

  /**
   * Disconnects from ZooKeeper server.
   */
  protected def disconnect(): Unit = {
    client.close()
    connectionHook(Disconnected)
  }

  /**
   * Sets up a partial callback-function that will be called on client
   * connection status change.
   */
  def watchConnection(reaction: Reaction[ConnectionEvent]) = {
    connectionHook = reaction orElse default[ConnectionEvent]
    if (isConnected) reaction(Connected)
  }

  /**
   * Tests whether the connection to ZooKeeper server is established.
   */
  def isConnected =
    client != null && client.getState == States.CONNECTED

  /**
   * Initiates disonnection from ZooKeeper server and performs clean up.
   */
  def close(): Unit = {
    disconnect()
  }

  /**
   * Takes a function to be called on client taking care of ensuring that it's
   * called with active instance of ZooKeeper client.
   */
  def apply[T](call: ZooKeeper => T): T = {
    if (!isConnected) connect()

    def perform = {
      connectionHook(Disconnected)
      connect()
      apply(call)
    }

    try call(client) catch {
      case _: SessionExpiredException => perform
      case _: ConnectionLossException => perform
    }
  }

  /**
   * Creates new node.
   *
   * @param path Path of node to be created.
   * @param maybeData Optional data that should be stored in created node.
   * @param persistent Specifies whether created node should be persistent.
   * @param sequential Specifies whether created node should be sequential.
   * @param recursive Specifies whether path to the node should be created.
   * @param filler Optional value with which path nodes should be created.
   */
  def create(path: String,
             maybeData: Option[String] = None,
             persistent: Boolean = false,
             sequential: Boolean = false,
             recursive: Boolean = false,
             filler: Option[String] = None): Unit =
  {
    if (recursive) {
      for (parentPath <- parentPaths(path)) {
        ignoring(classOf[NodeExistsException]) {
          create(parentPath, filler, persistent = true, recursive = false)
        }
      }
    }

    val data = maybeData.map( _.getBytes("utf8") ).orNull

    this { client =>
      client.create(
        resolvePath(path), data, AnyACL,
        createMode(persistent, sequential))
    }
  }

  /**
   * Gets node state and optionally sets watcher.
   */
  def stat(path: String, maybeWatcher: Option[EventWatcher] = None) = {
    val watcher = maybeWatcher.orNull
    if (maybeWatcher.isDefined) activeWatchers :+= watcher
    this { client =>
      Option { client.exists(resolvePath(path), watcher) }
    }
  }

  /**
   * Tests whether the node exists.
   */
  def exists(path: String) = stat(path) != None

  /**
   * Returns Some(value) of the node if exists, None otherwise.
   */
  def get(path: String, maybeWatcher: Option[EventWatcher] = None) = {
    val watcher = maybeWatcher.orNull
    if (maybeWatcher.isDefined) activeWatchers :+= watcher

    val maybeData = this { client =>
      catching(classOf[NoNodeException]).opt {
        client.getData(resolvePath(path), watcher, null)
      }
    }

    for {
      data  <- maybeData
      value <- Option(data)
    } yield new String(value)
  }

  /**
   * Sets a new value for the node.
   */
  def set(path: String, data: String): Unit = {
    this { _.setData(resolvePath(path), data.getBytes, AnyVersion) }
  }

  /**
   * Deletes node.
   */
  def delete(path: String, recursive: Boolean = false): Unit = {
    if (recursive) {
      for (child <- children(path)) {
        val childPath = path/child
        delete(childPath, recursive = true)
      }
    }

    this { client =>
      client.delete(resolvePath(path), AnyVersion)
    }
  }

  /**
   * Returns list of children of the node.
   */
  def children(path: String, maybeWatcher: Option[EventWatcher] = None) = {
    val watcher = maybeWatcher.orNull
    if (maybeWatcher.isDefined) activeWatchers :+= watcher
    this { client =>
      client.getChildren(resolvePath(path), watcher).toList
    }
  }

  /**
   * Tests whether the node is ephemeral.
   */
  def isEphemeral(path: String) =
    stat(path).map( _.getEphemeralOwner != 0).getOrElse(false)

  /**
   * Stores all active node watchers.
   */
  protected var activeWatchers = List.empty[EventWatcher]

  def removeAllWatchers(): Unit = {
    activeWatchers foreach { _.stop() }
    activeWatchers = Nil
  }

  /**
   * Sets up a callback for node events.
   */
  def watch(path: String, persistent: Boolean = true)
           (reaction: Reaction[Event]): EventWatcher =
  {
    val reactOn = reaction orElse default[Event]

    val watcher = new EventWatcher {
      def self: Option[EventWatcher] =
        if (persistent) Some(this) else None

      def reaction = {
        case EventType.NodeCreated => {
          // child watcher isn't set yet for that node so
          // we need to set it up if watcher is persistent
          if (persistent) watch(path, this)

          // since `watch` takes care of setting both data
          // and children watches there is no need to
          // set watcher again via `get`
          reactOn { NodeCreated(path, get(path)) }
        }

        case EventType.NodeDataChanged => reactOn {
          NodeChanged(path, get(path, self))
        }

        case EventType.NodeChildrenChanged => reactOn {
          NodeChildrenChanged(path, children(path, self))
        }

        case EventType.NodeDeleted => {
          // after node deletion we still may be interested
          // in watching it, in that case -- reset watcher
          if (persistent) watch(path, this)

          reactOn { NodeDeleted(path, 0) }
        }
      }
    }

    watch(path, watcher)
  }

  /**
   * Sets up a watcher on node events.
   */
  def watch(path: String, watcher: EventWatcher): EventWatcher = {
    stat(path, Some(watcher))

    // node may not exist yet, so we ignore NoNode exceptions
    ignoring(classOf[NoNodeException]) {
      children(path, Some(watcher))
    }

    watcher
  }

}


// vim: set ts=2 sw=2 et:
