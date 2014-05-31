package com.ataraxer.zooowner

import com.ataraxer.test.UnitSpec
import com.ataraxer.zooowner.message._

import org.apache.zookeeper.data.Stat
import org.apache.curator.test.TestingServer

import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._



object AsyncZooownerSpec {
  val port = 9181
  val zkAddress = "localhost:%d".format(port)
}


class AsyncZooownerSpec extends UnitSpec with Eventually {
  import ZooownerSpec._

  implicit val eventuallyConfig =
    PatienceConfig(timeout = 10.seconds)

  var zkServer: TestingServer = null
  var zk: Zooowner with Async = null


  before {
    zkServer = new TestingServer(port)
    zk = new Zooowner(zkAddress, 15.seconds, "prefix") with Async
    eventually { zk.isConnected should be (true) }
  }


  after {
    zkServer.stop()
    zkServer = null
    zk.close()
    zk = null
  }


  "Zooowner.async" should "create nodes with paths asynchronously" in {
    var done = false

    zk.async.create("node/with/long/path", Some("value"), recursive = true) {
      case NodeCreated(_, _) => done = true
    }

    eventually { done should be (true) }

    zk.get("node/with/long") should be (None)
    zk.get("node/with/long/path") should be (Some("value"))
  }


  it should "create nodes with paths filled with specified value " +
            "asynchronously" in
  {
    var done = false

    zk.async.create(
      "node/with/long/path",
      Some("value"),
      recursive = true,
      filler = Some("filler")
    ) { case NodeCreated(_, _) => done = true }

    eventually { done should be (true) }

    zk.get("node") should be (Some("filler"))
    zk.get("node/with/long") should be (Some("filler"))
    zk.get("node/with/long/path") should be (Some("value"))
  }


  it should "return stat of the node asynchronously" in {
    zk.create("node", Some("value"))

    var result = Option.empty[Stat]

    zk.async.stat("node") {
      case NodeStat(_, stat) => result = Option(stat)
    }

    eventually { result should not be (None) }
  }


  it should "return Some(value) if node exists asynchronously" in {
    zk.create("node", Some("value"))

    var result = Option.empty[String]

    zk.async.get("node") {
      case NodeData(_, data) => result = data
    }

    eventually { result should be (Some("value")) }
  }


  it should "return None if node doesn't exist asynchronously" in {
    var result = Option.empty[String]

    zk.async.get("non-existant-node") {
      case NodeData(_, data) => result = data
    }

    eventually { result should be (None) }
  }


  it should "change values of created nodes asynchronously" in {
    zk.create("node", Some("first value"))

    zk.get("node") should be (Some("first value"))

    var done = false

    zk.async.set("node", "second value") {
      case _: NodeStat => done = true
    }

    eventually { done should be (true) }

    zk.get("node") should be (Some("second value"))
  }


  it should "delete nodes asynchronously" in {
    zk.create("node", Some("first value"))

    var done = false

    zk.async.delete("node") {
      case _: NodeDeleted => done = true
    }

    eventually { done should be (true) }

    zk.exists("node") should be (false)
  }


  it should "delete nodes recursively asynchronously" in {
    zk.create("node", Some("first value"), persistent = true)
    zk.create("node/child", Some("child value"), persistent = true)

    var done = false

    zk.async.delete("node", recursive = true) {
      case _: NodeDeleted => done = true
    }

    eventually { done should be (true) }

    zk.exists("node") should be (false)
    zk.exists("node/child") should be (false)
  }

}


// vim: set ts=2 sw=2 et:
