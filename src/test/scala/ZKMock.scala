package com.ataraxer.zooowner

import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.ZooKeeper.States

import org.scalatest.Suite
import org.scalatest.mock.MockitoSugar

import org.mockito.Mockito._

import scala.concurrent.duration.FiniteDuration


trait ZKMock extends MockitoSugar { this: Suite =>
  val zkClient = {
    val zk = mock[ZooKeeper]
    when(zk.getState).thenReturn(States.CONNECTED)
    zk
  }
}


// vim: set ts=2 sw=2 et:
