package zooowner

import zooowner.message._
import zooowner.ZKNodeMeta.StatConverter
import zooowner.Zooowner.Reaction

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.Watcher.Event.{KeeperState, EventType}
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.CreateMode._
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.KeeperException._

import scala.concurrent.{Promise, Await, TimeoutException}
import scala.concurrent.duration._
import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Exception._

import ZKConnection._


case class ZKSession(id: ZKSessionId, password: ZKSessionPassword)


object ZKConnection {
  def apply(servers: String, timeout: FiniteDuration) = {
    new ZKConnection(servers, timeout)
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
  connectionTimeout: FiniteDuration = 5.seconds,
  session: Option[ZKSession] = None)
{
  import ZKConnection._

  /**
   * Internal promise which is resolved with active connection
   * once it is initially established.
   */
  private var connectionPromise = Promise[ZKConnection]()

  /**
   * Future which is resolved with active connection
   * once is is initially established.
   */
  val whenConnected = connectionPromise.future

  /**
   * Internal watcher, that controls ZooKeeper connection life-cycle.
   */
  private val stateWatcher = {
    ZKStateWatcher {
      case KeeperState.SyncConnected => {
        connectionWatcher(Connected)
        connectionPromise.success(this)
      }

      case KeeperState.Disconnected => {
        connectionWatcher(Disconnected)
      }

      case KeeperState.Expired => {
        connectionWatcher(Expired)
      }
    }
  }

  /**
   * Active  ZooKeeper client, through which all interactions with ZK are
   * being performed.
   */
  val client: ZooKeeper = {
    val timeoutMillis = sessionTimeout.toMillis.toInt
    new ZooKeeper(connectionString, timeoutMillis, stateWatcher)
  }

  /**
   * Tests whether the connection to ZooKeeper server is established.
   */
  def isConnected = client.getState == States.CONNECTED

  /**
   * Blocks until client is connected.
   */
  def awaitConnection(): Unit = {
    try {
      if (!isConnected) Await.result(whenConnected, connectionTimeout)
    } catch {
      case _: TimeoutException =>
        throw new ZKConnectionTimeoutException(
          "Can't connect to ZooKeeper within %s timeout".format(connectionTimeout))
    }
  }

  /**
   * Disconnects from ZooKeeper server.
   */
  def disconnect(): Unit = {
    client.close()
    connectionWatcher(Disconnected)
  }

  /**
   * Takes a function to be called on client taking care of ensuring that it's
   * called with active instance of ZooKeeper client.
   */
  def apply[T](call: ZooKeeper => T): T = {
    try call(client) catch {
      case exception: SessionExpiredException => {
        connectionWatcher(Expired)
        throw exception
      }

      case exception: ConnectionLossException => {
        connectionWatcher(Disconnected)
        throw exception
      }

      case exception: Throwable => throw exception
    }
  }
}


// vim: set ts=2 sw=2 et:
