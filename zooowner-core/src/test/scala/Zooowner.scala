package zooowner

import zooowner.test.ZooownerMock
import zooowner.mocking.ZKMock
import zooowner.message._
import zooowner.ZKPathDSL._

import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.Watcher.Event.KeeperState

import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._


class ZooownerSpec extends UnitSpec with Eventually {
  import DefaultSerializers._

  implicit val eventuallyConfig =
    PatienceConfig(timeout = 3.seconds)


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
    var hookRan = false

    val zk = new ZooownerMock(
      zkMock.createMock _,
      connectionWatcher = { case Connected => hookRan = true })

    eventually { hookRan should be (true) }
  }


  it should "create nodes with paths" in new Env {
    zk.create("/node/with/long/path", "value")

    zk.get[String]("/node/with/long") should be (None)
    zk.get[String]("/node/with/long/path") should be (Some("value"))
  }


  it should "create nodes with paths filled with nulls" in new Env {
    zk.create("/node/with/long/path", "value").child should be ("path")

    zkMock.check.created("/node", None)
    zkMock.check.created("/node/with", None)
    zkMock.check.created("/node/with/long", None)

    zk("/node/with/long/path")[String] should be ("value")
  }


  it should "create sequential node and return it's name" in new Env {
    zk.create("/queue/node", sequential = true).child should be ("node0000000000")
    zk.create("/queue/node", sequential = true).child should be ("node0000000001")
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
    zk.create("/node", "value", persistent = true)
    zk.create("/node/foo", "foo-value")
    zk.create("/node/bar", "bar-value")

    zk.children("/node") should contain theSameElementsAs Seq(
      zk"/node/foo",
      zk"/node/bar")
  }


  it should "delete nodes" in new Env {
    zk.create("/node", "first value")
    zk.delete("/node")

    zk.exists("/node") should be (false)
  }


  it should "delete nodes recursively" in new Env {
    zk.create("/node", "first value", persistent = true)
    zk.create("/node/child", "child value", persistent = true)
    zk.delete("/node", recursive = true)

    zk.exists("/node") should be (false)
    zk.exists("/node/child") should be (false)
  }


  it should "test if node is ephemeral" in new Env {
    zk.create("/persistent-node", persistent = true)
    zk.create("/ephemeral-node", persistent = false)

    zk.isEphemeral("/persistent-node") should be (false)
    zk.isEphemeral("/ephemeral-node") should be (true)
  }


  it should "set one-time watches on nodes" in new Env {
    var created = false
    var changed = false
    var deleted = false
    var childCreated = false

    val reaction: Reaction[ZKEvent] = {
      case NodeCreated(zk"/some-node", Some(node)) =>
        node.get should be ("value")
        created = true
      case NodeChanged(zk"/some-node", Some(node)) =>
        node.get should be ("new-value")
        changed = true
      case NodeDeleted(zk"/some-node") =>
        deleted = true
      case NodeChildrenChanged(zk"/some-node", Seq(zk"/some-node/child")) =>
        childCreated = true
    }

    zk.watch("/some-node", persistent = false)(reaction)
    zk.create("/some-node", Some("value"), persistent = true)
    eventually { created should be (true) }

    zk.watch("/some-node", persistent = false)(reaction)
    zk.create("/some-node/child", Some("value"))
    eventually { childCreated should be (true) }
    // cleanup
    zk.delete("/some-node/child")

    zk.watch("/some-node", persistent = false)(reaction)
    zk.set("/some-node", "new-value")
    eventually { changed should be (true) }

    zk.watch("/some-node", persistent = false)(reaction)
    zk.delete("/some-node", recursive = true)
    eventually { deleted should be (true) }
  }


  it should "set persistent watches on nodes" in new Env {
    var created = false
    var changed = false
    var deleted = false
    var childCreated = false

    zk.watch("/some-node") {
      case NodeCreated(zk"/some-node", Some(node)) =>
        node.get should be ("value")
        created = true
      case NodeChanged(zk"/some-node", Some(node)) =>
        node.get should be ("new-value")
        changed = true
      case NodeDeleted(zk"/some-node") =>
        deleted = true
      case NodeChildrenChanged(zk"/some-node", Seq(zk"/some-node/child")) =>
        childCreated = true
    }

    zk.create("/some-node", Some("value"), persistent = true)
    eventually { created should be (true) }

    zk.create("/some-node/child", Some("value"))
    eventually { childCreated should be (true) }

    zk.set("/some-node", "new-value")
    eventually { changed should be (true) }

    zk.delete("/some-node", recursive = true)
    eventually { deleted should be (true) }
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
    zk.create("/parent", persistent = true)

    val childrenEvent = zk.watchChildren("/parent")
    zk.create("/parent/foo")

    val NodeChildrenChanged(zk"/parent", children) = childrenEvent.futureValue
    children should contain theSameElementsAs Seq(zk"/parent/foo")
  }


  it should "return cancellable watcher" in new Env {
    var created = false
    var deleted = false

    val watcher = zk.watch("/some-node") {
      case NodeCreated(zk"/some-node", Some(node)) =>
        node.get should be ("value")
        created = true
      case NodeDeleted(zk"/some-node") =>
        deleted = true
    }

    zk.create("/some-node", Some("value"))
    eventually { created should be (true) }

    watcher.stop()

    zk.delete("/some-node")
    eventually { deleted should be (false) }
  }


  it should "fail and pass Expired event to registered callback " +
            "on session expiration" in new ZKMock
  {
    var hookRan = false

    val zk = new ZooownerMock(
      zkMock.createMock _,
      connectionWatcher = { case Expired => hookRan = true })

    zkMock.expireSession()

    intercept[SessionExpiredException] {
      zk.create("/foo", Some("value"))
    }

    hookRan should be (true)
  }
}


// vim: set ts=2 sw=2 et:
