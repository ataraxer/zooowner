package com.ataraxer.zooowner

import org.apache.zookeeper.{ZooKeeper, Watcher => ZKWatcher}
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.Watcher.Event._
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.CreateMode._
import org.apache.zookeeper.data.Stat

import org.scalatest.Suite

import org.mockito.Mockito._
import org.mockito.Matchers._
import org.mockito.Matchers.{eq => matches}
import org.mockito.stubbing._
import org.mockito.invocation._

import scala.concurrent.duration.FiniteDuration
import scala.collection.JavaConversions._
import scala.collection.mutable

import java.util.{List => JavaList}


object ZKMock {
  import Zooowner._

  def cleanPath(path: String) = path.stripPrefix("/").stripSuffix("/")
  def pathComponents(path: String) = cleanPath(path).split("/")
  def nodeParent(path: String) = "/" + pathComponents(path).init.mkString("/")
  def nodeName(path: String) = pathComponents(path).last


  val persistentModes = List(PERSISTENT_SEQUENTIAL, PERSISTENT)


  val ephemeralStat = {
    val stat = mock(classOf[Stat])
    when(stat.getEphemeralOwner).thenReturn(1)
    stat
  }

  val persistentStat = {
    val stat = mock(classOf[Stat])
    when(stat.getEphemeralOwner).thenReturn(0)
    stat
  }


  def anyWatcher = any(classOf[ZKWatcher])
  def anyStat = any(classOf[Stat])
  def anyData = any(classOf[Array[Byte]])
  def anyVersion = anyInt
  def anyCreateMode = any(classOf[CreateMode])
  def anyACL = matches(AnyACL)


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
    private val children =
      mutable.Map.empty[String, Set[String]].withDefaultValue(Set())

    private val watchers =
      mutable.Map.empty[String, Set[ZKWatcher]].withDefaultValue(Set())

    def addWatcher(path: String, watcher: ZKWatcher) = {
      if (watcher != null) {
        watchers(path) = watchers(path) + watcher
      }
    }

    def removeWatcher(path: String, watcher: ZKWatcher) = {
      watchers(path) = watchers(path) - watcher
    }


    def fireEvent(path: String, event: EventType): Unit = {
      val zkEvent = new WatchedEvent(event, KeeperState.SyncConnected, path)

      val nodeWatchers = watchers.getOrElse(path, Set())

      nodeWatchers foreach { watcher =>
        removeWatcher(path, watcher)
        watcher.process(zkEvent)
      }
    }


    private def existsAnswer(stat: Stat) = answer { ctx =>
      val Array(path: String, rawWatcher) = ctx.getArguments
      val watcher = rawWatcher.asInstanceOf[ZKWatcher]

      addWatcher(path, watcher)

      stat
    }


    private val createAnswer = answer { ctx =>
      val Array(path: String, rawData, _, rawCreateMode) = ctx.getArguments
      val data = rawData.asInstanceOf[Array[Byte]]
      val createMode = rawCreateMode.asInstanceOf[CreateMode]

      val persistent = persistentModes contains createMode

      create(path, data, persistent)

      path
    }


    private def setAnswer(stat: Stat) = answer { ctx =>
      val Array(path: String, newData: Array[Byte], _) = ctx.getArguments
      //val path = rawPath.asInstanceOf[String]

      doAnswer(getAnswer(newData)).when(client)
        .getData(matches(path), anyWatcher, anyStat)

      fireEvent(path, NodeDataChanged)

      stat
    }


    private def getAnswer(data: Array[Byte]) = answer { ctx =>
      val Array(path: String, rawWatcher, _) = ctx.getArguments
      val watcher = rawWatcher.asInstanceOf[ZKWatcher]

      addWatcher(path, watcher)

      data
    }


    private val deleteAnswer = answer { ctx =>
      val Array(path: String, _) = ctx.getArguments

      doThrow(new NoNodeException).when(client)
        .getData(matches(path), anyWatcher, anyStat)

      doAnswer(existsAnswer(null)).when(client)
        .exists(matches(path), anyWatcher)

      val parent = nodeParent(path)
      val name   = nodeName(path)
      children(parent) = children(parent) - name

      fireEvent(path, NodeDeleted)
      watchers(path) = Set()

      doAnswer(childrenAnswer).when(client)
        .getChildren(matches(parent), anyWatcher)
    }


    private val childrenAnswer = answer { ctx =>
      val Array(path: String, rawWatcher) = ctx.getArguments
      val watcher = rawWatcher.asInstanceOf[ZKWatcher]

      addWatcher(path, watcher)

      children(path).toList: JavaList[String]
    }


    val client = {
      val zk = mock(classOf[ZooKeeper])

      when(zk.getState).thenReturn(States.CONNECTED)

      when(zk.exists(anyString, anyWatcher))
        .thenAnswer(existsAnswer(null))

      when(zk.create(anyString, anyData, anyACL, anyCreateMode))
        .thenAnswer(createAnswer)

      when(zk.setData(anyString, anyData, anyVersion))
        .thenThrow(new NoNodeException)

      when(zk.getData(anyString, anyWatcher, anyStat))
        .thenThrow(new NoNodeException)

      when(zk.delete(anyString, anyVersion))
        .thenThrow(new NoNodeException)

      zk
    }


    def connect()    = when(client.getState).thenReturn(States.CONNECTED)
    def disconnect() = when(client.getState).thenReturn(States.NOT_CONNECTED)

    def expireSession() = {
      val fail = doThrow(new SessionExpiredException)
      fail.when(client).getData(anyString, anyWatcher, anyStat)
      fail.when(client).getChildren(anyString, anyWatcher)
      fail.when(client).exists(anyString, anyWatcher)
      fail.when(client).setData(anyString, anyData, anyVersion)
      fail.when(client).delete(anyString, anyVersion)
    }


    /**
     * Stubs following ZooKeeper methods to simulate created node:
     * - getData(String, Watcher, Stat)
     * - setData(String, Array[Byte], Int)
     * - exists(String, Watcher)
     * - delete(String, Int)
     * - getChildren(String, Watcher)
     *
     * @param path Path of the created node.
     * @param data Value of the node.
     * @param persistent Specifies whether created node should be persistent.
     */
    def create(path: String,
               data: Array[Byte],
               persistent: Boolean = false)
    {
      val stat = if (persistent) persistentStat else ephemeralStat

      val parent = nodeParent(path)
      val name   = nodeName(path)
      children(parent) = children(parent) + name

      doAnswer(getAnswer(data)).when(client)
        .getData(matches(path), anyWatcher, anyStat)

      doAnswer(childrenAnswer).when(client)
        .getChildren(matches(parent), anyWatcher)

      doAnswer(existsAnswer(stat)).when(client)
        .exists(matches(path), anyWatcher)

      doAnswer(setAnswer(stat)).when(client)
        .setData(matches(path), anyData, anyVersion)

      doAnswer(deleteAnswer).when(client)
        .delete(matches(path), anyVersion)

      fireEvent(path, NodeCreated)

      if (watchers contains parent) {
        fireEvent(parent, NodeChildrenChanged)
      }
    }


    object check {
      /**
       * Checks that ZooKeeper has been requested to create node with
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
