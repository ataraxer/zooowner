package zooowner

import zooowner.message._
import zooowner.ZKNodeMeta.StatConverter
import zooowner.ZKConnection.{ConnectionWatcher, NoWatcher}

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.Watcher.Event.EventType
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.CreateMode._
import org.apache.zookeeper.KeeperException._

import scala.concurrent.duration._
import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Exception._

import scala.language.implicitConversions


object Zooowner {
  /**
   * Creates new instance of `Zooowner`.
   *
   * @param servers ZooKeeper connection string
   * @param timeout ZooKeeper session timeout
   * @param connectionWatcher Connection events callback
   * @param session Session credentials -- id and password
   */
  def apply(
    servers: String,
    timeout: FiniteDuration,
    connectionWatcher: ConnectionWatcher = NoWatcher,
    session: Option[ZKSession] = None)
  {
    val connection = ZKConnection(
      connectionString = servers,
      sessionTimeout = timeout,
      connectionWatcher = connectionWatcher,
      session = session)

    new Zooowner(connection)
  }

  /**
   * Creates new instance of Zooowner.
   *
   * {{{
   * Zooowner.withWatcher("localhost:2181", 5.seconds) {
   *   case Connected => println("Connection established")
   *   case Disconnected => println("Connection lost")
   * }
   * }}}
   */
  def withWatcher(
    servers: String,
    timeout: FiniteDuration,
    session: Option[ZKSession] = None)
    (connectionWatcher: ConnectionWatcher = NoWatcher) =
  {
    Zooowner(servers, timeout, connectionWatcher, session)
  }


  private[zooowner] type Reaction[T] = PartialFunction[T, Unit]

  private[zooowner] object Reaction {
    def empty[T]: Reaction[T] = { case _ => }
  }

  private[zooowner] val Root = ""

  private[zooowner] def createMode(persistent: Boolean, sequential: Boolean) = {
    (persistent, sequential) match {
      case (true, true)   => PERSISTENT_SEQUENTIAL
      case (true, false)  => PERSISTENT
      case (false, true)  => EPHEMERAL_SEQUENTIAL
      case (false, false) => EPHEMERAL
    }
  }


  private[zooowner] def parentPaths(path: String) = {
    var parentPath = new StringBuffer
    var result = ArrayBuffer.empty[String]

    val cleanPath = path.stripPrefix("/").stripSuffix("/")

    for (nextPart <- cleanPath.split("/")) {
      parentPath append "/"
      parentPath append nextPart
      if (parentPath.toString != path) result += parentPath.toString
    }

    result
  }


  private[zooowner] def resolvePath(path: String) = {
    if (path startsWith "/") path else Root/path
  }


  implicit class SlashSeparatedPath(path: String) {
    def / (subpath: String) = path + "/" + subpath
  }
}


/**
 * ZooKeeper client that doesn't make you cry.
 *
 * @param connection Connection to ZooKeeper
 */
class Zooowner(connection: ZKConnection) {
  import Zooowner._

  /**
   * Internal active connection to ZooKeeper.
   */
  private var activeConnection = connection

  /*
   * Active ZooKeeper client, through which all interactions with ZK are
   * being performed.
   */
  def client = activeConnection.client

  /**
   * Disconnects from ZooKeeper server.
   */
  def disconnect(): Unit = connection.close()

  /**
   * Attempts to extract a [[EventWatcher]] from Option, add it to the active
   * watchers set and return extracted watcher on success or just returns null
   * to be passed to ZooKeeper otherwise.
   */
  protected def resolveWatcher(maybeWatcher: Option[EventWatcher]) = {
    val watcher = maybeWatcher.orNull
    if (maybeWatcher.isDefined) activeWatchers :+= watcher
    watcher
  }

  /**
   * Blocks until client is connected.
   */
  def awaitConnection(): Unit = connection.awaitConnection

  /**
   * Tests whether the connection to ZooKeeper server is established.
   */
  def isConnected = connection.isConnected

  /**
   * Establishes new sesssion if current one is expired.
   *
   * @param force Reconnect even if current connection is active.
   */
  def reconnect(force: Boolean = false): Unit = {
    if (!isConnected || force) {
      activeConnection = activeConnection.recreate()
    }
  }

  /**
   * Takes a function to be called on client taking care of ensuring that it's
   * called with active instance of ZooKeeper client.
   */
  def apply[T](call: ZooKeeper => T): T = {
    connection.apply(call)
  }

  /**
   * Creates new node.
   *
   * @param path Path of node to be created.
   * @param value Optional data that should be stored in created node.
   * @param persistent Specifies whether created node should be persistent.
   * @param sequential Specifies whether created node should be sequential.
   * @param recursive Specifies whether path to the node should be created.
   * @param filler Optional value with which path nodes should be created.
   */
  def create[V, F](
    path: String,
    value: V = Option.empty[String],
    persistent: Boolean = false,
    sequential: Boolean = false,
    recursive: Boolean = false,
    filler: F = Option.empty[String])
    (implicit
      valueEncoder: ZKEncoder[V],
      fillerEncoder: ZKEncoder[F]): Unit =
  {
    val realPath = resolvePath(path)

    if (recursive) {
      for (parentPath <- parentPaths(realPath)) {
        ignoring(classOf[NodeExistsException]) {
          create(
            path = parentPath,
            value = filler,
            filler = filler,
            persistent = true,
            recursive = false)
        }
      }
    }

    val data = valueEncoder.encode(value).orNull

    this { client =>
      client.create(
        realPath, data, AnyACL,
        createMode(persistent, sequential))
    }
  }

  /**
   * Gets node state and optionally sets watcher.
   */
  def meta(path: String, watcher: Option[EventWatcher] = None) = {
    this { client =>
      val maybeStat = Option {
        client.exists(resolvePath(path), resolveWatcher(watcher))
      }
      maybeStat.map(_.toMeta)
    }
  }

  /**
   * Tests whether the node exists.
   */
  def exists(path: String, watcher: Option[EventWatcher] = None) = {
    meta(path, watcher) != None
  }

  /**
   * Returns Some(value) of the node if exists, None otherwise.
   */
  def get[T]
    (path: String, watcher: Option[EventWatcher] = None)
    (implicit decoder: ZKDecoder[T]): Option[T] =
  {
    getNode(path) flatMap { node =>
      Option(decoder.decode(node.data))
    }
  }

  /**
   * Returns Some[ZKNode] if node exists, Non otherwise.
   */
  def getNode(path: String, watcher: Option[EventWatcher] = None) = {
    val meta = new Stat

    val maybeData = this { client =>
      catching(classOf[NoNodeException]) opt {
        client.getData(resolvePath(path), resolveWatcher(watcher), meta)
      }
    }

    maybeData map { data =>
      ZKNode(path, Option(data), meta)
    }
  }

  /**
   * Sets a new value for the node.
   */
  def set[T]
    (path: String, value: T, version: Int = AnyVersion)
    (implicit encoder: ZKEncoder[T]): Unit =
  {
    val data = encoder.encode(value).orNull
    this { _.setData(resolvePath(path), data, version) }
  }

  /**
   * Deletes node.
   *
   * @param path Path of the node to be deleted.
   * @param recursive Specifies whether all sub-nodes should be deleted.
   * @param version Version of a node to be deleted.
   */
  def delete(
    path: String,
    recursive: Boolean = false,
    version: Int = AnyVersion): Unit =
  {
    if (recursive) {
      for (child <- children(path)) {
        val childPath = path/child
        delete(childPath, recursive = true)
      }
    }

    this { client =>
      client.delete(resolvePath(path), version)
    }
  }

  /**
   * Returns list of children of the node.
   */
  def children(
    path: String,
    absolutePaths: Boolean = false,
    watcher: Option[EventWatcher] = None) =
  {
    val maybeWatcher = resolveWatcher(watcher)
    this { client =>
      val raw = client.getChildren(resolvePath(path), maybeWatcher).toList
      if (absolutePaths) raw map { path/_ } else raw
    }
  }

  /**
   * Tests whether the node is ephemeral.
   */
  def isEphemeral(path: String) = {
    meta(path).map(_.ephemeral).getOrElse(false)
  }

  /**
   * Stores all active node watchers.
   */
  protected var activeWatchers = List.empty[EventWatcher]

  /**
   * Stops and removes all active watchers.
   */
  def clearWatchers(): Unit = {
    activeWatchers foreach { _.stop() }
    activeWatchers = Nil
  }

  /**
   * Sets up a callback for node events.
   */
  def watch
    (path: String, persistent: Boolean = true)
    (reaction: Reaction[ZKEvent]): EventWatcher =
  {
    val reactOn = reaction orElse Reaction.empty[ZKEvent]

    val watcher = new EventWatcher {
      def self: Option[EventWatcher] =
        if (persistent) Some(this) else None

      def reaction = {
        case EventType.NodeCreated => {
          if (persistent) watchChildren(path, watcher = this)
          reactOn { NodeCreated(path, getNode(path)) }
        }

        case EventType.NodeDataChanged => reactOn {
          if (persistent) watchChildren(path, watcher = this)
          NodeChanged(path, getNode(path, watcher = self))
        }

        case EventType.NodeChildrenChanged => reactOn {
          if (persistent) watchData(path, watcher = this)
          NodeChildrenChanged(path, children(path, watcher = self))
        }

        case EventType.NodeDeleted => {
          // after node deletion we still may be interested
          // in watching it, in that case -- reset watcher
          if (persistent) watch(path, watcher = this)
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
    val nodeExists = exists(path, Some(watcher))
    if (nodeExists) children(path, watcher = Some(watcher))
    watcher
  }


  private def watchData(path: String, watcher: EventWatcher): Unit = {
    exists(path, watcher = Some(watcher))
  }


  private def watchChildren(path: String, watcher: EventWatcher): Unit = {
    children(path, watcher = Some(watcher))
  }
}


// vim: set ts=2 sw=2 et:
