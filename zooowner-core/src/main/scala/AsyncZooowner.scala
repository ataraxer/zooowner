package zooowner

import zooowner.message._
import scala.concurrent.duration._


trait AsyncZooowner {
  /**
   * Asynchronous version of `stat`.
   */
  def meta
    (path: String, watcher: Option[EventWatcher] = None)
    (callback: Reaction[ZKResponse]): Unit

  /**
   * Asynchronous version of [[Zooowner.create]].
   */
  def create[T](
    path: String,
    value: T = Option.empty[String],
    persistent: Boolean = false,
    sequential: Boolean = false)
    (callback: Reaction[ZKResponse])
    (implicit encoder: ZKEncoder[T]): Unit

  /**
   * Asynchronous version of [[Zooowner.delete]].
   */
  def delete
    (path: String, version: Int = AnyVersion)
    (callback: Reaction[ZKResponse]): Unit

  /**
   * Asynchronous version of [[Zooowner.set]].
   */
  def set[T]
    (path: String, value: T, version: Int = AnyVersion)
    (callback: Reaction[ZKResponse])
    (implicit encoder: ZKEncoder[T]): Unit

  /**
   * Asynchronous version of [[Zooowner.get]].
   */
  def get
    (path: String, watcher: Option[EventWatcher] = None)
    (callback: Reaction[ZKResponse]): Unit

  /**
   * Asynchronous version of [[Zooowner.children]].
   */
  def children
    (path: String, watcher: Option[EventWatcher] = None)
    (callback: Reaction[ZKResponse]): Unit
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
