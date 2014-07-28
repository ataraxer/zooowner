package com.ataraxer.zooowner.mocking

import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.CreateMode._
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.Watcher.Event._
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.data.{Stat, ACL}
import org.apache.zookeeper.{ZooKeeper, Watcher => ZKWatcher}

import org.mockito.Matchers._
import org.mockito.Matchers.{eq => matches}
import org.mockito.Mockito._
import org.mockito.invocation._
import org.mockito.stubbing._

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.util.control.Exception.catching

import java.util.{List => JavaList}

import com.ataraxer.zooowner.common.ZKNode


class ZKNodeTree {
  import ZKMock._
  import EventType.{NodeCreated, NodeDataChanged, NodeDeleted}
  import EventType.NodeChildrenChanged

  val rootNode = ZKNode("", persistent = true)

  def fetchNode(path: String) = {
    val components = pathComponents(path)
    var node = rootNode
    for (child <- components) {
      node = node.child(child)
    }
    node
  }

  /**
   * In-memory storage of node data watchers.
   */
  private val dataWatchers =
    mutable.Map.empty[String, Set[ZKWatcher]].withDefaultValue(Set())

  /**
   * In-memory storage of node children watchers.
   */
  private val childrenWatchers =
    mutable.Map.empty[String, Set[ZKWatcher]].withDefaultValue(Set())


  /**
   * Set up a data watcher on a node.
   */
  def addDataWatcher(path: String, watcher: ZKWatcher) = {
    if (watcher != null) {
      dataWatchers(path) = dataWatchers(path) + watcher
    }
  }

  /**
   * Set up a children watcher on a node.
   */
  def addChildrenWatcher(path: String, watcher: ZKWatcher) = {
    if (watcher != null) {
      childrenWatchers(path) = childrenWatchers(path) + watcher
    }
  }


  /**
   * Remove a watcher from a node.
   */
  def removeDataWatcher(path: String, watcher: ZKWatcher) = {
    dataWatchers(path) = dataWatchers(path) - watcher
  }

  /**
   * Remove a watcher from a node.
   */
  def removeChildrenWatcher(path: String, watcher: ZKWatcher) = {
    childrenWatchers(path) = childrenWatchers(path) - watcher
  }


  /**
   * Fire node event, passing it to all node's watchers.
   */
  def fireEvent(path: String, event: EventType): Unit = {
    val zkEvent = new WatchedEvent(event, KeeperState.SyncConnected, path)

    if (event == NodeChildrenChanged) {
      val nodeWatchers = childrenWatchers.getOrElse(path, Set())

      nodeWatchers foreach { watcher =>
        removeChildrenWatcher(path, watcher)
        watcher.process(zkEvent)
      }
    } else {
      val nodeWatchers = dataWatchers.getOrElse(path, Set())

      nodeWatchers foreach { watcher =>
        removeDataWatcher(path, watcher)
        watcher.process(zkEvent)
      }
    }
  }


  def exists(path: String, watcher: ZKWatcher) = {
    addDataWatcher(path, watcher)

    val stat = catching(classOf[NoNodeException]).opt {
      fetchNode(path).stat
    }

    stat.orNull
  }


  def create(path: String, data: Array[Byte], createMode: CreateMode) = {
    val persistent = persistentModes contains createMode
    val parent = nodeParent(path)
    val name = nodeName(path)

    fetchNode(parent).create(name, Option(data), persistent)
    fireEvent(path, NodeCreated)

    if (childrenWatchers contains parent) {
      fireEvent(parent, NodeChildrenChanged)
    }

    path
  }


  def set(path: String, newData: Array[Byte]) = {
    val node = fetchNode(path)
    node.data = Option(newData)

    fireEvent(path, NodeDataChanged)
    node.stat
  }


  def get(path: String, watcher: ZKWatcher) = {
    addDataWatcher(path, watcher)
    fetchNode(path).data.orNull
  }


  def delete(path: String) = {
    val parent = nodeParent(path)
    val name = nodeName(path)
    fetchNode(parent).delete(name)

    if (childrenWatchers contains parent) {
      fireEvent(parent, NodeChildrenChanged)
    }

    fireEvent(path, NodeDeleted)
    dataWatchers(path) = Set()
  }


  def children(path: String, watcher: ZKWatcher) = {
    addChildrenWatcher(path, watcher)
    fetchNode(path).children.toList: JavaList[String]
  }

}


// vim: set ts=2 sw=2 et:
