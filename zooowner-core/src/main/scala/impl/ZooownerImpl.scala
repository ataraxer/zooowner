package zooowner
package impl

import zooowner.message._

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.ZooKeeper

import scala.concurrent.duration._
import scala.collection.JavaConversions._
import scala.util.control.Exception.catching


/**
 * ZooKeeper client that doesn't make you cry.
 *
 * @param connection Connection to ZooKeeper
 */
private[zooowner] class ZooownerImpl(connection: ZKConnection)
  extends Zooowner
{
  import Zooowner._
  import ImplUtils._

  /**
   * Internal active connection to ZooKeeper.
   */
  private var activeConnection = connection

  def client = activeConnection.client

  def disconnect(): Unit = connection.close()

  def awaitConnection(timeout: FiniteDuration = 5.seconds): Unit = {
    connection.awaitConnection(timeout)
  }


  def isConnected = connection.isConnected


  def reconnect(force: Boolean = false): Unit = {
    if (!isConnected || force) {
      activeConnection = activeConnection.recreate()
    }
  }


  def apply[T](call: ZooKeeper => T): T = {
    connection.apply(call)
  }


  private def _create(
    path: String,
    data: ZKData,
    persistent: Boolean = false,
    sequential: Boolean = false): Unit =
  {
    val mode = createMode(persistent, sequential)

    this { client =>
      client.create(path, data.orNull, AnyACL, mode)
    }
  }


  def create[T: ZKEncoder](
    path: String,
    value: T = None,
    persistent: Boolean = false,
    sequential: Boolean = false) =
  {
    val realPath = resolvePath(path)
    createPathTo(realPath)
    _create(realPath, encode(value), persistent, sequential)
  }


  def createChild[T: ZKEncoder](
    path: String,
    child: String,
    value: T = None,
    persistent: Boolean = false,
    sequential: Boolean = false) =
  {
    val realPath = resolvePath(path / child)
    _create(realPath, encode(value), persistent, sequential)
  }


  def createPath(path: String) = createPathTo(path / "fake")


  private def createPathTo(node: String): Unit = {
    val realPath = parent(resolvePath(node))

    if (!exists(realPath)) {
      createPathTo(realPath)
      _create(realPath, None, persistent = true)
    }
  }


  def meta(path: String, watcher: Option[ZKEventWatcher] = None) = {
    this { client =>
      val maybeStat = Option {
        client.exists(resolvePath(path), watcher.orNull)
      }
      maybeStat.map(_.toMeta)
    }
  }


  def getNode(path: String, watcher: Option[ZKEventWatcher] = None) = {
    val meta = new Stat

    val maybeData = this { client =>
      catching(classOf[NoNodeException]) opt {
        client.getData(resolvePath(path), watcher.orNull, meta)
      }
    }

    maybeData map { data =>
      ZKNode(path, Option(data), meta)
    }
  }


  def set[T: ZKEncoder](
    path: String,
    value: T,
    version: Int = AnyVersion) =
  {
    val realPath = resolvePath(path)
    val data = encode(value)
    this { _.setData(realPath, data.orNull, version) }
  }


  def delete(
    path: String,
    recursive: Boolean = false,
    version: Int = AnyVersion): Unit =
  {
    if (recursive) {
      for (child <- children(path)) {
        val childPath = path/child
        delete(childPath, recursive = true)
      }
    }

    this { client =>
      client.delete(resolvePath(path), version)
    }
  }


  def children(
    path: String,
    absolutePaths: Boolean = false,
    watcher: Option[ZKEventWatcher] = None) =
  {
    this { client =>
      val raw = client.getChildren(resolvePath(path), watcher.orNull).toList
      if (absolutePaths) raw map { path/_ } else raw
    }
  }


  def isEphemeral(path: String) = {
    meta(path).map(_.ephemeral).getOrElse(false)
  }


  def watch
    (path: String, persistent: Boolean = true)
    (reaction: Reaction[ZKEvent]): ZKEventWatcher =
  {
    val callback = reaction orElse Reaction.empty[ZKEvent]
    val watcher = new DefaultNodeWatcher(this, path, callback, persistent)
    watch(path, watcher)
  }

  /**
   * Sets up a watcher on node events.
   */
  def watch(path: String, watcher: ZKEventWatcher): ZKEventWatcher = {
    val nodeExists = exists(path, Some(watcher))
    if (nodeExists) children(path, watcher = Some(watcher))
    watcher
  }
}


// vim: set ts=2 sw=2 et:
