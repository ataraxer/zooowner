package com.ataraxer.zooowner

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestActorRef, TestProbe, ImplicitSender}

import com.ataraxer.test.UnitSpec
import com.ataraxer.zooowner.message._

import org.apache.curator.test.TestingServer

import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._


class ZooownerActorSpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with Eventually
    with UnitSpec
{
  import ZooownerSpec._

  def this() = this { ActorSystem("zooowner-actor-spec") }

  var zk: TestActorRef[ZooownerActor] = null


  before {
    val zkServer = new TestingServer
    val zkAddress = zkServer.getConnectString
    zk = TestActorRef {
      new ZooownerActor(zkAddress, 15.seconds, "prefix")
    }
    eventually { zk.underlyingActor.zk.isConnected should be (true) }
  }


  after {
    val probe = TestProbe()
    probe watch zk
    system stop zk
    probe expectTerminated zk
  }


  "ZooownerActor" should "create nodes with paths asynchronously" in {
    zk ! Create("foo", Some("value"))
    expectMsg { NodeCreated("/prefix/foo", None) }
  }


  it should "delete nodes asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"))
    zk.underlyingActor.zk.exists("foo") should be (true)

    zk ! Delete("foo")
    expectMsgPF(5.seconds) { case NodeDeleted("/prefix/foo") => }

    zk.underlyingActor.zk.exists("foo") should be (false)
  }


  it should "change values of created nodes asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"))

    zk ! Set("foo", "new-value")
    expectMsgPF(5.seconds) { case NodeStat("/prefix/foo", _) => }

    zk.underlyingActor.zk.get("foo") should be (Some("new-value"))
  }


  it should "get values of existing nodes asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"))

    zk ! Get("foo")
    expectMsg { NodeData("/prefix/foo", Some("value")) }
  }


  it should "get node's children asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"), persistent = true)
    zk.underlyingActor.zk.create("foo/a", Some("value"))
    zk.underlyingActor.zk.create("foo/b", Some("value"))

    zk ! GetChildren("foo")
    expectMsgPF(5.seconds) {
      case NodeChildren("/prefix/foo", children) =>
        children should contain only ("a", "b")
    }
  }


}


// vim: set ts=2 sw=2 et:
