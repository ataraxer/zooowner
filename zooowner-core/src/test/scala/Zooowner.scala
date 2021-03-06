package zooowner

import zooowner.test.ZooownerMock
import zooowner.mocking.ZKMock
import zooowner.message._
import zooowner.ZKPathDSL._

import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.Watcher.Event.KeeperState

import scala.util.{Success, Failure}
import scala.concurrent.{Promise, Await, TimeoutException}
import scala.concurrent.duration._


class ZooownerSpec extends UnitSpec {
  import DefaultSerializers._


  trait Env extends ZKMock {
    val zk = new ZooownerMock(zkMock.createMock _)
    zk.isConnected should be (true)
  }


  "Zooowner" should "wait for connection on `awaitConnection`" in new ZKMock {
    val zk = new ZooownerMock(zkMock.createMock _)
    zk.awaitConnection()
    zk.isConnected should be (true)
  }


  it should "run provided hook on connection" in new ZKMock {
    val connectionPromise = Promise[Unit]()
    val disconnectionPromise = Promise[Unit]()
    val expirationPromise = Promise[Unit]()

    val connectionWatcher = ZKConnectionWatcher {
      case Connected => connectionPromise.success({})
      case Disconnected => disconnectionPromise.success({})
      case Expired => expirationPromise.success({})
    }

    val zk = new ZooownerMock(
      zkMock.createMock _,
      connectionWatcher = connectionWatcher)

    connectionPromise.future.futureValue

    zk.disconnect()
    disconnectionPromise.future.futureValue

    zkMock.expireSession()
    // cause session expired exception
    intercept[SessionExpiredException] { zk.exists("/foo") }
    expirationPromise.future.futureValue
  }


  it should "create nodes" in new Env {
    zk.create("/node").child should be ("node")
    zk.exists("/node") should be (true)

    zk.create("/node/child").child should be ("child")
    zk.exists("/node/child") should be (true)
  }


  it should "create nodes with paths filled with nulls" in new Env {
    zk.forceCreate("/node/with/long/path", "value").child should be ("path")

    zkMock.check.created("/node", None)
    zkMock.check.created("/node/with", None)
    zkMock.check.created("/node/with/long", None)

    zk("/node/with/long/path")[String] should be ("value")
  }


  it should "create sequential node and return it's name" in new Env {
    val one = zk.forceCreate("/queue/node", sequential = true)
    one.child should be ("node0000000000")

    val two = zk.forceCreate("/queue/node", sequential = true)
    two.child should be ("node0000000001")
  }


  it should "return Some(node) if it exists" in new Env {
    zk.create("/node", "value")

    val maybeNode = zk.get("/node")
    maybeNode should be ('defined)
    maybeNode.get.extract[String] should be ("value")
  }


  it should "return None if node doesn't exist" in new Env {
    zk.get("/non-existant-node") should be (None)
  }


  it should "change values of created nodes" in new Env {
    zk.create("/node", "first value")
    zk("/node")[String] should be ("first value")

    zk.set("/node", "second value")
    zk("/node")[String] should be ("second value")
  }


  it should "return a list of nodes children" in new Env {
    zk.create("/node")
    val children = Set(zk"/node/foo", zk"/node/bar")
    children foreach { path => zk.create(path) }
    // create additional sub child to check
    // that only immediate children are listed
    zk.create("/node/foo/error")

    zk.children("/node") should contain theSameElementsAs children
  }


  it should "return a list of all nodes under given path" in new Env {
    zk.create("/node")

    val children = Seq(
      zk"/node/foo",
      zk"/node/foo/foo",
      zk"/node/bar",
      zk"/node/bar/one",
      zk"/node/bar/one/two")

    children foreach { path => zk.create(path) }

    val listedChildren = zk.children("/node", recursive = true)
    listedChildren should contain theSameElementsAs children
  }


  it should "delete nodes" in new Env {
    zk.create("/node")
    zk.exists("/node") should be (true)

    zk.delete("/node") should be (Seq(zk"/node"))
    zk.exists("/node") should be (false)
  }


  it should "delete nodes recursively" in new Env {
    zk.create("/node")
    zk.create("/node/child")

    zk.exists("/node") should be (true)
    zk.exists("/node/child") should be (true)

    zk.delete("/node", recursive = true) should contain theSameElementsAs Seq(
      zk"/node",
      zk"/node/child")

    zk.exists("/node") should be (false)
    zk.exists("/node/child") should be (false)
  }


  it should "delete node's children recursively" in new Env {
    zk.create("/node")
    zk.exists("/node") should be (true)

    val children = Seq(
      zk"/node/foo",
      zk"/node/bar",
      zk"/node/bar/one",
      zk"/node/bar/two",
      zk"/node/bar/three")

    children foreach { path => zk.create(path) }

    zk.children("/node") should contain theSameElementsAs Seq(
      zk"/node/foo",
      zk"/node/bar")

    zk.deleteChildren("/node") should contain theSameElementsAs children

    zk.exists("/node") should be (true)
    zk.children("/node") should be (empty)
  }


  it should "test if node is ephemeral" in new Env {
    zk.create("/persistent-node")
    zk.create("/ephemeral-node", ephemeral = true)

    zk.isEphemeral("/persistent-node") should be (false)
    zk.isEphemeral("/ephemeral-node") should be (true)
  }


  it should "set persistent watches on nodes" in new Env {
    val created = Promise[Unit]()
    val changed = Promise[Unit]()
    val deleted = Promise[Unit]()
    val childCreated = Promise[Unit]()

    zk.watch("/some-node") {
      case Success(NodeCreated(zk"/some-node", Some(node))) =>
        node.extract[String] should be ("value")
        created.success({})
      case Success(NodeChanged(zk"/some-node", Some(node))) =>
        node.extract[String] should be ("new-value")
        changed.success({})
      case Success(NodeDeleted(zk"/some-node")) =>
        deleted.success({})
      case Success(NodeChildrenChanged(zk"/some-node", children)) =>
        children should be (Seq(zk"/some-node/child"))
        childCreated.success({})
    }

    zk.create("/some-node", Some("value"))
    created.future.futureValue

    zk.create("/some-node/child", Some("value"))
    childCreated.future.futureValue

    zk.set("/some-node", "new-value")
    changed.future.futureValue

    zk.delete("/some-node", recursive = true)
    deleted.future.futureValue
  }


  it should "set one-time watchers on node data" in new Env {
    val createEvent = zk.watchData("/some-node")
    zk.create("/some-node", "initial-value")

    val NodeCreated(zk"/some-node", Some(createdNode)) = createEvent.futureValue
    createdNode.extract[String] should be ("initial-value")

    val changeEvent = zk.watchData("/some-node")
    zk.set("/some-node", "new-value")

    val NodeChanged(zk"/some-node", Some(changedNode)) = changeEvent.futureValue
    changedNode.extract[String] should be ("new-value")

    val deleteEvent = zk.watchData("/some-node")
    zk.delete("/some-node")

    val NodeDeleted(zk"/some-node") = deleteEvent.futureValue
  }


  it should "set one-time watchers on node children" in new Env {
    zk.create("/parent")

    val childrenEvent = zk.watchChildren("/parent")
    zk.create("/parent/foo")

    val NodeChildrenChanged(zk"/parent", children) = childrenEvent.futureValue
    children should contain theSameElementsAs Seq(zk"/parent/foo")
  }


  it should "return failure on any exception in one-time watcher" in new Env {
    zk.forceCreate("/some-node/child")

    val futureEvent = zk.watchChildren("/some-node")
    zkMock.expireSession()
    zkMock.fireChildrenChangedEvent("/some-node")

    val result = futureEvent recover {
      case _: SessionExpiredException => "failed"
    }

    result.futureValue should be ("failed")
  }


  it should "return cancellable watcher" in new Env {
    val created = Promise[Unit]()
    val deleted = Promise[Unit]()

    val watcher = zk.watch("/some-node") {
      case Success(NodeCreated(zk"/some-node", Some(node))) =>
        node.extract[String] should be ("value")
        created.success({})
      case Success(NodeDeleted(zk"/some-node")) =>
        deleted.success({})
    }

    zk.create("/some-node", "value")
    created.future.futureValue

    watcher.stop()

    zk.delete("/some-node")
    intercept[TimeoutException] { Await.ready(deleted.future, 1.second) }
  }


  it should "safely handle null node data" in new Env {
    zk.create("/foo")

    intercept[ZKNodeDataIsNullException] { zk("/foo")[String] }
    val Failure(exception) = zk("/foo").tryExtract[String]
    exception shouldBe a [ZKNodeDataIsNullException]
    zk("/foo")[Option[String]] should be (None)

    zk("/foo") = "bar"

    zk("/foo")[String] should be ("bar")
  }
}


// vim: set ts=2 sw=2 et:
