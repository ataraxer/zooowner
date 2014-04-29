package com.ataraxer.zooowner

import scala.concurrent.duration._

import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.Watcher.Event.KeeperState._
import org.apache.zookeeper.CreateMode._
import org.apache.zookeeper.KeeperException


object Zooowner {

}


case class Zooowner(servers: String,
                    timeout: FiniteDuration,
                    pathPrefix: String)
{
  private var client: ZooKeeper = null

  // path prefix should be simple identifier
  if (pathPrefix contains "/")
    throw new IllegalArgumentException

  def absolutePath(path: String) =
    "/" + pathPrefix + "/" + path

  private var _onConnection: () => Unit = null

  def onConnection(action: () => Unit) {
    _onConnection = action
    if (isConnected) {
      _onConnection
    }
  }

  val watcher = Watcher {
    case SyncConnected => {
      assert { isConnected == true }
      create("/" + pathPrefix, null)
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

  def isConnected = client.getState == States.CONNECTED

  connect()

  def create(path: String, data: String,
             persisten: Boolean = false,
             sequential: Boolean = false)
  {
    val createMode = (persisten, sequential) match {
      case (true, true)   => PERSISTENT_SEQUENTIAL
      case (true, false)  => PERSISTENT
      case (false, true)  => EPHEMERAL_SEQUENTIAL
      case (false, false) => EPHEMERAL
    }

    try {
      client.create(absolutePath(path), data.getBytes, null, createMode)
    } catch {
      case e: KeeperException => println(e)
      case e: InterruptedException => println(e)
    }
  }

  def exists(path: String) =
    client.exists(absolutePath(path), false) != null

  def get(path: String) =
    client.getData(absolutePath(path), null, null).toString

  def set(path: String, data: String) =
    client.setData(absolutePath(path), data.getBytes, -1)
}


// vim: set ts=2 sw=2 et:
