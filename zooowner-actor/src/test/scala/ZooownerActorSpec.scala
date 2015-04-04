package zooowner
package actor

import akka.actor.ActorSystem
import akka.testkit._

import zooowner.ZKConnection.ConnectionWatcher
import zooowner.test.ZooownerMock
import zooowner.mocking.ZKMock
import zooowner.message._

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
      new ZooownerActor("", 1.second) with ZKMock {
        override val zk = {
          val watcher: ConnectionWatcher = {
            case event => self ! event
          }

          new ZooownerMock(zkMock.createMock _, watcher) with AsyncAPI
        }
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
    expectMsg { NodeCreated("/foo", None) }
  }


  it should "delete nodes asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"))
    zk.underlyingActor.zk.exists("foo") should be (true)

    zk ! DeleteNode("foo")
    expectMsgPF(5.seconds) { case NodeDeleted("/foo") => }

    zk.underlyingActor.zk.exists("foo") should be (false)
  }


  it should "change values of created nodes asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"))

    zk ! SetNodeValue("foo", "new-value")
    expectMsgPF(5.seconds) { case NodeMeta("/foo", _) => }

    zk.underlyingActor.zk.get[String]("foo") should be (Some("new-value"))
  }


  it should "get values of existing nodes asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"))

    zk ! GetNodeValue("foo")
    expectMsgPF(3.seconds) {
      case Node(node) => {
        node.path should be ("/foo")
        node.extract[String] should be ("value")
      }
    }
  }


  it should "get node's children asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"), persistent = true)
    zk.underlyingActor.zk.create("foo/a", Some("value"))
    zk.underlyingActor.zk.create("foo/b", Some("value"))

    zk ! GetNodeChildren("foo")
    expectMsgPF(5.seconds) {
      case NodeChildren("/foo", children) =>
        children should contain only ("a", "b")
    }
  }


  it should "watch node events and report them back to watcher" in {
    zk.underlyingActor.zk.create("foo", Some("value"), persistent = true)

    zk ! WatchNode("foo")

    zk.underlyingActor.zk.set("foo", "new-value")

    expectMsgPF(5.seconds) {
      case NodeChanged("foo", Some(node)) =>
        node.get should be ("new-value")
    }
  }

}


// vim: set ts=2 sw=2 et:
