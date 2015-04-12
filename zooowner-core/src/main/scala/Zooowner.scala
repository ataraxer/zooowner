package zooowner

import zooowner.message._
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._


trait Zooowner {
  import Zooowner._

  /**
   * Takes a function to be called on client taking care of ensuring that it's
   * called with active instance of ZooKeeper client.
   */
  def apply(path: ZKPath, watcher: Option[ZKEventWatcher] = None): ZKNode

  /**
   * Sets a new value for the node.
   */
  def update[T: ZKEncoder](path: ZKPath, value: T) = set(path, value)

  /*
   * Active ZooKeeper client, through which all interactions with ZK are
   * being performed.
   */
  def client: ZKClient

  /**
   * Waits for connection to esablish within given timeout.
   *
   * @param timeout Amount of time to wait for connection
   */
  def awaitConnection(timeout: FiniteDuration = 5.seconds): Unit

  /**
   * Tests whether the connection to ZooKeeper server is established.
   */
  def isConnected: Boolean

  /**
   * Establishes new sesssion if current one is expired.
   *
   * @param force Reconnect even if current connection is active.
   */
  def reconnect(force: Boolean = false): Unit

  /**
   * Disconnects from ZooKeeper server.
   */
  def disconnect(): Unit

  /**
   * Creates new node, ensuring that persisten path to it exists,
   * by filling missing nodes with null's, and returns it's name.
   *
   * @param path Path of node to be created.
   * @param value Optional data that should be stored in created node.
   * @param persistent Specifies whether created node should be persistent.
   * @param sequential Specifies whether created node should be sequential.
   */
  def create[T: ZKEncoder](
    path: ZKPath,
    value: T = NoData,
    persistent: Boolean = false,
    sequential: Boolean = false): ZKPath

  /**
   * Creates a new node under existing persistent one and returns it's name.
   *
   * @param path Path of parent node.
   * @param child Name of a child to be created.
   * @param value Optional data that should be stored in created node.
   * @param persistent Specifies whether created node should be persistent.
   * @param sequential Specifies whether created node should be sequential.
   */
  def createChild[T: ZKEncoder](
    path: ZKPath,
    value: T = NoData,
    persistent: Boolean = false,
    sequential: Boolean = false): ZKPath

  /**
   * Creates persistent path, creating each missing node with null value.
   */
  def createPath(path: ZKPath): Unit

  /**
   * Gets node state and optionally sets watcher.
   */
  def meta(
    path: ZKPath,
    watcher: Option[ZKEventWatcher] = None): Option[ZKMeta]

  /**
   * Tests whether the node exists.
   */
  def exists(path: ZKPath, watcher: Option[ZKEventWatcher] = None) = {
    meta(path, watcher).isDefined
  }

  /**
   * Optionally returns a node.
   */
  def get(
    path: ZKPath,
    watcher: Option[ZKEventWatcher] = None): Option[ZKNode]

  /**
   * Sets a new value for the node.
   *
   * @param path Path of a node to be updated.
   * @param value Value to be saved into node.
   * @param version Expected version of an existing node.
   * @param force Create node if it does not exist.
   */
  def set[T: ZKEncoder](
    path: ZKPath,
    value: T,
    version: Int = AnyVersion): Unit

  /**
   * Sets a new value if node exists, creates it otherwise.
   *
   * @param path Path of a node to be updated.
   * @param value Value to be saved into node.
   * @param persistent Specifies whether created node should be persistent.
   */
  def forceSet[T: ZKEncoder](
    path: ZKPath,
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
    path: ZKPath,
    recursive: Boolean = false,
    version: Int = AnyVersion): Unit

  /**
   * Returns list of children of the node.
   */
  def children(
    path: ZKPath,
    watcher: Option[ZKEventWatcher] = None): Seq[ZKPath]

  /**
   * Tests whether the node is ephemeral.
   */
  def isEphemeral(path: ZKPath): Boolean

  /**
   * Tests whether the node is persistent.
   */
  def isPersistent(path: ZKPath) = !isEphemeral(path)

  /**
   * Sets up a callback for node events.
   */
  def watch(path: ZKPath)(reaction: Reaction[ZKEvent]): ZKEventWatcher

  /**
   * Sets up a watcher on node events.
   */
  def watch(path: ZKPath, watcher: ZKEventWatcher): ZKEventWatcher

  /**
   * Sets up a one-time watcher on a node, and returns a future change.
   */
  def watchData
    (path: ZKPath)
    (implicit executor: ExecutionContext): Future[ZKDataEvent]

  /**
   * Sets up a one-time watcher on a node children, and returns a future change.
   */
  def watchChildren
    (path: ZKPath)
    (implicit executor: ExecutionContext): Future[ZKChildrenEvent]
}


object Zooowner {
  /**
   * Creates new instance of [[Zooowner]] from [[ZKConnection]].
   */
  def apply(connection: ZKConnection): Zooowner = {
    new impl.ZooownerImpl(connection)
  }

  /**
   * Creates new instance of [[Zooowner]].
   *
   * @param servers ZooKeeper connection string
   * @param timeout ZooKeeper session timeout
   * @param connectionWatcher Connection events callback
   * @param session Session credentials -- id and password
   */
  def apply(
    servers: String,
    timeout: FiniteDuration,
    connectionWatcher: ZKConnectionWatcher = NoWatcher,
    session: Option[ZKSession] = None): Zooowner =
  {
    val connection = ZKConnection(
      connectionString = servers,
      sessionTimeout = timeout,
      connectionWatcher = connectionWatcher,
      session = session)

    Zooowner(connection)
  }

  /**
   * Creates new instance of [[Zooowner]].
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
    (connectionWatcher: ZKConnectionWatcher = NoWatcher): Zooowner =
  {
    Zooowner(servers, timeout, connectionWatcher, session)
  }
}


// vim: set ts=2 sw=2 et:
