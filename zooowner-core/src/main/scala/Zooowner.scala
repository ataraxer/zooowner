package zooowner

import zooowner.message._
import zooowner.ZKNodeMeta.StatConverter
import zooowner.ZKConnection.{ConnectionWatcher, NoWatcher}

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.Watcher.Event.EventType
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.CreateMode._

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


  private[zooowner] def parent(path: String) = {
    val clean = path.stripPrefix("/").stripSuffix("/")
    if (clean.isEmpty) "/" else "/" + clean.split("/").init.mkString("/")
  }


  private[zooowner] def resolvePath(path: String) = {
    if (path startsWith "/") path else Root/path
  }


  private def encode[T](data: T)(implicit encoder: ZKEncoder[T]) = {
    encoder.encode(data)
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
   * Waits for connection to esablish within given timeout.
   *
   * @param timeout Amount of time to wait for connection
   */
  def awaitConnection(timeout: FiniteDuration = 5.seconds): Unit = {
    connection.awaitConnection(timeout)
  }

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
   * Creates a node with raw data.
   */
  private def _create(
    path: String,
    data: ZKData,
    persistent: Boolean = false,
    sequential: Boolean = false): Unit =
  {
    val mode = createMode(persistent, sequential)

    this { client =>
      client.create(path, data.orNull, AnyACL, mode)
    }
  }

  /**
   * Creates new node, ensuring that persisten path to it exists.
   * All missing nodes on a path are filled with null's.
   *
   * @param path Path of node to be created.
   * @param value Optional data that should be stored in created node.
   * @param persistent Specifies whether created node should be persistent.
   * @param sequential Specifies whether created node should be sequential.
   */
  def create[T: ZKEncoder](
    path: String,
    value: T = None,
    persistent: Boolean = false,
    sequential: Boolean = false) =
  {
    val realPath = resolvePath(path)
    createPathTo(realPath)
    _create(realPath, encode(value), persistent, sequential)
  }

  /**
   * Creates a new node under existing persistent one.
   *
   * @param path Path of parent node.
   * @param child Name of a child to be created.
   * @param value Optional data that should be stored in created node.
   * @param persistent Specifies whether created node should be persistent.
   * @param sequential Specifies whether created node should be sequential.
   */
  def createChild[T: ZKEncoder](
    path: String,
    child: String,
    value: T = None,
    persistent: Boolean = false,
    sequential: Boolean = false) =
  {
    val realPath = resolvePath(path / child)
    _create(realPath, encode(value), persistent, sequential)
  }

  /**
   * Creates persistent path, creating each missing node with null value.
   */
  def createPath(path: String) = createPathTo(path / "fake")

  /**
   * Ensures persistent path exists by creating all missing nodes with
   * null value.
   *
   * @param node Node path to which has to be created.
   */
  private def createPathTo(node: String): Unit = {
    val realPath = parent(resolvePath(node))

    if (!exists(realPath)) {
      createPathTo(realPath)
      _create(realPath, None, persistent = true)
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
   *
   * @param path Path of a node to be updated.
   * @param value Value to be saved into node.
   * @param version Expected version of an existing node.
   * @param force Create node if it does not exist.
   */
  def set[T: ZKEncoder](
    path: String,
    value: T,
    version: Int = AnyVersion) =
  {
    val realPath = resolvePath(path)
    val data = encode(value)
    this { _.setData(realPath, data.orNull, version) }
  }

  /**
   * Sets a new value if node exists, creates it otherwise.
   *
   * @param path Path of a node to be updated.
   * @param value Value to be saved into node.
   * @param persistent Specifies whether created node should be persistent.
   */
  def forceSet[T: ZKEncoder](
    path: String,
    value: T,
    persistent: Boolean = true) =
  {
    if (exists(path)) {
      val bothPersistent = persistent && isPersistent(path)
      val bothEphemeral = !persistent && isEphemeral(path)

      require(
        bothPersistent || bothEphemeral,
        "Exising node should have the same creation mode as requested")

      set(path, value)
    } else {
      create(path, value, persistent = persistent)
    }
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
   * Tests whether the node is persistent.
   */
  def isPersistent(path: String) = !isEphemeral(path)

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
