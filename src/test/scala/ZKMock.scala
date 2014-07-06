package com.ataraxer.zooowner

import org.apache.zookeeper.{ZooKeeper, Watcher => ZKWatcher}

import org.scalamock.scalatest.MockFactory
import org.scalatest.Suite

import scala.concurrent.duration.FiniteDuration


trait ZKMock extends MockFactory { this: Suite =>
  def zkAddress: String
  def zkTimeout: FiniteDuration
  def zkWatcher: ZKWatcher

  def zk  = {
    mock[ZooKeeper](zkAddress, zkTimeout.toMillis.toInt, zkWatcher)
  }
}


// vim: set ts=2 sw=2 et:
