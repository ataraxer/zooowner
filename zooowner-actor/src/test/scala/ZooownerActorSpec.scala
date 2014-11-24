package com.ataraxer.zooowner
package actor

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestActorRef, TestProbe, ImplicitSender}

import com.ataraxer.test.UnitSpec
import com.ataraxer.zooowner.test.ZooownerMock
import com.ataraxer.zooowner.mocking.ZKMock
import com.ataraxer.zooowner.message._

import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._


class ZooownerActorSpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with Eventually
    with UnitSpec
{
  import DefaultSerializers._

  def this() = this { ActorSystem("zooowner-actor-spec") }

  var zk: TestActorRef[ZooownerActor] = null


  before {
    zk = TestActorRef {
      new ZooownerActor("", 1.second, Some("/prefix")) with ZKMock {
        override val zk = new ZooownerMock(zkMock.createMock _) with Async
      }
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
    zk ! CreateNode("foo", Some("value"))
    expectMsg { NodeCreated("/prefix/foo", None) }
  }


  it should "delete nodes asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"))
    zk.underlyingActor.zk.exists("foo") should be (true)

    zk ! DeleteNode("foo")
    expectMsgPF(5.seconds) { case NodeDeleted("/prefix/foo") => }

    zk.underlyingActor.zk.exists("foo") should be (false)
  }


  it should "change values of created nodes asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"))

    zk ! SetNodeValue("foo", "new-value")
    expectMsgPF(5.seconds) { case NodeMeta("/prefix/foo", _) => }

    zk.underlyingActor.zk.get[String]("foo") should be (Some("new-value"))
  }


  it should "get values of existing nodes asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"))

    zk ! GetNodeValue("foo")
    expectMsgPF(3.seconds) {
      case Node("/prefix/foo", Some("value"), _) =>
    }
  }


  it should "get node's children asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"), persistent = true)
    zk.underlyingActor.zk.create("foo/a", Some("value"))
    zk.underlyingActor.zk.create("foo/b", Some("value"))

    zk ! GetNodeChildren("foo")
    expectMsgPF(5.seconds) {
      case NodeChildren("/prefix/foo", children) =>
        children should contain only ("a", "b")
    }
  }


  it should "watch node events and report them back to watcher" in {
    zk.underlyingActor.zk.create("foo", Some("value"), persistent = true)

    zk ! WatchNode("foo")

    zk.underlyingActor.zk.set("foo", "new-value")
    expectMsg { NodeChanged("foo", Some("new-value")) }
  }

}


// vim: set ts=2 sw=2 et:
