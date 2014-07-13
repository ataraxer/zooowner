package com.ataraxer.zooowner

import org.apache.zookeeper.{ZooKeeper, Watcher => ZKWatcher}
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.data.Stat

import org.scalatest.Suite

import org.mockito.Mockito._
import org.mockito.Matchers._
import org.mockito.Matchers.{eq => matches}
import org.mockito.stubbing._
import org.mockito.invocation._

import scala.concurrent.duration.FiniteDuration
import scala.collection.JavaConversions._

import java.util.{List => JavaList}


trait ZKMock {
  import Zooowner._

  def answer[T](code: InvocationOnMock => T) = {
    new Answer[T] {
      override def answer(invocation: InvocationOnMock): T =
        code(invocation)
    }
  }


  object zkMock {
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


    val client = {
      val zk = mock(classOf[ZooKeeper])
      when(zk.getState).thenReturn(States.CONNECTED)
      when(zk.getData(anyString, anyWatcher, anyStat))
        .thenThrow(new NoNodeException)
      when(zk.exists(anyString, anyWatcher))
        .thenReturn(null)
      zk
    }


    def create(path: String,
               maybeData: Option[String] = None,
               persistent: Boolean = false)
    {
      val data = maybeData.map( _.getBytes("utf8") ).orNull
      val stat = if (persistent) persistentStat else ephemeralStat

      doReturn(data).when(client)
        .getData(matches(path), anyWatcher, anyStat)

      doReturn(stat).when(client)
        .exists(matches(path), anyWatcher)


      val setAnswer = answer { ctx =>
        val Array(_, newData, _) = ctx.getArguments
        doReturn(newData).when(client)
          .getData(matches(path), anyWatcher, anyStat)
        stat
      }

      doAnswer(setAnswer).when(client)
        .setData(matches(path), anyData, anyInt)


      val deleteAnswer = answer { ctx =>
        doThrow(new NoNodeException).when(client)
          .getData(matches(path), anyWatcher, anyStat)

        doReturn(null).when(client)
          .exists(matches(path), anyWatcher)
      }

      doAnswer(deleteAnswer).when(client)
        .delete(matches(path), anyInt)
    }


    def createChildren(path: String, children: Map[String, Some[String]]) = {
      val childrenNames: JavaList[String] = children.keys.toList

      for ((child, data) <- children) {
        val fullPath = path + "/" + child
        create(fullPath, data)
      }

      doReturn(childrenNames).when(client)
        .getChildren(matches(path), anyWatcher)
    }


    object check {
      def created(path: String, maybeData: Option[String] = None) = {
        val data = maybeData.map( _.getBytes("utf8") ).orNull
        verify(client).create(
          matches(path), matches(data), matches(AnyACL), anyCreateMode)
      }
    }
  }
}


// vim: set ts=2 sw=2 et:
