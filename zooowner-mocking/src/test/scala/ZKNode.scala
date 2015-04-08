package zooowner
package mocking

import org.apache.zookeeper.KeeperException._
import scala.language.implicitConversions


class NodeTreeSpec extends UnitSpec {
  import ZKNode._

  implicit class StringNode(node: ZKNode) {
    def dataString = node.data.map(new String(_))
  }


  "ZKNode" should "be either persistent or epehemeral" in {
    EphemeralNode("name", None) shouldBe a [ZKNode]
    PersistentNode("name", None) shouldBe a [ZKNode]
  }


  it should "have data assigned to it" in {
    ZKNode("node", None).data should be (None)
    ZKNode("node", "string").dataString should be (Some("string"))
  }


  it should "allow to change data" in {
    val node = ZKNode("original-value")
    node.data = "changed-value"
    node.dataString should be (Some("changed-value"))
  }


  it should "store node's data version, starting from 0" in {
    ZKNode("value").version should be (0)
  }


  it should "increment version on data change" in {
    val node = ZKNode("original-value")
    node.data = "changed-value"
    node.version should be (1)
  }


  it should "check that correct version is being changed" in {
    val node = ZKNode("original-value")
    node.set("changed-value", version = 0)

    intercept[BadVersionException] {
      node.set("changed-value", version = 9000)
    }
  }


  it should "allow to create children nodes under persistent once" in {
    val node = ZKNode("original-value", persistent = true)
    node.create("child", "child-value")

    node.children should contain only ("child")
    node.exists("child") should be (true)
    node.child("child").dataString should be (Some("child-value"))
  }


  it should "not allow to create children under ephemeral once" in {
    val node = ZKNode("original-value", persistent = false)

    intercept[NoChildrenForEphemeralsException] {
      node.create("child", "child-value")
    }
  }


  it should "throw exception if node to be created already exists" in {
    val node = ZKNode("original-value", persistent = true)
    node.create("child", "child-value")

    intercept[NodeExistsException] {
      node.create("child", "child-value")
    }
  }


  it should "get child by name" in {
    val node = ZKNode("original-value", persistent = true)
    val child = node.create("child", "child-value")

    node.child("child") should be (child)
  }


  it should "throw exception if requested child doesn't exists" in {
    val node = ZKNode("original-value", persistent = true)

    intercept[NoNodeException] {
      node.child("child")
    }
  }


  it should "allow to delete created children" in new {
    val node = ZKNode("original-value", persistent = true)
    node.create("child", "child-value")
    node.delete("child")

    node.children shouldBe empty
    node.exists("child") should be (false)
    intercept[NoNodeException] {
      node.child("child")
    }
  }


  it should "throw exception if node to be deleted has children" in new {
    val root = ZKNode("node", persistent = true)
    val parent = root.create("parent", persistent = true, sequential = false)
    parent.create("child")

    intercept[NotEmptyException] {
      root.delete("parent")
    }
  }

}


// vim: set ts=2 sw=2 et:
