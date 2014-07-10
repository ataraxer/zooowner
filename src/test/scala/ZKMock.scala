package com.ataraxer.zooowner

import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.data.Stat

import org.scalatest.Suite

import org.mockito.Mockito._

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

    val client = {
      val zk = mock(classOf[ZooKeeper])
      when(zk.getState).thenReturn(States.CONNECTED)
      zk
    }
  }
}


// vim: set ts=2 sw=2 et:
