package com.ataraxer.zooowner

import org.apache.zookeeper.{ZooKeeper, Watcher => ZKWatcher}
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.data.Stat

import org.scalatest.Suite

import org.mockito.Mockito._
import org.mockito.Matchers._
import org.mockito.Matchers.{eq => matchString}

import scala.concurrent.duration.FiniteDuration


trait ZKMock {
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


    val client = {
      val zk = mock(classOf[ZooKeeper])
      when(zk.getState).thenReturn(States.CONNECTED)
      when(zk.getData(anyString, anyWatcher, anyStat))
        .thenThrow(new NoNodeException)
      when(zk.exists(anyString, anyWatcher))
        .thenReturn(null)
      zk
    }
  }
}


// vim: set ts=2 sw=2 et:
