package com.ataraxer.zooowner

import com.ataraxer.test.UnitSpec

import org.apache.curator.test.TestingServer

import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._


object ZooownerSpec {
  val port = 9181
  val zkAddress = "localhost:%d".format(port)
}


class ZooownerSpec extends UnitSpec with Eventually {
  import ZooownerSpec._

  implicit val eventuallyConfig =
    PatienceConfig(timeout = 10.seconds)

  var zkServer: TestingServer = null
  var zk: Zooowner = null


  before {
    zkServer = new TestingServer(port)
    zk = new Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }
  }


  after {
    zkServer.stop()
    zkServer = null
    zk.close()
    zk = null
  }


  "Zooowner" should "connect to ZooKeeper" in {
    val zk = new Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.close()
  }


  it should "be initialized with simple path prefix " +
            "without slashes" in
  {
    lazy val zkOne = new Zooowner(zkAddress, 15.seconds, "/prefix")
    an [IllegalArgumentException] should be thrownBy zkOne

    lazy val zkTwo = new Zooowner(zkAddress, 15.seconds, "prefix/")
    an [IllegalArgumentException] should be thrownBy zkTwo

    lazy val zkThree = new Zooowner(zkAddress, 15.seconds, "prefix/sub-prefix")
    an [IllegalArgumentException] should be thrownBy zkThree
  }


  it should "create root node on connection" in {
    val zk = new Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.exists("/prefix") should be (true)

    zk.close()
  }


  it should "accept connection hook, that will be run on connection" in {
    var hookRan = false

    val zk = new Zooowner(zkAddress, 15.seconds, "prefix")
    zk.onConnection { () => hookRan = true }

    eventually { hookRan should be (true) }
    zk.close()
  }


  it should "run connection hook if connection already established" in {
    var hookRan = false

    val zk = new Zooowner(zkAddress, 15.seconds, "prefix")
    eventually { zk.isConnected should be (true) }

    zk.onConnection { () => hookRan = true }
    eventually { hookRan should be (true) }

    zk.close()
  }


  it should "create node with paths" in {
    zk.create("node/with/long/path", Some("value"), recursive = true)

    zk.get("node/with/long/path") should be (Some("value"))
  }


  it should "return Some(value) if node exists" in {
    zk.create("node", Some("value"))

    zk.get("node") should be (Some("value"))
  }


  it should "return None if node doesn't exist" in {
    zk.get("non-existant-node") should be (None)
  }


  it should "change values of created nodes" in {
    zk.create("node", Some("first value"))

    zk.get("node") should be (Some("first value"))

    zk.set("node", "second value")

    zk.get("node") should be (Some("second value"))
  }


  it should "delete nodes" in {
    zk.create("node", Some("first value"))
    zk.delete("node")

    zk.exists("node") should be (false)
  }


  it should "delete nodes recursively" in {
    zk.create("node", Some("first value"), persistent = true)
    zk.create("node/child", Some("child value"), persistent = true)
    zk.delete("node", recursive = true)

    zk.exists("node") should be (false)
    zk.exists("node/child") should be (false)
  }


  it should "test if node is ephemeral" in {
    zk.create("persistent-node", persistent = true)
    zk.create("ephemeral-node", persistent = false)

    zk.isEphemeral("persistent-node") should be (false)
    zk.isEphemeral("ephemeral-node") should be (true)
  }


  it should "set one-time watches on nodes" in {
    import com.ataraxer.zooowner.Zooowner.Reaction
    import com.ataraxer.zooowner.event._

    var created = false
    var changed = false
    var deleted = false
    var childCreated = false

    val reaction: Zooowner.Reaction[Event] = {
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

    zk.watch("some-node", persistent = false)(reaction)
    zk.set("some-node", "new-value")
    eventually { changed should be (true) }

    zk.watch("some-node", persistent = false)(reaction)
    zk.delete("some-node", recursive = true)
    eventually { deleted should be (true) }
  }


  it should "set persistent watches on nodes" in {
    import com.ataraxer.zooowner.event._

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


  it should "return cancellable watcher" in {
    import com.ataraxer.zooowner.event._

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
    Thread.sleep(1000)
    deleted should be (false)
  }

}


// vim: set ts=2 sw=2 et:
