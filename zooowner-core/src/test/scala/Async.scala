package com.ataraxer.zooowner

import com.ataraxer.test.UnitSpec
import com.ataraxer.zooowner.test.ZooownerMock
import com.ataraxer.zooowner.mocking.ZKMock
import com.ataraxer.zooowner.message._

import org.apache.zookeeper.data.Stat

import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._


class AsyncZooownerSpec extends UnitSpec with Eventually {
  import DefaultSerializers._

  implicit val eventuallyConfig =
    PatienceConfig(timeout = 3.seconds)


  trait Env extends ZKMock {
    val zk = new ZooownerMock(zkMock.createMock _) with Async
    zk.isConnected should be (true)
  }


  "Async Zooowner" should "create nodes asynchronously" in new Env {
    var done = false

    zk.async.create("node", Some("value")) {
      case NodeCreated(_, _) => done = true
    }

    eventually { done should be (true) }

    zk.get[String]("node") should be (Some("value"))
  }


  it should "return stat of the node asynchronously" in new Env {
    zk.create("node", Some("value"))

    var result = Option.empty[common.ZKNodeMeta]

    zk.async.stat("node") {
      case NodeMeta(_, stat) => result = Some(stat)
    }

    eventually { result should not be (None) }
  }


  it should "return Some(value) if node exists asynchronously" in new Env {
    zk.create("node", Some("value"))

    var result = Option.empty[String]

    zk.async.get("node") {
      case node: Node => result = Some(node.extract[String])
    }

    eventually { result should be (Some("value")) }
  }


  it should "return None if node doesn't exist asynchronously" in new Env {
    var result = Option.empty[Any]

    zk.async.get("non-existant-node") {
      case node: Node => result = node.data
    }

    eventually { result should be (None) }
  }


  it should "get node's children asynchronously" in new Env {
    var result = List.empty[String]
    zk.create("parent", persistent = true)
    zk.create("parent/child-a")
    zk.create("parent/child-b")

    zk.async.children("parent") {
      case NodeChildren(_, children) => result = children
    }

    eventually { result should contain only ("child-a", "child-b") }
  }


  it should "change values of created nodes asynchronously" in new Env {
    zk.create("node", Some("first value"))

    zk.get[String]("node") should be (Some("first value"))

    var done = false

    zk.async.set("node", "second value") {
      case _: NodeMeta=> done = true
    }

    eventually { done should be (true) }

    zk.get[String]("node") should be (Some("second value"))
  }


  it should "delete nodes asynchronously" in new Env {
    zk.create("node", Some("first value"))

    var done = false

    zk.async.delete("node") {
      case _: NodeDeleted => done = true
    }

    eventually { done should be (true) }

    zk.exists("node") should be (false)
  }

}


// vim: set ts=2 sw=2 et:
