package com.ataraxer.zooowner

import com.ataraxer.test.UnitSpec

import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._


class ZooownerSpec extends UnitSpec with Eventually {

  implicit val eventuallyConfig =
    PatienceConfig(timeout = 10.seconds)


  "Zooowner" should "connect to ZooKeeper" in {
    val zk = Zooowner("localhost:9181", 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.close()
  }


  it should "create root node on connection" in {
    val zk = Zooowner("localhost:9181", 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    eventually { zk.exists("/prefix") should be (true) }

    zk.close()
  }


  it should "get and set values of created nodes" in {
    val zk = Zooowner("localhost:9181", 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.create("node", Some("first value"))

    zk.get("node") should be ("first value")
    zk.set("node", "second value")
    zk.get("node") should be ("second value")

    zk.close()
  }


  it should "delete nodes" in {
    val zk = Zooowner("localhost:9181", 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.create("node", Some("first value"))
    zk.delete("node")

    eventually { zk.exists("node") should be (false) }

    zk.close()
  }

}


// vim: set ts=2 sw=2 et:
