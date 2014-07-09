package com.ataraxer.zooowner

import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.data.Stat

import org.scalatest.Suite
import org.scalatest.mock.MockitoSugar

import org.mockito.Mockito._

import scala.concurrent.duration.FiniteDuration


trait ZKMock extends MockitoSugar { this: Suite =>
  object zkMock {
    val ephemeralStat = {
      val stat = mock[Stat]
      when(stat.getEphemeralOwner).thenReturn(1)
      stat
    }

    val persistentStat = {
      val stat = mock[Stat]
      when(stat.getEphemeralOwner).thenReturn(0)
      stat
    }

    val client = {
      val zk = mock[ZooKeeper]
      when(zk.getState).thenReturn(States.CONNECTED)
      zk
    }
  }
}


// vim: set ts=2 sw=2 et:
