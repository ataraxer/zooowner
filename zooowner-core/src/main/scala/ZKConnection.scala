package zooowner

import zooowner.message._

import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.Watcher.Event.KeeperState

import scala.concurrent.Future
import scala.concurrent.duration._

import ZKConnection._


trait ZKConnection {
  /**
   * Takes a function to be called on client taking care of ensuring that it's
   * called with active instance of ZooKeeper client.
   */
  def apply[T](call: ZooKeeper => T): T

  /**
   * Active  ZooKeeper client, through which all interactions with ZK are
   * being performed.
   */
  def client: ZooKeeper

  /**
   * Future which is resolved with active connection
   * once is is initially established.
   */
  def whenConnected: Future[ZKConnection]

  /**
   * Waits for connection to esablish within given timeout.
   *
   * @param timeout Amount of time to wait for connection
   */
  def awaitConnection(timeout: FiniteDuration): Unit

  /**
   * Tests whether the connection to ZooKeeper server is established.
   */
  def isConnected: Boolean

  /**
   * Closes current connection and returns a new connection with the same
   * arguments as this one.
   */
  def recreate(): ZKConnection

  /**
   * Disconnects from ZooKeeper server.
   */
  def close(): Unit
}


case class ZKSession(id: ZKSessionId, password: ZKSessionPassword)


object ZKConnection {
  /**
   * `ZKConnection` encapsulates and maintaines connection to ZooKeeper.
   *
   * @param servers Connection string, consisting of comma separated host:port
   * values.
   * @param timeout Connection timeout.
   * @param connectionWatcher Hook-function, that will be called when connection
   * to ZooKeeper server is established.
   */
  def apply(
    connectionString: String,
    sessionTimeout: FiniteDuration,
    connectionWatcher: ConnectionWatcher = NoWatcher,
    session: Option[ZKSession] = None): ZKConnection =
  {
    new impl.ZKConnectionImpl(
      connectionString,
      sessionTimeout,
      connectionWatcher,
      session)
  }


  def apply(servers: String, timeout: FiniteDuration): ZKConnection = {
    ZKConnection(servers, timeout)
  }


  type ConnectionWatcher = Reaction[ConnectionEvent]
  val NoWatcher = Reaction.empty[ConnectionEvent]
}


// vim: set ts=2 sw=2 et:
