package com.ataraxer.zooowner

import org.apache.zookeeper.{ZooKeeper, Watcher => ZKWatcher}
import org.apache.zookeeper.ZooKeeper.States
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
  def cleanPath(path: String) = path.stripPrefix("/").stripSuffix("/")
  def pathComponents(path: String) = cleanPath(path).split("/")
  def nodeParent(path: String) = "/" + pathComponents(path).init.mkString("/")
  def nodeName(path: String) = pathComponents(path).last

  val persistentModes = List(PERSISTENT_SEQUENTIAL, PERSISTENT)
}


trait ZKMock {
  import ZKMock._
  import Zooowner._

  def answer[T](code: InvocationOnMock => T) = {
    new Answer[T] {
      override def answer(invocation: InvocationOnMock): T =
        code(invocation)
    }
  }


  object zkMock {
    private val children =
      mutable.Map.empty[String, Set[String]].withDefaultValue(Set())

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
    def anyCreateMode = any(classOf[CreateMode])
    def anyACL = matches(AnyACL)


    val createAnswer = answer { ctx =>
      val Array(rawPath, rawData, _, rawCreateMode) = ctx.getArguments
      val path = rawPath.asInstanceOf[String]
      val data = rawData.asInstanceOf[Array[Byte]]
      val createMode = rawCreateMode.asInstanceOf[CreateMode]

      val persistent = persistentModes contains createMode

      create(path, data, persistent)

      path
    }


    def setAnswer(path: String, stat: Stat) = answer { ctx =>
      val Array(_, newData, _) = ctx.getArguments

      doReturn(newData).when(client)
        .getData(matches(path), anyWatcher, anyStat)

      stat
    }


    def deleteAnswer(path: String) = answer { ctx =>
      doThrow(new NoNodeException).when(client)
        .getData(matches(path), anyWatcher, anyStat)

      doReturn(null).when(client)
        .exists(matches(path), anyWatcher)

      val parent = nodeParent(path)
      val name   = nodeName(path)
      children(parent) = children(parent) - name

      doReturn(children(parent).toList: JavaList[String]).when(client)
        .getChildren(matches(parent), anyWatcher)
    }


    val client = {
      val zk = mock(classOf[ZooKeeper])

      when(zk.getState).thenReturn(States.CONNECTED)

      when(zk.create(anyString, anyData, anyACL, anyCreateMode))
        .thenAnswer(createAnswer)

      when(zk.getData(anyString, anyWatcher, anyStat))
        .thenThrow(new NoNodeException)

      // TODO: remove as redundant
      when(zk.exists(anyString, anyWatcher))
        .thenReturn(null)

      zk
    }


    /**
     * Stubs following ZooKeeper methods to simulate created node:
     * - getData(String, Watcher, Stat)
     * - setData(String, Array[Byte])
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

      doReturn(children(parent).toList: JavaList[String]).when(client)
        .getChildren(matches(parent), anyWatcher)

      doReturn(data).when(client)
        .getData(matches(path), anyWatcher, anyStat)

      doReturn(stat).when(client)
        .exists(matches(path), anyWatcher)

      doAnswer(setAnswer(path, stat)).when(client)
        .setData(matches(path), anyData, anyInt)

      doAnswer(deleteAnswer(path)).when(client)
        .delete(matches(path), anyInt)
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
