package com.ataraxer.zooowner

import scala.concurrent.duration._

import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.Watcher.Event.KeeperState._
import org.apache.zookeeper.CreateMode._
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.ZooDefs.Ids

import scala.collection.JavaConversions._
import scala.util.control.Exception._

import java.util.{List => JavaList}


object Zooowner {
  val AnyVersion = -1
}


case class Zooowner(servers: String,
                    timeout: FiniteDuration,
                    pathPrefix: String)
{
  import Zooowner._

  // path prefix should be simple identifier
  if (pathPrefix contains "/")
    throw new IllegalArgumentException

  private var client: ZooKeeper = null

  private def prefixedPath(path: String) = {
    // path should always start from slash
    if (path startsWith "/") {
      throw new IllegalArgumentException
    }
    "/" + pathPrefix + "/" + path
  }

  private def resolvePath(path: String) =
    if (path startsWith "/") path else prefixedPath(path)

  private var _onConnection: () => Unit = null

  def onConnection(action: () => Unit) {
    _onConnection = action
    if (isConnected) {
      _onConnection
    }
  }

  private val watcher = Watcher {
    case SyncConnected => {
      assert { isConnected == true }

      ignoring(classOf[NodeExistsException]) {
        create("/" + pathPrefix, persistent = true)
      }

      if (_onConnection != null) {
        _onConnection()
      }
    }

    case Disconnected | Expired => connect()
  }

  private def connect() {
    if (client != null) disconnect()
    client = new ZooKeeper(servers, timeout.toMillis.toInt, watcher)
  }

  private def disconnect() {
    client.close()
    client = null
  }

  def close() { disconnect() }

  def isConnected =
    client.getState == States.CONNECTED

  connect()

  def create(path: String,
             maybeData: Option[String] = None,
             persistent: Boolean = false,
             sequential: Boolean = false)
  {
    val createMode = (persistent, sequential) match {
      case (true, true)   => PERSISTENT_SEQUENTIAL
      case (true, false)  => PERSISTENT
      case (false, true)  => EPHEMERAL_SEQUENTIAL
      case (false, false) => EPHEMERAL
    }

    val data = maybeData.map( _.getBytes("utf8") ).getOrElse(null)

    try {
      client.create(resolvePath(path), data,
                    Ids.OPEN_ACL_UNSAFE,
                    createMode)
    } catch {
      case e: NodeExistsException =>
      case e: KeeperException => println(e)
      case e: InterruptedException => println(e)
    }
  }

  def exists(path: String) =
    client.exists(resolvePath(path), false) != null

  def get(path: String) =
    catching(classOf[NoNodeException]).opt {
      new String(client.getData(resolvePath(path), null, null))
    }

  def set(path: String, data: String) =
    client.setData(resolvePath(path), data.getBytes, AnyVersion)

  def delete(path: String, recursive: Boolean = false): Unit = {
    if (recursive) {
      for (child <- children(path)) {
        val childPath = path + "/" + child
        delete(childPath, recursive = true)
      }
    }

    client.delete(resolvePath(path), AnyVersion)
  }

  def children(path: String) =
    client.getChildren(resolvePath(path), false)

  def isEphemeral(path: String) = {
    val nodeState = client.exists(resolvePath(path), false)
    (nodeState != null) && (nodeState.getEphemeralOwner != 0)
  }

}


// vim: set ts=2 sw=2 et:
