package zooowner

import zooowner.test.ZooownerMock
import zooowner.mocking.ZKMock
import zooowner.message._

import org.apache.zookeeper.data.Stat

import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


class AsyncZooownerSpec extends UnitSpec with Eventually {
  import DefaultSerializers._

  implicit val eventuallyConfig = PatienceConfig(timeout = 3.seconds)


  trait Env extends ZKMock {
    val zk = new ZooownerMock(zkMock.createMock _) with AsyncAPI
    zk.isConnected should be (true)
  }


  "Async Zooowner" should "create nodes asynchronously" in new Env {
    val result = zk.async.create("node", Some("value"))

    result.futureValue should be ("node")
    zk.get[String]("node") should be (Some("value"))
  }


  it should "return node meta of the node asynchronously" in new Env {
    zk.create("node", Some("value"))

    val result = zk.async.meta("node")
    result.futureValue should not be (None)
  }


  it should "return Some(value) if node exists" in new Env {
    zk.create("node", Some("value"))

    val result = zk.async.get("node")
    result.futureValue.extract[String] should be ("value")
  }


  it should "return Failure[NoNode] if node doesn't exist" in new Env {
    val result = zk.async.get("non-existant-node") recover {
      case _: NoNodeException => true
    }

    result.mapTo[Boolean].futureValue should be (true)
  }


  it should "get node's children asynchronously" in new Env {
    zk.create("parent", persistent = true)
    zk.create("parent/child-a")
    zk.create("parent/child-b")

    val result = zk.async.children("parent")
    result.futureValue should contain only ("child-a", "child-b")
  }


  it should "change values of created nodes asynchronously" in new Env {
    zk.create("node", Some("first value"))

    zk.get[String]("node") should be (Some("first value"))

    val result = zk.async.set("node", "second value")
    result.futureValue should not be (None)
    zk.get[String]("node") should be (Some("second value"))
  }


  it should "delete nodes asynchronously" in new Env {
    zk.create("node", Some("first value"))

    val result = zk.async.delete("node")
    result.isReadyWithin(3.seconds)
    zk.exists("node") should be (false)
  }

}


// vim: set ts=2 sw=2 et:
