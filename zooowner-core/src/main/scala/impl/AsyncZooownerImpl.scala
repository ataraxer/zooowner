package zooowner
package impl

import org.apache.zookeeper.data.Stat
import zooowner.message.ZKResponse


private[zooowner] class AsyncZooownerImpl(zooowner: Zooowner)
  extends AsyncZooowner
{
  import ImplUtils._
  import Callbacks._

  def client = zooowner.client


  def meta
    (path: String, watcher: Option[ZKEventWatcher] = None)
    (callback: Reaction[ZKResponse]): Unit =
  {
    client.exists(
      resolvePath(path),
      watcher.orNull,
      OnStat(callback),
      null)
  }


  def create[T: ZKEncoder](
    path: String,
    value: T = NoData,
    persistent: Boolean = false,
    sequential: Boolean = false)
    (callback: Reaction[ZKResponse]): Unit =
  {
    val data = encode(value).orNull

    client.create(
      resolvePath(path),
      data,
      AnyACL,
      createMode(persistent, sequential),
      OnCreated(callback, data),
      null)
  }


  def delete
    (path: String, version: Int = AnyVersion)
    (callback: Reaction[ZKResponse]): Unit =
  {
    client.delete(
      resolvePath(path),
      version,
      OnDeleted(callback),
      null)
  }


  def set[T: ZKEncoder]
    (path: String, value: T, version: Int = AnyVersion)
    (callback: Reaction[ZKResponse]): Unit =
  {
    val data = encode(value).orNull

    client.setData(
      resolvePath(path), data, version,
      OnStat(callback), null
    )
  }


  def get
    (path: String, watcher: Option[ZKEventWatcher] = None)
    (callback: Reaction[ZKResponse]): Unit =
  {
    client.getData(
      resolvePath(path),
      watcher.orNull,
      OnData(callback),
      null)
  }


  def children
    (path: String, watcher: Option[ZKEventWatcher] = None)
    (callback: Reaction[ZKResponse]): Unit =
  {
    client.getChildren(
      resolvePath(path),
      watcher.orNull,
      OnChildren(callback),
      null)
  }
}


// vim: set ts=2 sw=2 et:
