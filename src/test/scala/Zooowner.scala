package com.ataraxer.zooowner

import com.ataraxer.test.UnitSpec

import scala.concurrent.duration._


class ZooownerSpec extends UnitSpec {

  "Zooowner" should "connect to ZooKeeper" in {
    var connected = false
    var timeout = 0

    var zk: Zooowner =
      Zooowner("localhost:9181", 15.seconds, "prefix") {
        client => {
          connected = true
          client.isConnected should be (true)
        }
      }

    while (!connected) {
      timeout += 1
      if (timeout > 3) fail
      Thread.sleep(1000)
    }
  }


  it should "create ephemeral nodes by default" in {
    var connected = false
    var timeout = 0

    var zk: Zooowner =
      Zooowner("localhost:9181", 15.seconds, "prefix") {
        client => {
          connected = true
          client.create("/test", "value")
        }
      }

    while (!connected) {
      timeout += 1
      if (timeout > 3) fail
      Thread.sleep(1000)
    }
  }

}


// vim: set ts=2 sw=2 et:
