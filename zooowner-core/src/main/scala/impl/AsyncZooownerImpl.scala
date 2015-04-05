package zooowner
package impl

import zooowner.message.ZKResponse


private[zooowner] class AsyncZooownerImpl(zooowner: Zooowner)
  extends AsyncZooowner
{
  import ImplUtils._
  import Callbacks._

  def client = zooowner.client


  def meta
    (path: String, watcher: Option[EventWatcher] = None)
    (callback: Reaction[ZKResponse]): Unit =
  {
    client.exists(
      resolvePath(path),
      watcher.orNull,
      OnStat(callback),
      null)
  }


  def create[T](
    path: String,
    value: T = Option.empty[String],
    persistent: Boolean = false,
    sequential: Boolean = false)
    (callback: Reaction[ZKResponse])
    (implicit encoder: ZKEncoder[T]): Unit =
  {
    val data = encoder.encode(value).orNull

    client.create(
      resolvePath(path),
      data,
      AnyACL,
      createMode(persistent, sequential),
      OnCreated(callback),
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


  def set[T]
    (path: String, value: T, version: Int = AnyVersion)
    (callback: Reaction[ZKResponse])
    (implicit encoder: ZKEncoder[T]): Unit =
  {
    val data = encoder.encode(value).orNull

    client.setData(
      resolvePath(path), data, version,
      OnStat(callback), null
    )
  }


  def get
    (path: String, watcher: Option[EventWatcher] = None)
    (callback: Reaction[ZKResponse]): Unit =
  {
    client.getData(
      resolvePath(path),
      watcher.orNull,
      OnData(callback),
      null)
  }


  def children
    (path: String, watcher: Option[EventWatcher] = None)
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
