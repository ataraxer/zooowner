package zooowner
package mocking

import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.data.Stat

import scala.collection.mutable

import ZKNode._


object ZKNode {
  def apply(data: ZKData, persistent: Boolean = false): ZKNode = {
    if (persistent) {
      new PersistentNode(data)
    } else {
      new EphemeralNode(data)
    }
  }


  def apply(data: String, persistent: Boolean): ZKNode = {
    apply(Some(data.getBytes), persistent)
  }


  def apply(data: String): ZKNode = apply(data, false)
  def apply(persistent: Boolean): ZKNode = apply(None, persistent)
  def apply(): ZKNode = apply(None, false)

  type ZKData = Option[Array[Byte]]
  val AnyVersion = -1
}


abstract class ZKNode(initialData: ZKData) {
  protected object State {
    var data = initialData
    var version = 0
    var children = mutable.Map.empty[String, ZKNode]
  }


  protected def checkVersion(version: Int) = {
    val checkRequired = version != AnyVersion
    if (checkRequired && version != State.version) {
      throw new BadVersionException
    }
  }


  protected def incrementVersion() = State.version += 1


  // operations with node data itself
  def data: ZKData = State.data

  def data_=(data: ZKData) = {
    set(data, version = AnyVersion)
  }

  def data_=(data: String) = {
    set(Some(data.getBytes), version = AnyVersion)
  }

  def set(data: ZKData, version: Int): Unit = {
    checkVersion(version)
    incrementVersion()
    State.data = data
  }

  def set(data: String, version: Int): Unit = {
    set(Some(data.getBytes), version)
  }

  def children: Set[String] = State.children.keys.toSet

  def child(name: String): ZKNode = {
    State.children.get(name) match {
      case Some(childNode) => childNode
      case None => throw new NoNodeException
    }
  }

  def version: Int = State.version
  // generate Stat from State
  def stat: Stat = {
    val ephemeralOwner = if (persistent) 0 else 1
    val dataLength = State.data.size
    val numChildren = State.children.size
    new Stat(
      0, // czxid
      0, // mzxid
      0, // ctime
      0, // mtime
      State.version,
      0, // cversion
      0, // aversion
      ephemeralOwner,
      dataLength,
      numChildren,
      0) // pzxid
  }

  val persistent: Boolean
  val sequential: Boolean = true

  // operations with children
  def create(
    child: String, data: ZKData,
    persistent: Boolean = true): ZKNode =
  {
    if (exists(child)) throw new NodeExistsException
    val newNode = ZKNode(data, persistent)
    State.children(child) = newNode
    newNode
  }

  def create(child: String, data: String, persistent: Boolean): ZKNode = {
    create(child, Some(data.getBytes), persistent)
  }

  def create(child: String, persistent: Boolean): ZKNode = {
    create(child, None, persistent)
  }

  def create(child: String, data: String): ZKNode = create(child, data, false)
  def create(child: String): ZKNode = create(child, None, false)


  def delete(child: String, version: Int = AnyVersion): Unit = {
    val childNode = this.child(child)
    childNode.checkVersion(version)
    if (!childNode.children.isEmpty) throw new NotEmptyException
    State.children -= child
  }

  def exists(child: String) = State.children.contains(child)
}


case class EphemeralNode(value: ZKData = None) extends ZKNode(value) {
  val persistent = false

  override def create(child: String, data: ZKData, persistent: Boolean) = {
    throw new NoChildrenForEphemeralsException
  }
}


case class PersistentNode(value: ZKData = None) extends ZKNode(value) {
  val persistent = true
}


// vim: set ts=2 sw=2 et:
