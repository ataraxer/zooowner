package zooowner

import zooowner.test.ZooownerMock
import zooowner.mocking.ZKMock
import zooowner.message._
import zooowner.ZKPathDSL._

import org.apache.zookeeper.data.Stat

import scala.concurrent.duration._


class AsyncZooownerSpec extends UnitSpec {
  import DefaultSerializers._


  trait Env extends ZKMock {
    val zk = new ZooownerMock(zkMock.createMock _) with AsyncAPI
    zk.isConnected should be (true)
  }


  "Async Zooowner" should "create nodes asynchronously" in new Env {
    val result = zk.async.create("/node", "value")

    result.futureValue should be (zk"/node")
    zk("/node")[String] should be ("value")
  }


  it should "return node meta of the node asynchronously" in new Env {
    zk.create("/node", "value")

    val result = zk.async.meta("/node")
    result.futureValue should not be (None)
  }


  it should "return Some(value) if node exists" in new Env {
    zk.create("/node", "value")

    val result = zk.async.get("/node")
    result.futureValue.extract[String] should be ("value")
  }


  it should "return Failure[NoNode] if node doesn't exist" in new Env {
    val result = zk.async.get("/non-existant-node") recover {
      case _: NoNodeException => true
    }

    result.mapTo[Boolean].futureValue should be (true)
  }


  it should "get node's children asynchronously" in new Env {
    zk.create("/parent", persistent = true)
    zk.create("/parent/child-a")
    zk.create("/parent/child-b")

    val result = zk.async.children("/parent")
    result.futureValue should contain theSameElementsAs Seq(
      zk"/parent/child-a",
      zk"/parent/child-b")
  }


  it should "change values of created nodes asynchronously" in new Env {
    zk.create("/node", "first value")

    zk("/node")[String] should be ("first value")

    val result = zk.async.set("/node", "second value")
    result.futureValue should not be (None)
    zk("/node")[String] should be ("second value")
  }


  it should "delete nodes asynchronously" in new Env {
    zk.create("/node", "first value")

    val result = zk.async.delete("/node")
    result.isReadyWithin(3.seconds)
    zk.exists("/node") should be (false)
  }


  it should "set one-time watchers on node data" in new Env {
    val createEvent = zk.async.watchData("/some-node")
    zk.create("/some-node", "initial-value")

    val NodeCreated(zk"/some-node", Some(createdNode)) = createEvent.futureValue
    createdNode.extract[String] should be ("initial-value")

    val changeEvent = zk.async.watchData("/some-node")
    zk.set("/some-node", "new-value")

    val NodeChanged(zk"/some-node", Some(changedNode)) = changeEvent.futureValue
    changedNode.extract[String] should be ("new-value")

    val deleteEvent = zk.async.watchData("/some-node")
    zk.delete("/some-node")

    val NodeDeleted(zk"/some-node") = deleteEvent.futureValue
  }


  it should "set one-time watchers on node children" in new Env {
    zk.create("/parent", persistent = true)

    val childrenEvent = zk.async.watchChildren("/parent")
    zk.create("/parent/foo")

    val NodeChildrenChanged(zk"/parent", children) = childrenEvent.futureValue
    children should contain theSameElementsAs Seq(zk"/parent/foo")
  }
}


// vim: set ts=2 sw=2 et:
