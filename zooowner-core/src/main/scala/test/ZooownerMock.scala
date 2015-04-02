package zooowner
package test

import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.Watcher.Event.KeeperState
import scala.concurrent.duration._


class ZooownerMock(generator: () => ZooKeeper)
  extends Zooowner("", 1.second)
{
  override def generateClient = generator()

  override def connect() = {
    super.connect()
    connectionWatcher foreach { watcher =>
      watcher.dispatch(KeeperState.SyncConnected)
    }
  }

  connectionWatcher = Some(generateWatcher(connectionFlag))

  connectionWatcher foreach { watcher =>
    watcher.dispatch(KeeperState.SyncConnected)
  }
}


// vim: set ts=2 sw=2 et:
