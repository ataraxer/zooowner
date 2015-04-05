package zooowner
package test

import org.apache.zookeeper.ZooKeeper
import scala.concurrent.duration._


class ZooownerMock(
    createClient: () => ZooKeeper,
    connectionWatcher: ZKConnectionWatcher = NoWatcher)
  extends impl.ZooownerImpl(
    new ZKConnectionMock(createClient(), connectionWatcher))


// vim: set ts=2 sw=2 et:
