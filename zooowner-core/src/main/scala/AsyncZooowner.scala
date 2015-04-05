package zooowner

import zooowner.message._

import org.apache.zookeeper.{ZooKeeper, Watcher}

import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.control.Exception._


object AsyncZooowner {
  def apply(servers: String, timeout: FiniteDuration): AsyncZooowner = {
    val connection = new ZKConnection(servers, timeout)
    AsyncZooowner(connection)
  }

  def apply(connection: ZKConnection): AsyncZooowner = {
    new Zooowner(connection) with AsyncAPI
  }
}


/**
 * Plug-in trait which extends Zooowner client with asynchronous API.
 *
 * {{{
 * val zk = AsyncZooowner("localhost:2181", 5.seconds, Some("prefix"))
 * }}}
 */
trait AsyncAPI { this: Zooowner =>
  import Zooowner._


  object async {
    /**
     * Asynchronous version of `stat`.
     */
    def stat
      (path: String, watcher: Option[EventWatcher] = None)
      (callback: Reaction[ZKResponse]): Unit =
    {
      client.exists(
        resolvePath(path),
        watcher.orNull,
        OnStat(callback),
        null)
    }

    /**
     * Asynchronous version of [[Zooowner.create]].
     */
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

    /**
     * Asynchronous version of [[Zooowner.delete]].
     */
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

    /**
     * Asynchronous version of [[Zooowner.set]].
     */
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

    /**
     * Asynchronous version of [[Zooowner.get]].
     */
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

    /**
     * Asynchronous version of [[Zooowner.children]].
     */
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
}


// vim: set ts=2 sw=2 et:
