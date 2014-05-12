package com.ataraxer.zooowner

import com.ataraxer.test.UnitSpec

import org.apache.curator.test.TestingServer

import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._


object ZooownerSpec {
  val port = 9181
  val zkAddress = "localhost:%d".format(port)
}


class ZooownerSpec extends UnitSpec with Eventually {
  import ZooownerSpec._

  implicit val eventuallyConfig =
    PatienceConfig(timeout = 10.seconds)

  var zkServer: TestingServer = null
  var zk: Zooowner = null


  before {
    zkServer = new TestingServer(port)
    zk = new Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }
  }


  after {
    zkServer.stop()
    zkServer = null
    zk.close()
    zk = null
  }


  "Zooowner" should "connect to ZooKeeper" in {
    val zk = new Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.close()
  }


  it should "create root node on connection" in {
    val zk = new Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.exists("/prefix") should be (true)

    zk.close()
  }


  it should "return Some(value) if node exists" in {
    zk.create("node", Some("value"))

    zk.get("node") should be (Some("value"))
  }


  it should "return None if node doesn't exist" in {
    zk.get("non-existant-node") should be (None)
  }


  it should "change values of created nodes" in {
    zk.create("node", Some("first value"))

    zk.get("node") should be (Some("first value"))

    zk.set("node", "second value")

    zk.get("node") should be (Some("second value"))
  }


  it should "delete nodes" in {
    zk.create("node", Some("first value"))
    zk.delete("node")

    zk.exists("node") should be (false)
  }


  it should "delete nodes recursively" in {
    zk.create("node", Some("first value"), persistent = true)
    zk.create("node/child", Some("child value"), persistent = true)
    zk.delete("node", recursive = true)

    zk.exists("node") should be (false)
    zk.exists("node/child") should be (false)
  }


  it should "test if node is ephemeral" in {
    zk.create("persistent-node", persistent = true)
    zk.create("ephemeral-node", persistent = false)

    zk.isEphemeral("persistent-node") should be (false)
    zk.isEphemeral("ephemeral-node") should be (true)
  }


  it should "set persistent watches on nodes" in {
    import com.ataraxer.zooowner.event._

    var created = false
    var changed = false
    var deleted = false

    zk.watch("some-node") {
      case NodeCreated("some-node", Some("value")) =>
        created = true
      case NodeChanged("some-node", Some("new-value")) =>
        changed = true
      case NodeDeleted("some-node") =>
        deleted = true
    }

    zk.create("some-node", Some("value"))
    eventually { created should be (true) }

    zk.set("some-node", "new-value")
    eventually { changed should be (true) }

    zk.delete("some-node")
    eventually { deleted should be (true) }
  }

}


// vim: set ts=2 sw=2 et:
