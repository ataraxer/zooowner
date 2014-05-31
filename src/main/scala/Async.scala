package com.ataraxer.zooowner

import com.ataraxer.zooowner.message._

import org.apache.zookeeper.ZooKeeper

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

  object async {

    /**
     * Asynchronous version of [[Zooowner.stat]].
     */
    def stat(path: String, maybeWatcher: Option[EventWatcher] = None)
            (callback: Reaction[Response]): Unit =
    {
      val watcher = maybeWatcher.orNull
      if (maybeWatcher.isDefined) activeWatchers :+= watcher

      client.exists(resolvePath(path), watcher, OnStat(callback), null)
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
          case NodeDeleted(path, counter)
            if (counter == nodeChildren.size) =>
              deleteNode(parent, parentCallback)
        }

        for (child <- nodeChildren) {
          deleteRecursive(path/child, hook)
        }
      }

      def deleteRecursive(path: String, hook: OnDeleted): Unit = {
        async.children(path) {
          case NodeChildren(Nil) =>
            deleteNode(path, hook)

          case NodeChildren(nodeChildren) =>
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
    def get(path: String, maybeWatcher: Option[EventWatcher] = None)
           (callback: Reaction[Response]): Unit =
    {
      val watcher = maybeWatcher.orNull
      if (maybeWatcher.isDefined) activeWatchers :+= watcher

      client.getData(resolvePath(path), watcher, OnData(callback), null)
    }

    /**
     * Asynchronous version of [[Zooowner.children]].
     */
    def children(path: String, maybeWatcher: Option[EventWatcher] = None)
                (callback: Reaction[Response]): Unit =
    {
      val watcher = maybeWatcher.orNull
      if (maybeWatcher.isDefined) activeWatchers :+= watcher

      client.getChildren(resolvePath(path), watcher, OnChildren(callback), null)
    }
  }
}


// vim: set ts=2 sw=2 et:
