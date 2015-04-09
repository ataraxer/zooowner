package zooowner
package actor

import akka.actor.ActorSystem
import akka.testkit._

import zooowner.test.ZooownerMock
import zooowner.mocking.ZKMock
import zooowner.message._

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
    zk ! CreateNode("foo", Some("value"))
    val response = expectMsgType[NodeCreated]
    response.path should be ("foo")
  }


  it should "delete nodes asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"))
    zk.underlyingActor.zk.exists("foo") should be (true)

    zk ! DeleteNode("foo")
    expectMsg { NodeDeleted("foo") }

    zk.underlyingActor.zk.exists("foo") should be (false)
  }


  it should "change values of created nodes asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"))

    zk ! SetNodeValue("/foo", "new-value")

    val result = expectMsgType[NodeMeta]
    result.path should be ("/foo")
    zk.underlyingActor.zk.get[String]("foo") should be (Some("new-value"))
  }


  it should "get values of existing nodes asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"))

    zk ! GetNodeValue("/foo")

    val Node("/foo", node) = expectMsgType[Node]
    node.path should be ("/foo")
    node.extract[String] should be ("value")
  }


  it should "get node's children asynchronously" in {
    zk.underlyingActor.zk.create("foo", Some("value"), persistent = true)
    zk.underlyingActor.zk.create("foo/a", Some("value"))
    zk.underlyingActor.zk.create("foo/b", Some("value"))

    zk ! GetNodeChildren("/foo")

    val result = expectMsgType[NodeChildren]
    result.path should be ("/foo")
    result.children should contain only ("a", "b")
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


  it should "support custom serializer" in {
    val alice = Person("Alice", 21)
    val bob = Person("Bob", 42)

    zk ! CreateNode("/bob", bob)
    val NodeCreated("bob", None) = expectMsgType[NodeCreated]
    //node.extract[Person] should be (bob)

    zk ! SetNodeValue("bob", alice)
    expectMsgType[NodeMeta]

    zk ! GetNodeValue("bob")
    val Node("bob", newNode) = expectMsgType[Node]
    newNode.path should be ("/bob")
    newNode.extract[Person] should be (alice)
  }
}


// vim: set ts=2 sw=2 et:
