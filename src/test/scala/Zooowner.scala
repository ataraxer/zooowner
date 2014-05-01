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


  it should "create ephemeral nodes by default" in {
    val zk = Zooowner("localhost:9181", 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.create("test", "value")

    eventually { zk.exists("test") should be (true) }

    zk.close()
  }

}


// vim: set ts=2 sw=2 et:
