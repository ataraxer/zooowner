package com.ataraxer.zooowner.mocking

import com.ataraxer.test.UnitSpec

import org.apache.zookeeper.CreateMode._
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.{Watcher, WatchedEvent}
import org.apache.zookeeper.Watcher.Event.{EventType, KeeperState}
import org.apache.zookeeper.ZooDefs.Ids.{OPEN_ACL_UNSAFE => AnyACL}
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.ZooKeeper.States

import scala.concurrent.duration._


class ZKMockSpec extends UnitSpec {

  trait Env extends ZKMock {
    val zk = zkMock.createMock()
  }


  "ZKMock" should "mock ZooKeeper client" in new Env {
    zk shouldBe a [ZooKeeper]
  }


  it should "be connected by default" in new Env {
    zk.getState should be (States.CONNECTED)
  }


  it should "simulate node creation" in new Env {
    val data = "some-data".getBytes
    zk.create("/some-node", data, AnyACL, PERSISTENT)

    zk.exists("/some-node", null) should not be (null)
    zk.getData("/some-node", null, null) should be (data)
  }


  it should "simulate node existance check" in new Env {
    zk.exists("/non-existing-node", null) should be (null)
    zk.exists("/non-existing-node/non-existing-child", null) should be (null)
  }


  it should "simulate children creation" in new Env {
    val data = "some-data".getBytes
    zk.create("/some-node", data, AnyACL, PERSISTENT)

    zk.getChildren("/some-node", null) shouldBe empty

    zk.create("/some-node/child-a", data, AnyACL, PERSISTENT)
    zk.create("/some-node/child-b", data, AnyACL, PERSISTENT)

    zk.getChildren("/some-node", null) should contain only ("child-a", "child-b")
  }


  it should "simulate changing of created nodes" in new Env {
    val data = "some-data".getBytes
    zk.create("/some-node", data, AnyACL, PERSISTENT)
    val newData = "some-new-data".getBytes
    zk.setData("/some-node", newData, -1)

    zk.getData("/some-node", null, null) should be (newData)
  }


  it should "simulate deletion of created nodes" in new Env {
    val data = "some-data".getBytes
    zk.create("/some-node", data, AnyACL, PERSISTENT)
    zk.exists("/some-node", null) should not be (null)
    zk.delete("/some-node", -1)

    zk.exists("/some-node", null) should be (null)
  }


  it should "simulate children deletion" in new Env {
    val data = "some-data".getBytes
    zk.create("/some-node", data, AnyACL, PERSISTENT)
    zk.create("/some-node/child-a", data, AnyACL, PERSISTENT)
    zk.create("/some-node/child-b", data, AnyACL, PERSISTENT)
    zk.getChildren("/some-node", null) should contain only ("child-a", "child-b")
    zk.delete("/some-node/child-a", -1)

    zk.getChildren("/some-node", null) should contain only ("child-b")
  }


  it should "simulate NoNodeException for client calls " +
            "on uncreated nodes" in new Env
  {
    intercept[NoNodeException] {
      zk.getData("/non-existing-node", null, null)
    }

    intercept[NoNodeException] {
      val data = "some-data".getBytes
      zk.setData("/non-existing-node", data, -1)
    }

    intercept[NoNodeException] {
      zk.getChildren("/non-existing-node", null)
    }

    intercept[NoNodeException] {
      zk.delete("/non-existing-node", -1)
    }
  }


  it should "simulate NoNodeException on attempt to create a node " +
            "under non-existing one" in new Env
  {
    intercept[NoNodeException] {
      val data = "some-data".getBytes
      zk.create("/non-existing-node/some-node", data, AnyACL, PERSISTENT)
    }
  }


  it should "simulate NoChildrenForEphemeralsException on attempt to create " +
            "a node under ephemeral one" in new Env
  {
    val data = "some-data".getBytes
    zk.create("/ephemeral-node", data, AnyACL, EPHEMERAL)

    intercept[NoChildrenForEphemeralsException] {
      zk.create("/ephemeral-node/child", data, AnyACL, EPHEMERAL)
    }
  }


  it should "simulate NodeExistsException on creation of " +
            "existing node attempt" in new Env
  {
    val data = "some-data".getBytes
    zk.create("/some-node", data, AnyACL, PERSISTENT)

    intercept[NodeExistsException] {
      zk.create("/some-node", data, AnyACL, PERSISTENT)
    }
  }


  it should "simulate NotEmptyException on node " +
            "with children deletion" in new Env
  {
    val data = "some-data".getBytes
    zk.create("/some-node", data, AnyACL, PERSISTENT)
    zk.create("/some-node/child", data, AnyACL, PERSISTENT)

    intercept[NotEmptyException] {
      zk.delete("/some-node", -1)
    }
  }


  it should "simulate NodeCreated event" in new Env {
    var eventFired = false

    val watcher = new Watcher {
      def process(event: WatchedEvent) {
        event.getType should be (EventType.NodeCreated)
        eventFired = true
      }
    }

    // set up watcher
    zk.exists("/some-node", watcher)
    val data = "some-data".getBytes
    zk.create("/some-node", data, AnyACL, PERSISTENT)

    eventFired should be (true)
  }


  it should "simulate NodeDataChanged event" in new Env {
    var eventFired = false

    val watcher = new Watcher {
      def process(event: WatchedEvent) {
        event.getType should be (EventType.NodeDataChanged)
        eventFired = true
      }
    }

    val data = "some-data".getBytes
    zk.create("/some-node", data, AnyACL, PERSISTENT)
    // set up watcher
    zk.exists("/some-node", watcher)
    val newData = "some-new-data".getBytes
    zk.setData("/some-node", newData, -1)

    eventFired should be (true)
  }


  it should "simulate NodeChildrenChanged event" in new Env {
    var eventFired = false

    val watcher = new Watcher {
      def process(event: WatchedEvent) {
        event.getType should be (EventType.NodeChildrenChanged)
        eventFired = true
      }
    }

    val data = "some-data".getBytes
    zk.create("/some-node", data, AnyACL, PERSISTENT)
    zk.create("/some-node/child-a", data, AnyACL, PERSISTENT)
    zk.create("/some-node/child-b", data, AnyACL, PERSISTENT)
    // set up watcher
    zk.getChildren("/some-node", watcher)
    // change children by deleting one of existing
    zk.delete("/some-node/child-b", -1)

    eventFired should be (true)

    eventFired = false
    // set up watcher
    zk.getChildren("/some-node", watcher)
    // change children by creating a new one
    zk.create("/some-node/child-c", data, AnyACL, PERSISTENT)

    eventFired should be (true)
  }


  it should "simulate NodeDeleted event" in new Env {
    var eventFired = false

    val watcher = new Watcher {
      def process(event: WatchedEvent) {
        event.getType should be (EventType.NodeDeleted)
        eventFired = true
      }
    }

    val data = "some-data".getBytes
    zk.create("/some-node", data, AnyACL, PERSISTENT)
    // set up watcher
    zk.exists("/some-node", watcher)
    zk.delete("/some-node", -1)

    eventFired should be (true)
  }

}


// vim: set ts=2 sw=2 et:

