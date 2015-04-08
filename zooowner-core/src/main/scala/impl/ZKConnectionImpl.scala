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
    session: Option[ZKSession])
  extends ZKConnection
{
  import ZKConnection._

  private val connectionPromise = Promise[ZKConnection]()

  val whenConnected = connectionPromise.future


  def awaitConnection(timeout: FiniteDuration): Unit = {
    try {
      if (!isConnected) Await.result(whenConnected, timeout)
    } catch {
      case _: TimeoutException =>
        throw new ZKConnectionTimeoutException(
          "Can't connect to ZooKeeper within %s timeout".format(timeout))
    }
  }


  def isConnected = client.getState == States.CONNECTED

  private val dispatchEvent = connectionWatcher orElse NoWatcher


  protected val stateWatcher = {
    ZKStateWatcher {
      case KeeperState.SyncConnected => {
        dispatchEvent(Connected)
        if (!connectionPromise.isCompleted) {
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


  val client = {
    val timeoutMillis = sessionTimeout.toMillis.toInt
    new ZKClient(connectionString, timeoutMillis, stateWatcher)
  }


  def apply[T](call: ZKClient => T): T = {
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
    dispatchEvent(Disconnected)
  }
}


// vim: set ts=2 sw=2 et:
