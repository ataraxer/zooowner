package zooowner

import zooowner.message._
import zooowner.Zooowner.Reaction

import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.Watcher.Event.KeeperState
import org.apache.zookeeper.KeeperException._

import scala.concurrent.{Promise, Await, TimeoutException}
import scala.concurrent.duration._

import ZKConnection._


case class ZKSession(id: ZKSessionId, password: ZKSessionPassword)


object ZKConnection {
  def apply(servers: String, timeout: FiniteDuration) = {
    new ZKConnection(servers, timeout)
  }

  def apply(
    connectionString: String,
    sessionTimeout: FiniteDuration,
    connectionWatcher: ConnectionWatcher = NoWatcher,
    session: Option[ZKSession] = None) =
  {
    new ZKConnection(
      connectionString,
      sessionTimeout,
      connectionWatcher,
      session)
  }

  type ConnectionWatcher = Reaction[ConnectionEvent]
  val NoWatcher = Reaction.empty[ConnectionEvent]
}


/**
 * `ZKConnection` encapsulates and maintaines connection to ZooKeeper.
 *
 * @param servers Connection string, consisting of comma separated host:port
 * values.
 * @param timeout Connection timeout.
 * @param connectionWatcher Hook-function, that will be called when connection
 * to ZooKeeper server is established.
 */
class ZKConnection(
  connectionString: String,
  sessionTimeout: FiniteDuration,
  connectionWatcher: ConnectionWatcher = NoWatcher,
  session: Option[ZKSession] = None)
{
  import ZKConnection._

  /**
   * Internal promise which is resolved with active connection
   * once it is initially established.
   */
  private val connectionPromise = Promise[ZKConnection]()

  /**
   * Future which is resolved with active connection
   * once is is initially established.
   */
  val whenConnected = connectionPromise.future

  /**
   * Safely dispatches event to a connection watcher.
   */
  private val dispatchEvent = {
    connectionWatcher orElse Reaction.empty[ConnectionEvent]
  }

  /**
   * Internal watcher, that controls ZooKeeper connection life-cycle.
   */
  protected val stateWatcher = {
    ZKStateWatcher {
      case KeeperState.SyncConnected => {
        dispatchEvent(Connected)
        if (connectionPromise.isCompleted) {
          connectionPromise.success(this)
        }
      }

      case KeeperState.Disconnected => {
        dispatchEvent(Disconnected)
      }

      case KeeperState.Expired => {
        dispatchEvent(Expired)
      }
    }
  }

  /**
   * Active  ZooKeeper client, through which all interactions with ZK are
   * being performed.
   */
  val client = {
    val timeoutMillis = sessionTimeout.toMillis.toInt
    new ZooKeeper(connectionString, timeoutMillis, stateWatcher)
  }

  /**
   * Tests whether the connection to ZooKeeper server is established.
   */
  def isConnected = client.getState == States.CONNECTED

  /**
   * Waits for connection to esablish within given timeout.
   *
   * @param timeout Amount of time to wait for connection
   */
  def awaitConnection(timeout: FiniteDuration): Unit = {
    try {
      if (!isConnected) Await.result(whenConnected, timeout)
    } catch {
      case _: TimeoutException =>
        throw new ZKConnectionTimeoutException(
          "Can't connect to ZooKeeper within %s timeout".format(timeout))
    }
  }

  /**
   * Disconnects from ZooKeeper server.
   */
  def close(): Unit = {
    client.close()
    dispatchEvent(Disconnected)
  }

  /**
   * Closes current connection and returns a new connection with the same
   * arguments as this one.
   */
  def recreate(): ZKConnection = {
    close()

    new ZKConnection(
      connectionString,
      sessionTimeout,
      connectionWatcher,
      session)
  }

  /**
   * Takes a function to be called on client taking care of ensuring that it's
   * called with active instance of ZooKeeper client.
   */
  def apply[T](call: ZooKeeper => T): T = {
    try call(client) catch {
      case exception: SessionExpiredException => {
        dispatchEvent(Expired)
        throw exception
      }

      case exception: ConnectionLossException => {
        dispatchEvent(Disconnected)
        throw exception
      }

      case exception: Throwable => throw exception
    }
  }
}


// vim: set ts=2 sw=2 et:
