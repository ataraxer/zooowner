package zooowner
package impl

import zooowner.message._

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.Watcher.Event.EventType

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, ExecutionContext}
import scala.collection.JavaConversions._
import scala.util.control.Exception.catching


/**
 * ZooKeeper client that doesn't make you cry.
 *
 * @param connection Connection to ZooKeeper
 */
private[zooowner] class ZooownerImpl(initialConnection: ZKConnection)
  extends Zooowner
{
  import Zooowner._
  import ImplUtils._

  /**
   * Internal active connection to ZooKeeper.
   */
  private[zooowner] var connection = initialConnection

  def client = connection.client

  def disconnect(): Unit = connection.close()

  def awaitConnection(timeout: FiniteDuration = 5.seconds): Unit = {
    connection.awaitConnection(timeout)
  }


  def isConnected = connection.isConnected


  def reconnect(force: Boolean = false): Unit = {
    if (!isConnected || force) {
      connection = connection.recreate()
    }
  }


  private def _create(
    path: ZKPath,
    data: ZKData,
    persistent: Boolean = false,
    sequential: Boolean = false): ZKPath =
  {
    connection { client =>
      val mode = createMode(persistent, sequential)
      client.create(path, data.orNull, AnyACL, mode)
    }
  }


  def create[T: ZKEncoder](
    path: ZKPath,
    value: T = NoData,
    persistent: Boolean = false,
    sequential: Boolean = false) =
  {
    if (!path.isRoot) createPathTo(path)
    _create(path, encode(value), persistent, sequential)
  }


  def createChild[T: ZKEncoder](
    path: ZKPath,
    value: T = NoData,
    persistent: Boolean = false,
    sequential: Boolean = false) =
  {
    require(!path.isRoot, "Path should not be root")
    _create(path, encode(value), persistent, sequential)
  }


  def createPath(path: ZKPath) = createPathTo(path / "fake")


  private def createPathTo(node: ZKPath): Unit = {
    val parent = node.parent

    if (!exists(parent)) {
      createPathTo(parent)
      _create(parent, None, persistent = true)
    }
  }


  def meta(path: ZKPath, watcher: Option[ZKEventWatcher] = None) = {
    connection { client =>
      Option { client.exists(path, watcher.orNull) }
    } map ( _.toMeta )
  }


  def apply(path: ZKPath, watcher: Option[ZKEventWatcher] = None) = {
    val stat = new Stat

    connection { client =>
      val data = client.getData(path, watcher.orNull, stat)
      ZKNode(path, Option(data), Some(stat.toMeta))
    }
  }


  def get(path: ZKPath, watcher: Option[ZKEventWatcher] = None) = {
    catching(classOf[NoNodeException]) opt { apply(path, watcher) }
  }


  def set[T: ZKEncoder](
    path: ZKPath,
    value: T,
    version: Int = AnyVersion) =
  {
    val data = encode(value)
    connection { _.setData(path, data.orNull, version) }
  }


  def delete(
    path: ZKPath,
    recursive: Boolean = false,
    version: Int = AnyVersion): Unit =
  {
    if (recursive) {
      children(path) foreach { child =>
        delete(child, recursive = true)
      }
    }

    connection { client =>
      client.delete(path, version)
    }
  }


  def children(
    path: ZKPath,
    watcher: Option[ZKEventWatcher] = None) =
  {
    connection { client =>
      client.getChildren(path, watcher.orNull).toList
    } map { child => ZKPath(path) / child }
  }


  def isEphemeral(path: ZKPath) = {
    meta(path).map(_.ephemeral).getOrElse(false)
  }


  def watch
    (path: ZKPath, persistent: Boolean = true)
    (reaction: Reaction[ZKEvent]): ZKEventWatcher =
  {
    val callback = reaction orElse Reaction.empty[ZKEvent]
    val watcher = new DefaultNodeWatcher(this, path, callback, persistent)
    watch(path, watcher)
  }

  /**
   * Sets up a watcher on node events.
   */
  def watch(path: ZKPath, watcher: ZKEventWatcher): ZKEventWatcher = {
    val nodeExists = exists(path, Some(watcher))
    if (nodeExists) children(path, watcher = Some(watcher))
    watcher
  }


  def watchData
    (path: ZKPath)
    (implicit executor: ExecutionContext): Future[ZKDataEvent] =
  {
    val eventWatcher = new OneTimeWatcher(connection)
    exists(path, watcher = Some(eventWatcher))
    _processEvent(path, eventWatcher.futureEvent).mapTo[ZKDataEvent]
  }


  def watchChildren
    (path: ZKPath)
    (implicit executor: ExecutionContext): Future[ZKChildrenEvent] =
  {
    val eventWatcher = new OneTimeWatcher(connection)
    children(path, watcher = Some(eventWatcher))
    _processEvent(path, eventWatcher.futureEvent).mapTo[ZKChildrenEvent]
  }


  private def _processEvent
    (path: ZKPath, futureEvent: Future[EventType])
    (implicit executor: ExecutionContext): Future[ZKEvent] =
  {
    futureEvent map {
      case EventType.NodeCreated =>
        NodeCreated(path, get(path))

      case EventType.NodeDataChanged =>
        NodeChanged(path, get(path))

      case EventType.NodeDeleted =>
        NodeDeleted(path)

      case EventType.NodeChildrenChanged =>
        NodeChildrenChanged(path, children(path))

      case event =>
        throw new Exception("Unexpected event encountered: " + event)
    }
  }
}


// vim: set ts=2 sw=2 et:
