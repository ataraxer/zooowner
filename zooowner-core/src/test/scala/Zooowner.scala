package zooowner

import zooowner.test.ZooownerMock
import zooowner.mocking.ZKMock
import zooowner.message._

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


  "Zooowner" should "be initialized with simple path prefix " +
                    "without slashes" in
  {
    val stubAddress = "localhost:2181"

    lazy val zkOne = new Zooowner(stubAddress, 15.seconds, Some("prefix"))
    an [IllegalArgumentException] should be thrownBy zkOne

    lazy val zkTwo = new Zooowner(stubAddress, 15.seconds, Some("prefix/"))
    an [IllegalArgumentException] should be thrownBy zkTwo
  }


  it should "wait for connection on `waitConnection`" in new ZKMock {
    val zk = new ZooownerMock(zkMock.createMock _)
    zk.waitConnection()
    zk.isConnected should be (true)
  }


  it should "create root node on connection" in new Env {
    zkMock.check.created("/prefix")
  }


  it should "run provided hook on connection" in new Env {
    var hookRan = false

    zk.watchConnection { case Connected => hookRan = true }
    eventually { hookRan should be (true) }

    zk.close()
  }


  it should "create nodes with paths" in new Env {
    zk.create("node/with/long/path", Some("value"), recursive = true)

    zk.get[String]("node/with/long") should be (None)
    zk.get[String]("node/with/long/path") should be (Some("value"))
  }


  it should "create nodes with paths filled with specified value" in new Env {
    zk.create(
      "node/with/long/path",
      Some("value"),
      recursive = true,
      filler = Some("filler")
    )

    zkMock.check.created("/prefix/node", Some("filler"))
    zkMock.check.created("/prefix/node/with", Some("filler"))
    zkMock.check.created("/prefix/node/with/long", Some("filler"))

    zk.get[String]("node/with/long/path") should be (Some("value"))
  }


  it should "return Some(value) if node exists" in new Env {
    zk.create("node", Some("value"))

    zk.get[String]("node") should be (Some("value"))
  }


  it should "return None if node doesn't exist" in new Env {
    zk.get[String]("non-existant-node") should be (None)
  }


  it should "change values of created nodes" in new Env {
    zk.create("node", Some("first value"))

    zk.get[String]("node") should be (Some("first value"))

    zk.set("node", "second value")

    zk.get[String]("node") should be (Some("second value"))
  }


  it should "return a list of nodes children" in new Env {
    zk.create("node", Some("value"), persistent = true)
    zk.create("node/foo", Some("foo-value"))
    zk.create("node/bar", Some("bar-value"))

    zk.children("node") should contain only ("foo", "bar")
  }


  it should "delete nodes" in new Env {
    zk.create("node", Some("first value"))
    zk.delete("node")

    zk.exists("node") should be (false)
  }


  it should "delete nodes recursively" in new Env {
    zk.create("node", Some("first value"), persistent = true)
    zk.create("node/child", Some("child value"), persistent = true)
    zk.delete("node", recursive = true)

    zk.exists("node") should be (false)
    zk.exists("node/child") should be (false)
  }


  it should "test if node is ephemeral" in new Env {
    zk.create("persistent-node", persistent = true)
    zk.create("ephemeral-node", persistent = false)

    zk.isEphemeral("persistent-node") should be (false)
    zk.isEphemeral("ephemeral-node") should be (true)
  }


  it should "set one-time watches on nodes" in new Env {
    import zooowner.Zooowner.Reaction

    var created = false
    var changed = false
    var deleted = false
    var childCreated = false

    val reaction: Zooowner.Reaction[ZKEvent] = {
      case NodeCreated("some-node", Some("value")) =>
        created = true
      case NodeChanged("some-node", Some("new-value")) =>
        changed = true
      case NodeDeleted("some-node") =>
        deleted = true
      case NodeChildrenChanged("some-node", Seq("child")) =>
        childCreated = true
    }

    zk.watch("some-node", persistent = false)(reaction)
    zk.create("some-node", Some("value"), persistent = true)
    eventually { created should be (true) }

    zk.watch("some-node", persistent = false)(reaction)
    zk.create("some-node/child", Some("value"))
    eventually { childCreated should be (true) }
    // cleanup
    zk.delete("some-node/child")

    zk.watch("some-node", persistent = false)(reaction)
    zk.set("some-node", "new-value")
    eventually { changed should be (true) }

    zk.watch("some-node", persistent = false)(reaction)
    zk.delete("some-node", recursive = true)
    eventually { deleted should be (true) }
  }


  it should "set persistent watches on nodes" in new Env {
    var created = false
    var changed = false
    var deleted = false
    var childCreated = false

    zk.watch("some-node") {
      case NodeCreated("some-node", Some("value")) =>
        created = true
      case NodeChanged("some-node", Some("new-value")) =>
        changed = true
      case NodeDeleted("some-node") =>
        deleted = true
      case NodeChildrenChanged("some-node", Seq("child")) =>
        childCreated = true
    }

    zk.create("some-node", Some("value"), persistent = true)
    eventually { created should be (true) }

    zk.create("some-node/child", Some("value"))
    eventually { childCreated should be (true) }

    zk.set("some-node", "new-value")
    eventually { changed should be (true) }

    zk.delete("some-node", recursive = true)
    eventually { deleted should be (true) }
  }


  it should "return cancellable watcher" in new Env {
    var created = false
    var deleted = false

    val watcher = zk.watch("some-node") {
      case NodeCreated("some-node", Some("value")) =>
        created = true
      case NodeDeleted("some-node") =>
        deleted = true
    }

    zk.create("some-node", Some("value"))
    eventually { created should be (true) }

    watcher.stop()

    zk.delete("some-node")
    Thread.sleep(500)
    deleted should be (false)
  }


  it should "cancell all watchers" in new Env {
    var createdA = false
    var deletedA = false
    var createdB = false
    var deletedB = false

    val watcherA = zk.watch("some-node") {
      case NodeCreated("some-node", Some("value")) =>
        createdA = true
      case NodeDeleted("some-node") =>
        deletedA = true
    }

    val watcherB = zk.watch("other-node") {
      case NodeCreated("other-node", Some("value")) =>
        createdB = true
      case NodeDeleted("other-node") =>
        deletedB = true
    }

    zk.create("some-node", Some("value"))
    eventually { createdA should be (true) }

    zk.create("other-node", Some("value"))
    eventually { createdB should be (true) }

    zk.removeAllWatchers()

    zk.delete("some-node")
    Thread.sleep(500)
    deletedA should be (false)

    zk.delete("other-node")
    Thread.sleep(500)
    deletedB should be (false)
  }


  it should "fail and pass Expired event to registered callback " +
            "on session expiration" in new Env
  {
    var hookRan = false

    zk.watchConnection {
      case Expired => hookRan = true
    }

    zkMock.expireSession()

    intercept[SessionExpiredException] {
      zk.create("foo", Some("value"))
    }

    hookRan should be (true)
  }

}


// vim: set ts=2 sw=2 et:
