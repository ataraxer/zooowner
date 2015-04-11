package zooowner

import zooowner.message._
import scala.concurrent.Future
import scala.concurrent.duration._


trait AsyncZooowner {
  /**
   * Asynchronous version of `stat`.
   */
  def meta(
    path: ZKPath,
    watcher: Option[ZKEventWatcher] = None): Future[ZKNodeMeta]

  /**
   * Asynchronous version of [[Zooowner.create]].
   */
  def create[T: ZKEncoder](
    path: ZKPath,
    value: T = NoData,
    persistent: Boolean = false,
    sequential: Boolean = false): Future[ZKPath]

  /**
   * Asynchronous version of [[Zooowner.delete]].
   */
  def delete(
    path: ZKPath,
    version: Int = AnyVersion): Future[Unit]

  /**
   * Asynchronous version of [[Zooowner.set]].
   */
  def set[T: ZKEncoder](
    path: ZKPath,
    value: T, version: Int = AnyVersion): Future[ZKNodeMeta]

  /**
   * Asynchronous version of [[Zooowner.get]].
   */
  def get(
    path: ZKPath,
    watcher: Option[ZKEventWatcher] = None): Future[ZKNode]

  /**
   * Asynchronous version of [[Zooowner.children]].
   */
  def children(
    path: ZKPath,
    watcher: Option[ZKEventWatcher] = None): Future[Seq[ZKPath]]
}


trait AsyncAPI { this: Zooowner =>
  val async: AsyncZooowner = new impl.AsyncZooownerImpl(this)
}


/**
 * Zooowner client extended with asynchronous API.
 *
 * {{{
 * val zk = AsyncZooowner("localhost:2181", 5.seconds, Some("prefix"))
 * }}}
 */
object AsyncZooowner {
  type RichZooowner = Zooowner with AsyncAPI

  def apply(servers: String, timeout: FiniteDuration): RichZooowner = {
    val connection = ZKConnection(servers, timeout)
    AsyncZooowner(connection)
  }

  def apply(connection: ZKConnection): RichZooowner = {
    new impl.ZooownerImpl(connection) with AsyncAPI
  }
}


// vim: set ts=2 sw=2 et:
