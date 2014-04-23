package com.ataraxer.zooowner

import scala.concurrent.duration._

import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.Watcher.Event.KeeperState._


object Zooowner {

}


case class Zooowner(hosts: String, timeout: FiniteDuration) {
  private var connected = false
  private var client: ZooKeeper = null

  val watcher = Watcher {
    case SyncConnected => connected = true
  }

  def connect() {
    client = new ZooKeeper(hosts, timeout.toMillis.toInt, watcher)
  }

  def disconnect() {
    client.close()
  }

}


// vim: set ts=2 sw=2 et:
