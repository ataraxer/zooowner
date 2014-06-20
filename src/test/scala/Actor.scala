package com.ataraxer.zooowner

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestActorRef, TestProbe, ImplicitSender}

import com.ataraxer.test.UnitSpec
import com.ataraxer.zooowner.message._

import org.apache.curator.test.TestingServer

import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._


object ZooownerActorSpec {
  val port = 9181
  val zkAddress = "localhost:%d".format(port)
}


class ZooownerActorSpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with Eventually
    with UnitSpec
{
  import ZooownerSpec._

  def this() = this { ActorSystem("zooowner-actor-spec") }

  var zkServer: TestingServer = null
  var zk: TestActorRef[ZooownerActor] = null


  before {
    zkServer = new TestingServer(port)
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
}


// vim: set ts=2 sw=2 et:
