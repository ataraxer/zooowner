package zooowner
package test

import zooowner.ZKConnection.ConnectionWatcher
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.Watcher.Event.KeeperState
import scala.concurrent.duration._


class ZKConnectionMock(
    override val client: ZooKeeper,
    connectionWatcher: ConnectionWatcher)
  extends ZKConnection("", 1.second, connectionWatcher = connectionWatcher)
{
  stateWatcher.dispatch(KeeperState.SyncConnected)
}


// vim: set ts=2 sw=2 et:
