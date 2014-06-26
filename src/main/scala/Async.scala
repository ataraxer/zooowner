package com.ataraxer.zooowner

import com.ataraxer.zooowner.message._

import org.apache.zookeeper.{ZooKeeper, Watcher => ZKWatcher}

import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.control.Exception._


/**
 * Plug-in trait which extends Zooowner client with asynchronous API.
 *
 * {{{
 * val zk = new ZooKeeper("localhost:2181", 5.seconds, "prefix") with Async
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
    def create(path: String,
               maybeData: Option[String],
               persistent: Boolean = false,
               sequential: Boolean = false,
               recursive: Boolean = false,
               filler: Option[String] = None)
              (callback: Reaction[Response]): Unit =
    {
      if (recursive) {
        for (parentPath <- parentPaths(path)) {
          create(parentPath, filler, persistent = true,
                 recursive = false)(default[Response])
        }
      }

      val data = maybeData.map( _.getBytes("utf8") ).orNull

      client.create(
        resolvePath(path), data, AnyACL,
        createMode(persistent, sequential),
        OnCreated(callback), null
      )
    }

    /**
     * Asynchronous version of [[Zooowner.delete]].
     */
    def delete(path: String, recursive: Boolean = false)
              (callback: Reaction[Response]): Unit =
    {
      def deleteNode(path: String, hook: OnDeleted): Unit =
        client.delete(resolvePath(path), AnyVersion, hook, null)

      def deleteChildren(parent: String, nodeChildren: List[String],
                         parentCallback: OnDeleted): Unit =
      {
        val hook = OnDeleted {
          case event: NodeDeleted with Counter
            if (event.count == nodeChildren.size) =>
              deleteNode(parent, parentCallback)
        }

        for (child <- nodeChildren) {
          deleteRecursive(path/child, hook)
        }
      }

      def deleteRecursive(path: String, hook: OnDeleted): Unit = {
        async.children(path) {
          case NodeChildren(_, Nil) =>
            deleteNode(path, hook)

          case NodeChildren(_, nodeChildren) =>
            deleteChildren(path, nodeChildren, hook)
        }
      }

      recursive match {
        case true  => deleteRecursive(path, OnDeleted(callback))
        case false => deleteNode(path, OnDeleted(callback))
      }
    }

    /**
     * Asynchronous version of [[Zooowner.set]].
     */
    def set(path: String, data: String)
           (callback: Reaction[Response]): Unit =
    {
      client.setData(
        resolvePath(path), data.getBytes, AnyVersion,
        OnStat(callback), null
      )
    }

    /**
     * Asynchronous version of [[Zooowner.get]].
     */
    def get(path: String, watcher: Option[EventWatcher] = None)
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
