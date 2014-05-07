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


  before {
    zkServer = new TestingServer(port)
  }


  after {
    zkServer.stop()
    zkServer = null
  }


  "Zooowner" should "connect to ZooKeeper" in {
    val zk = Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.close()
  }


  it should "create root node on connection" in {
    val zk = Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.exists("/prefix") should be (true)

    zk.close()
  }


  it should "return Some(value) if node exists" in {
    val zk = Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.create("node", Some("value"))

    zk.get("node") should be (Some("value"))

    zk.close()
  }


  it should "return None if node doesn't exist" in {
    val zk = Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.get("non-existant-node") should be (None)

    zk.close()
  }


  it should "change values of created nodes" in {
    val zk = Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.create("node", Some("first value"))

    zk.get("node") should be (Some("first value"))
    zk.set("node", "second value")
    zk.get("node") should be (Some("second value"))

    zk.close()
  }


  it should "delete nodes" in {
    val zk = Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.create("node", Some("first value"))
    zk.delete("node")

    zk.exists("node") should be (false)

    zk.close()
  }


  it should "delete nodes recursively" in {
    val zk = Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.create("node", Some("first value"), persistent = true)
    zk.create("node/child", Some("child value"), persistent = true)
    zk.delete("node", recursive = true)

    zk.exists("node") should be (false)
    zk.exists("node/child") should be (false)

    zk.close()
  }

}


// vim: set ts=2 sw=2 et:
