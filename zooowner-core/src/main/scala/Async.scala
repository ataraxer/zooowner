package com.ataraxer.zooowner

import com.ataraxer.zooowner.common.Constants._
import com.ataraxer.zooowner.message._

import org.apache.zookeeper.{ZooKeeper, Watcher => ZKWatcher}

import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.control.Exception._


/**
 * Plug-in trait which extends Zooowner client with asynchronous API.
 *
 * {{{
 * val zk = new ZooKeeper("localhost:2181", 5.seconds, Some("prefix")) with Async
 * }}}
 */
trait Async { this: Zooowner =>
  import Zooowner._

  protected var client: ZooKeeper
  protected var activeWatchers: List[EventWatcher]
  protected def resolvePath(path: String): String
  protected def resolveWatcher(maybeWatcher: Option[EventWatcher]): ZKWatcher

  object async {

    /**
     * Asynchronous version of [[Zooowner.stat]].
     */
    def stat(path: String, watcher: Option[EventWatcher] = None)
            (callback: Reaction[Response]): Unit =
    {
      client.exists(
        resolvePath(path),
        resolveWatcher(watcher),
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
      (callback: Reaction[Response])
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
      (callback: Reaction[Response]): Unit =
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
      (callback: Reaction[Response])
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
      (callback: Reaction[Response]): Unit =
    {
      client.getData(
        resolvePath(path),
        resolveWatcher(watcher),
        OnData(callback),
        null)
    }

    /**
     * Asynchronous version of [[Zooowner.children]].
     */
    def children(path: String, watcher: Option[EventWatcher] = None)
                (callback: Reaction[Response]): Unit =
    {
      client.getChildren(
        resolvePath(path),
        resolveWatcher(watcher),
        OnChildren(callback),
        null)
    }
  }
}


// vim: set ts=2 sw=2 et:
