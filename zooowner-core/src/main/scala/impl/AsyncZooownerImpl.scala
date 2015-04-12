package zooowner
package impl

import zooowner.message._
import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.Watcher.Event.EventType
import scala.concurrent.{Promise, Future, ExecutionContext}


private[zooowner] class AsyncZooownerImpl(zooowner: ZooownerImpl)
  extends AsyncZooowner
{
  import ImplUtils._
  import ZKCallback._

  def client = zooowner.client


  def meta(
    path: ZKPath,
    watcher: Option[ZKEventWatcher] = None): Future[Option[ZKMeta]] =
  {
    val result = Promise[Option[ZKMeta]]()
    client.exists(path, watcher.orNull, OnStat(result), null)
    result.future
  }


  def create[T: ZKEncoder](
    path: ZKPath,
    value: T = NoData,
    persistent: Boolean = false,
    sequential: Boolean = false): Future[ZKPath] =
  {
    val result = Promise[ZKPath]()
    val data = encode(value).orNull
    val mode = createMode(persistent, sequential)
    client.create(path, data, AnyACL, mode, OnCreated(result), null)
    result.future
  }


  def delete(path: ZKPath, version: Int = AnyVersion): Future[Unit] = {
    val result = Promise[Unit]()
    client.delete(path, version, OnDeleted(result), null)
    result.future
  }


  def set[T: ZKEncoder](
    path: ZKPath,
    value: T,
    version: Int = AnyVersion): Future[Option[ZKMeta]] =
  {
    val result = Promise[Option[ZKMeta]]()
    val data = encode(value).orNull
    client.setData(path, data, version, OnStat(result), null)
    result.future
  }


  def get(
    path: ZKPath,
    watcher: Option[ZKEventWatcher] = None): Future[ZKNode] =
  {
    val result = Promise[ZKNode]()
    client.getData(path, watcher.orNull, OnData(result), null)
    result.future
  }


  def children(
    path: ZKPath,
    watcher: Option[ZKEventWatcher] = None): Future[Seq[ZKPath]] =
  {
    val result = Promise[Seq[ZKPath]]()
    client.getChildren(path, watcher.orNull, OnChildren(result), null)
    result.future
  }


  def watchData
    (path: ZKPath)
    (implicit executor: ExecutionContext): Future[ZKDataEvent] =
  {
    val eventWatcher = new OneTimeWatcher(zooowner.connection)
    meta(path, watcher = Some(eventWatcher))
    _processEvent(path, eventWatcher.futureEvent).mapTo[ZKDataEvent]
  }


  def watchChildren
    (path: ZKPath)
    (implicit executor: ExecutionContext): Future[ZKChildrenEvent] =
  {
    val eventWatcher = new OneTimeWatcher(zooowner.connection)
    children(path, watcher = Some(eventWatcher))
    _processEvent(path, eventWatcher.futureEvent).mapTo[ZKChildrenEvent]
  }


  private def _processEvent
    (path: ZKPath, futureEvent: Future[EventType])
    (implicit executor: ExecutionContext): Future[ZKEvent] =
  {
    futureEvent flatMap {
      case EventType.NodeCreated =>
        get(path) map { node => NodeCreated(path, Some(node)) }

      case EventType.NodeDataChanged =>
        get(path) map { node => NodeChanged(path, Some(node)) }

      case EventType.NodeDeleted =>
        Future.successful(NodeDeleted(path))

      case EventType.NodeChildrenChanged =>
        children(path) map { children => NodeChildrenChanged(path, children) }

      case event =>
        Future.failed(new Exception("Unexpected event encountered: " + event))
    }
  }
}


// vim: set ts=2 sw=2 et:
