package com.ataraxer.zooowner

import scala.concurrent.duration._

import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.Watcher.Event.KeeperState._


object Zooowner {

}


case class Zooowner(servers: String,
                    timeout: FiniteDuration,
                    pathPrefix: String)
{
  private var client: ZooKeeper = null

  val watcher = Watcher {
    case SyncConnected => assert { isConnected == true }

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

  def isConnected = client.getState == States.CONNECTED

  connect()
}


// vim: set ts=2 sw=2 et:
