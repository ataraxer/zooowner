package zooowner
package actor

import akka.actor.ActorSystem
import akka.testkit._

import zooowner.test.ZooownerMock
import zooowner.mocking.ZKMock
import zooowner.message._
import zooowner.ZKPathDSL._

import org.scalatest.concurrent.Eventually

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
    Some(buffer.array())
  }

  implicit val customDecoder = ZKDecoder[Person] { data =>
    val buffer = ByteBuffer.wrap(data getOrElse Array.empty[Byte])
    val nameSize = buffer.getInt
    val nameBytes = new Array[Byte](nameSize)
    buffer.get(nameBytes)
    val name = new String(nameBytes)
    val age = buffer.getInt
    Person(name, age)
  }
}


class ZooownerActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with Eventually
  with UnitSpec
{
  import ZooownerActorSpec._
  import DefaultSerializers._

  def this() = this { ActorSystem("zooowner-actor-spec") }

  var zk: TestActorRef[ZooownerActor] = null


  before {
    zk = TestActorRef {
      new ZooownerActor("", 1.second) with ZKMock {
        override val zk = {
          val watcher: ZKConnectionWatcher = {
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


  "ZooownerActor" should "create nodes asynchronously" in {
    zk ! CreateNode("/foo", Some("value"))
    val response = expectMsgType[NodeCreated]
    response.path should be (zk"/foo")
  }


  it should "delete nodes asynchronously" in {
    zk.underlyingActor.zk.create("/foo", Some("value"))
    zk.underlyingActor.zk.exists("/foo") should be (true)

    zk ! DeleteNode("/foo")
    expectMsg { NodeDeleted(zk"/foo") }

    zk.underlyingActor.zk.exists("/foo") should be (false)
  }


  it should "change values of created nodes asynchronously" in {
    zk.underlyingActor.zk.create("/foo", Some("value"))

    zk ! SetNodeValue("/foo", "new-value")

    val result = expectMsgType[NodeMeta]
    result.path should be (zk"/foo")
    zk.underlyingActor.zk("/foo")[String] should be ("new-value")
  }


  it should "get values of existing nodes asynchronously" in {
    zk.underlyingActor.zk.create("/foo", Some("value"))

    zk ! GetNodeValue("/foo")

    val Node(zk"/foo", node) = expectMsgType[Node]
    node.path should be (zk"/foo")
    node.extract[String] should be ("value")
  }


  it should "get node's children asynchronously" in {
    zk.underlyingActor.zk.create("/foo", "value", persistent = true)
    zk.underlyingActor.zk.create("/foo/a", "value")
    zk.underlyingActor.zk.create("/foo/b", "value")

    zk ! GetNodeChildren("/foo")

    val result = expectMsgType[NodeChildren]
    result.path should be (zk"/foo")
    result.children should contain theSameElementsAs Seq(
      zk"/foo/a",
      zk"/foo/b")
  }


  it should "watch node events and report them back to watcher" in {
    zk.underlyingActor.zk.create("/foo", Some("value"), persistent = true)

    zk ! WatchNode("/foo")

    zk.underlyingActor.zk.set("/foo", "new-value")

    expectMsgPF(5.seconds) {
      case NodeChanged(zk"/foo", Some(node)) =>
        node.extract[String] should be ("new-value")
    }
  }


  it should "support custom serializer" in {
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


// vim: set ts=2 sw=2 et:
