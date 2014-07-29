package com.ataraxer.zooowner

import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.Watcher.Event.KeeperState
import scala.concurrent.duration._


class ZooownerMock(generator: () => ZooKeeper)
    extends Zooowner("", 1.second, "prefix")
{
  override def generateClient = generator()

  override def connect() = {
    super.connect()
    watcher.reaction(KeeperState.SyncConnected)
  }

  watcher.reaction(KeeperState.SyncConnected)
}


// vim: set ts=2 sw=2 et:
