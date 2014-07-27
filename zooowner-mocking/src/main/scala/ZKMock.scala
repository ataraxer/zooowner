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


object ZKMock {

  def cleanPath(path: String) = path.stripPrefix("/").stripSuffix("/")

  def pathComponents(path: String) = {
    val clean = cleanPath(path)
    if (clean.isEmpty) Nil else clean.split("/").toList
  }

  def nodeParent(path: String) = "/" + pathComponents(path).init.mkString("/")
  def nodeName(path: String) = pathComponents(path).last


  val persistentModes = List(PERSISTENT_SEQUENTIAL, PERSISTENT)


  def anyWatcher = any(classOf[ZKWatcher])
  def anyStat = any(classOf[Stat])
  def anyData = any(classOf[Array[Byte]])
  def anyVersion = anyInt
  def anyCreateMode = any(classOf[CreateMode])
  def anyACL = any(classOf[JavaList[ACL]])


  def answer[T](code: InvocationOnMock => T) = {
    new Answer[T] {
      override def answer(invocation: InvocationOnMock): T =
        code(invocation)
    }
  }
}


trait ZKMock {
  import ZKMock._
  import EventType.{NodeCreated, NodeDataChanged, NodeDeleted}
  import EventType.NodeChildrenChanged

  object zkMock {
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


    /**
     * Generate `exists(String, Watcher)` stub answer.
     */
    private val existsAnswer = answer { ctx =>
      val Array(path: String, rawWatcher) = ctx.getArguments
      val watcher = rawWatcher.asInstanceOf[ZKWatcher]

      addDataWatcher(path, watcher)

      val stat = catching(classOf[NoNodeException]).opt {
        fetchNode(path).stat
      }
      stat.orNull
    }


    /**
     * `create(String, Array[Byte], Array[ACL], CreateMode)`
     * stub answer.
     */
    private val createAnswer = answer { ctx =>
      val Array(path: String, rawData, _, rawCreateMode) = ctx.getArguments
      val data = rawData.asInstanceOf[Array[Byte]]
      val createMode = rawCreateMode.asInstanceOf[CreateMode]
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


    /**
     * Generate `setData(String, Int)` stub answer.
     */
    private val setAnswer = answer { ctx =>
      val Array(path: String, newData: Array[Byte], _) = ctx.getArguments

      val node = fetchNode(path)
      node.data = Option(newData)

      fireEvent(path, NodeDataChanged)
      node.stat
    }


    /**
     * Generate `getData(String, Watcher, Stat)` stub answer.
     */
    private val getAnswer = answer { ctx =>
      val Array(path: String, rawWatcher, _) = ctx.getArguments
      val watcher = rawWatcher.asInstanceOf[ZKWatcher]

      addDataWatcher(path, watcher)

      fetchNode(path).data.orNull
    }


    /**
     * `delete(String, Int)` stub answer.
     */
    private val deleteAnswer = answer { ctx =>
      val Array(path: String, _) = ctx.getArguments

      val parent = nodeParent(path)
      val name = nodeName(path)
      fetchNode(parent).delete(name)

      if (childrenWatchers contains parent) {
        fireEvent(parent, NodeChildrenChanged)
      }

      fireEvent(path, NodeDeleted)
      dataWatchers(path) = Set()
    }


    /**
     * `getChildren(String, Watcher)` stub answer.
     */
    private val childrenAnswer = answer { ctx =>
      val Array(path: String, rawWatcher) = ctx.getArguments
      val watcher = rawWatcher.asInstanceOf[ZKWatcher]

      addChildrenWatcher(path, watcher)

      fetchNode(path).children.toList: JavaList[String]
    }


    /**
     * Internal ZK mock reference.
     */
    private var client = generate


    /**
     * Updates internal ZK mock and returns it.
     */
    def createMock(): ZooKeeper = {
      val newClient = generate
      client = newClient
      newClient
    }


    /**
     * Return new ZK mock with default stubbing.
     */
    def generate = {
      val zk = mock(classOf[ZooKeeper])

      when(zk.getState).thenReturn(States.CONNECTED)

      when(zk.exists(anyString, anyWatcher))
        .thenAnswer(existsAnswer)

      when(zk.create(anyString, anyData, anyACL, anyCreateMode))
        .thenAnswer(createAnswer)

      when(zk.setData(anyString, anyData, anyVersion))
        .thenAnswer(setAnswer)

      when(zk.getData(anyString, anyWatcher, anyStat))
        .thenAnswer(getAnswer)

      when(zk.getChildren(anyString, anyWatcher))
        .thenAnswer(childrenAnswer)

      when(zk.delete(anyString, anyVersion))
        .thenAnswer(deleteAnswer)

      zk
    }


    /**
     * Change ZK mock status to connected.
     */
    def connect() = when(client.getState).thenReturn(States.CONNECTED)

    /**
     * Change ZK mock status to disconnected.
     */
    def disconnect() = when(client.getState).thenReturn(States.NOT_CONNECTED)

    /**
     * Simulate session expiration.
     */
    def expireSession() = {
      val fail = doThrow(new SessionExpiredException)
      fail.when(client).exists(anyString, anyWatcher)
      fail.when(client).create(anyString, anyData, anyACL, anyCreateMode)
      fail.when(client).getData(anyString, anyWatcher, anyStat)
      fail.when(client).setData(anyString, anyData, anyVersion)
      fail.when(client).getChildren(anyString, anyWatcher)
      fail.when(client).delete(anyString, anyVersion)
    }


    object check {
      /**
       * Check that ZooKeeper has been requested to create node with
       * specified path and optional data.
       *
       * @param path Path of the created node.
       * @param maybeData Optional value of the node.
       */
      def created(path: String, maybeData: Option[String] = None) = {
        val data = maybeData.map( _.getBytes("utf8") ).orNull
        verify(client).create(
          matches(path), matches(data), anyACL, anyCreateMode)
      }
    }
  }
}


// vim: set ts=2 sw=2 et:
