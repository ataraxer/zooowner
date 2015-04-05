package zooowner
package test

import zooowner.ZKConnection.{ConnectionWatcher, NoWatcher}
import org.apache.zookeeper.ZooKeeper
import scala.concurrent.duration._


class ZooownerMock(
    createClient: () => ZooKeeper,
    connectionWatcher: ConnectionWatcher = NoWatcher)
  extends impl.ZooownerImpl(
    new ZKConnectionMock(createClient(), connectionWatcher))


// vim: set ts=2 sw=2 et:
