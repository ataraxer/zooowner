package com.ataraxer.zooowner

import scala.concurrent.duration._

import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.Watcher.Event.{KeeperState, EventType}
import org.apache.zookeeper.CreateMode._
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.ZooDefs.Ids

import scala.collection.JavaConversions._
import scala.util.control.Exception._

import com.ataraxer.zooowner.event._

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
  import KeeperState._

  // path prefix should be simple identifier
  if (pathPrefix contains "/") {
    throw new IllegalArgumentException
  }

  /*
   * Hook-function, that will be called when connection to ZooKeeper
   * server is established.
   */
  private var connectionHook: Option[Action] = None

  /**
   * Internal watcher, that controls ZooKeeper connection life-cycle.
   */
  private val watcher = StateWatcher {
    case SyncConnected => {
      assert { isConnected == true }

      ignoring(classOf[NodeExistsException]) {
        create(Root/pathPrefix, persistent = true)
      }

      connectionHook foreach { action => action() }
    }

    case Disconnected | Expired => connect()
  }

  /**
   * Internal ZooKeeper client, through which all interactions with ZK are
   * being performed.
   */
  private var client: ZooKeeper = generateClient

  /**
   * Returns path prefixed with [[pathPrefix]]
   */
  private def prefixedPath(path: String) = {
    // path should always start from slash
    if (path startsWith "/") {
      throw new IllegalArgumentException
    }

    Root/pathPrefix/path
  }

  /**
   * Path resolver, that distincts between absolute paths starting with `/`
   * character and paths relative to [[pathPrefix]].
   */
  private def resolvePath(path: String) =
    if (path startsWith "/") path else prefixedPath(path)

  /**
   * Initiates connection to ZooKeeper server.
   */
  private def connect(): Unit = {
    disconnect()
    client = generateClient
  }

  /**
   * Generates new ZooKeeper client.
   */
  def generateClient =
    new ZooKeeper(servers, timeout.toMillis.toInt, watcher)

  /**
   * Disconnects from ZooKeeper server.
   */
  private def disconnect(): Unit = {
    client.close()
  }

  /**
   * Sets hook-function, that will be called when connection to ZooKeeper
   * server is established.
   */
  def onConnection(action: Action): Unit = {
    connectionHook = Some(action)
    if (isConnected) action()
  }

  /**
   * Tests whether the connection to ZooKeeper server is established.
   */
  def isConnected =
    client.getState == States.CONNECTED

  /**
   * Initiates disonnection from ZooKeeper server and performs clean up.
   */
  def close(): Unit = {
    disconnect()
  }

  /**
   * Creates new node.
   *
   * @param path Path of node to be created.
   * @param maybeData Optional data that should be stored in created node.
   * @param persistent Specifies whether created node should be persistent.
   * @param sequential Specifies whether created node should be sequential.
   * @param recursive Specifies whether path to the node should be created.
   * @param filler Value with which path nodes should be created.
   */
  def create(path: String,
             maybeData: Option[String] = None,
             persistent: Boolean = false,
             sequential: Boolean = false,
             recursive: Boolean = false,
             filler: Option[String] = None): Unit =
  {
    if (recursive) {
      val parts = path.split("/")

      var parentPath = ""
      for (nextPart <- parts) {
        parentPath = (parentPath/nextPart).replaceAll("^/", "")
        if (parentPath != path) {
          ignoring(classOf[NodeExistsException]) {
            create(parentPath, filler, persistent = true)
          }
        }
      }
    }

    val createMode = (persistent, sequential) match {
      case (true, true)   => PERSISTENT_SEQUENTIAL
      case (true, false)  => PERSISTENT
      case (false, true)  => EPHEMERAL_SEQUENTIAL
      case (false, false) => EPHEMERAL
    }

    val data = maybeData.map( _.getBytes("utf8") ).orNull

    client.create(resolvePath(path), data, AnyACL, createMode)
  }

  /**
   * Gets node state and optionally sets watcher.
   */
  def stat(path: String, maybeWatcher: Option[EventWatcher] = None) = {
    val watcher = maybeWatcher.orNull
    if (maybeWatcher.isDefined) activeWatchers :+= watcher
    Option { client.exists(resolvePath(path), watcher) }
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
    val maybeData = catching(classOf[NoNodeException]).opt {
      client.getData(resolvePath(path), watcher, null)
    }
    maybeData map { new String(_) }
  }

  /**
   * Sets a new value for the node.
   */
  def set(path: String, data: String): Unit = {
    client.setData(resolvePath(path), data.getBytes, AnyVersion)
  }

  /**
   * Deletes node.
   */
  def delete(path: String, recursive: Boolean = false): Unit = {
    if (recursive) {
      for (child <- children(path)) {
        val childPath = path + "/" + child
        delete(childPath, recursive = true)
      }
    }

    client.delete(resolvePath(path), AnyVersion)
  }

  /**
   * Returns list of children of the node.
   */
  def children(path: String, maybeWatcher: Option[EventWatcher] = None) = {
    val watcher = maybeWatcher.orNull
    if (maybeWatcher.isDefined) activeWatchers :+= watcher
    client.getChildren(resolvePath(path), watcher)
  }

  /**
   * Tests whether the node is ephemeral.
   */
  def isEphemeral(path: String) =
    stat(path).map( _.getEphemeralOwner != 0).getOrElse(false)

  /**
   * Stores all active node watchers.
   */
  private var activeWatchers = List.empty[EventWatcher]

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
          // we need to set it up if watcher is persistant
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

          reactOn { NodeDeleted(path) }
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
