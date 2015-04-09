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
    path: String,
    watcher: Option[ZKEventWatcher] = None): Future[ZKNodeMeta] =
  {
    val result = Promise[ZKNodeMeta]()

    client.exists(
      resolvePath(path),
      watcher.orNull,
      OnStat(result),
      null)

    result.future
  }


  def create[T: ZKEncoder](
    path: String,
    value: T = NoData,
    persistent: Boolean = false,
    sequential: Boolean = false): Future[String] =
  {
    val result = Promise[String]()
    val data = encode(value).orNull

    client.create(
      resolvePath(path),
      data,
      AnyACL,
      createMode(persistent, sequential),
      OnCreated(result),
      null)

    result.future
  }


  def delete(path: String, version: Int = AnyVersion): Future[Unit] = {
    val result = Promise[Unit]()

    client.delete(
      resolvePath(path),
      version,
      OnDeleted(result),
      null)

    result.future
  }


  def set[T: ZKEncoder](
    path: String,
    value: T,
    version: Int = AnyVersion): Future[ZKNodeMeta] =
  {
    val result = Promise[ZKNodeMeta]()
    val data = encode(value).orNull

    client.setData(
      resolvePath(path),
      data,
      version,
      OnStat(result),
      null)

    result.future
  }


  def get(
    path: String,
    watcher: Option[ZKEventWatcher] = None): Future[ZKNode] =
  {
    val result = Promise[ZKNode]()

    client.getData(
      resolvePath(path),
      watcher.orNull,
      OnData(result),
      null)

    result.future
  }


  def children(
    path: String,
    watcher: Option[ZKEventWatcher] = None): Future[Seq[String]] =
  {
    val result = Promise[Seq[String]]()

    client.getChildren(
      resolvePath(path),
      watcher.orNull,
      OnChildren(result),
      null)

    result.future
  }
}


// vim: set ts=2 sw=2 et:
