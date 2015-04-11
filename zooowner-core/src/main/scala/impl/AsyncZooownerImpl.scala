package zooowner
package impl

import zooowner.message.ZKResponse
import org.apache.zookeeper.data.Stat
import scala.concurrent.{Promise, Future}


private[zooowner] class AsyncZooownerImpl(zooowner: Zooowner)
  extends AsyncZooowner
{
  import ImplUtils._
  import ZKCallback._

  def client = zooowner.client


  def meta(
    path: ZKPath,
    watcher: Option[ZKEventWatcher] = None): Future[ZKNodeMeta] =
  {
    val result = Promise[ZKNodeMeta]()
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
    version: Int = AnyVersion): Future[ZKNodeMeta] =
  {
    val result = Promise[ZKNodeMeta]()
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
}


// vim: set ts=2 sw=2 et:
