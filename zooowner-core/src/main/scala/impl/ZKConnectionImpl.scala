package zooowner
package impl

import zooowner.message._

import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.Watcher.Event.KeeperState

import scala.concurrent.{Promise, Await, TimeoutException}
import scala.concurrent.duration._

import ZKConnection._


private[zooowner] class ZKConnectionImpl(
    connectionString: String,
    sessionTimeout: FiniteDuration,
    connectionWatcher: ZKConnectionWatcher,
    sessionCredentials: Option[ZKSession])
  extends ZKConnection
{
  import ZKConnection._

  private val connectionPromise = Promise[ZKConnection]()
  private val expirationPromise = Promise[ZKSession]()
  val whenConnected = connectionPromise.future
  val whenExpired = expirationPromise.future


  def awaitConnection(timeout: FiniteDuration): Unit = {
    try {
      if (!isConnected) Await.result(whenConnected, timeout)
    } catch {
      case _: TimeoutException =>
        throw new ZKConnectionTimeoutException(
          "Can't connect to ZooKeeper within %s timeout".format(timeout))
    }
  }


  def awaitExpiration(timeout: FiniteDuration): Unit = {
    if (!isConnected) Await.result(whenExpired, timeout)
  }


  def isConnected = client.getState.isConnected
  def isExpired = expirationPromise.isCompleted

  private val watcherCallback = connectionWatcher orElse NoWatcher


  protected val stateWatcher = {
    ZKStateWatcher {
      case KeeperState.SyncConnected => {
        watcherCallback(Connected)
        if (!connectionPromise.isCompleted) {
          connectionPromise.success(this)
        }
      }

      case KeeperState.Disconnected => {
        watcherCallback(Disconnected)
      }

      case KeeperState.Expired => {
        watcherCallback(Expired)
        if (!expirationPromise.isCompleted) {
          expirationPromise.success(session.get)
        }
      }
    }
  }


  val client: ZKClient = {
    val timeoutMillis = sessionTimeout.toMillis.toInt

    sessionCredentials map { case ZKSession(id, pass) =>
      new ZKClient(connectionString, timeoutMillis, stateWatcher, id, pass)
    } getOrElse {
      new ZKClient(connectionString, timeoutMillis, stateWatcher)
    }
  }


  def session = {
    if (!isConnected) None
    else Some(ZKSession(client.getSessionId, client.getSessionPasswd))
  }


  def apply[T](call: ZKClient => T): T = {
    try call(client) catch {
      case exception: SessionExpiredException => {
        stateWatcher.dispatch(KeeperState.Expired)
        throw exception
      }

      case exception: ConnectionLossException => {
        stateWatcher.dispatch(KeeperState.Disconnected)
        throw exception
      }

      case exception: Throwable => throw exception
    }
  }


  def recreate(): ZKConnection = {
    close()

    new ZKConnectionImpl(
      connectionString,
      sessionTimeout,
      connectionWatcher,
      session)
  }


  def close(): Unit = {
    client.close()
    stateWatcher.dispatch(KeeperState.Disconnected)
  }
}


// vim: set ts=2 sw=2 et:
