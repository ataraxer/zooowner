package zooowner
package actor

import akka.actor.ActorSystem
import akka.testkit._

import zooowner.test.ZooownerMock
import zooowner.mocking.ZKMock
import zooowner.message._
import zooowner.ZKPathDSL._

import org.scalatest._

import scala.util.Failure
import scala.concurrent.duration._

import java.nio.ByteBuffer


object ZooownerActorSpec {
  case class Person(name: String, age: Int)

  implicit val customEncoder = ZKEncoder[Person] { person =>
    val size = 4 + person.name.size + 4
    val buffer = ByteBuffer.allocate(size)
    buffer.putInt(person.name.size)
    buffer.put(person.name.getBytes)
    buffer.putInt(person.age)
    buffer.rewind()
    buffer.array()
  }

  implicit val customDecoder = ZKDecoder[Person] { data =>
    val buffer = ByteBuffer.wrap(data)
    val nameSize = buffer.getInt
    val nameBytes = new Array[Byte](nameSize)
    buffer.get(nameBytes)
    val name = new String(nameBytes)
    val age = buffer.getInt
    Person(name, age)
  }
}


class ZooownerActorSpec
  extends TestKit(ActorSystem("zooowner-actor-spec"))
  with UnitSpec
{
  import ZooownerActorSpec._
  import DefaultSerializers._


  class Env(implicit system: ActorSystem)
    extends TestKit(system)
    with ZKMock
    with ImplicitSender
  {
    val zk = TestActorRef {
      new ZooownerActor(null, "", 1.second) {
        override val zk = {
          val watcher = ZKConnectionWatcher { case event => self ! event }
          new ZooownerMock(zkMock.createMock _, watcher) with AsyncAPI
        }
      }
    }

    def zkClient = zk.underlyingActor.zk

    def cleanup(): Unit = {
      val probe = TestProbe()
      probe.watch(zk)
      system.stop(zk)
      probe.expectTerminated(zk)
    }

    def withCleanup(body: => Any) = {
      try body finally cleanup()
    }
  }


  "ZooownerActor" should "create nodes asynchronously" in new Env {
    withCleanup {
      zk ! CreateNode("/foo", "value")
      val response = expectMsgType[NodeCreated]
      response.path should be (zk"/foo")
      response.node should be (None)
    }
  }


  it should "return error messages with cause of failure for path" in new Env {
    withCleanup {
      zkClient.create("/foo")

      zk ! CreateNode("/foo")
      val response = expectMsgType[ZKFailure]
      response should be (NodeExists("/foo"))
    }
  }


  it should "delete nodes asynchronously" in new Env {
    withCleanup {
      zkClient.create("/foo")
      zkClient.exists("/foo") should be (true)

      zk ! DeleteNode("/foo")
      expectMsg { NodeDeleted(zk"/foo") }

      zkClient.exists("/foo") should be (false)
    }
  }


  it should "change values of created nodes asynchronously" in new Env {
    withCleanup {
      zkClient.create("/foo", "value")
      zkClient("/foo")[String] should be ("value")

      zk ! SetNodeValue("/foo", "new-value")

      val result = expectMsgType[NodeMeta]
      result.path should be (zk"/foo")
      zkClient("/foo")[String] should be ("new-value")
    }
  }


  it should "get values of existing nodes asynchronously" in new Env {
    withCleanup {
      zkClient.create("/foo", "value")

      zk ! GetNodeValue("/foo")

      val Node(zk"/foo", node) = expectMsgType[Node]
      node.path should be (zk"/foo")
      node.extract[String] should be ("value")
    }
  }


  it should "get node's children asynchronously" in new Env {
    withCleanup {
      zkClient.create("/foo", "value")
      zkClient.create("/foo/a", "value")
      zkClient.create("/foo/b", "value")

      zk ! GetNodeChildren("/foo")

      val result = expectMsgType[NodeChildren]
      result.path should be (zk"/foo")
      result.children should contain theSameElementsAs Seq(
        zk"/foo/a",
        zk"/foo/b")
    }
  }


  it should "return message on connection loss" in new Env {
    withCleanup {
      zkMock.expireSession()

      zk ! GetNodeValue("/foo")
      val response = expectMsgType[ZKFailure]
      response should be (ConnectionLost("/foo", Expired))
    }
  }


  it should "watch node events and report them back to watcher" in new Env {
    withCleanup {
      zkClient.create("/foo", "value")
      zkClient("/foo")[String] should be ("value")

      zk ! WatchNode("/foo")

      zkClient.set("/foo", "new-value")

      val NodeChanged(zk"/foo", Some(node)) = expectMsgType[NodeChanged]
      node.extract[String] should be ("new-value")
    }
  }


  it should "report failure to watcher" in new Env {
    withCleanup {
      zkClient.create("/foo")

      zk ! WatchNode("/foo")
      zkMock.expireSession()
      zkMock.fireChildrenChangedEvent("/foo")

      val response = expectMsgType[ZKFailure]
      response should be (ConnectionLost("/foo", Expired))
    }
  }


  it should "support custom serializer" in new Env {
    withCleanup {
      val alice = Person("Alice", 21)
      val bob = Person("Bob", 42)

      zk ! CreateNode("/bob", bob)
      val NodeCreated(zk"/bob", None) = expectMsgType[NodeCreated]
      //node.extract[Person] should be (bob)

      zk ! SetNodeValue("/bob", alice)
      expectMsgType[NodeMeta]

      zk ! GetNodeValue("/bob")
      val Node(zk"/bob", newNode) = expectMsgType[Node]
      newNode.path should be (zk"/bob")
      newNode.extract[Person] should be (alice)
    }
  }
}


// vim: set ts=2 sw=2 et:
