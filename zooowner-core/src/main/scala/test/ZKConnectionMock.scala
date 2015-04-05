package zooowner
package test

import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.Watcher.Event.KeeperState
import scala.concurrent.duration._


class ZKConnectionMock(
    override val client: ZooKeeper,
    connectionWatcher: ZKConnectionWatcher)
  extends impl.ZKConnectionImpl("", 1.second, connectionWatcher, None)
{
  stateWatcher.dispatch(KeeperState.SyncConnected)
}


// vim: set ts=2 sw=2 et:
