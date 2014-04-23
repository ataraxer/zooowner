package com.ataraxer.zooowner

import org.apache.zookeeper.{Watcher => ZooWatcher, WatchedEvent}
import org.apache.zookeeper.Watcher.Event.KeeperState


object Watcher {
  type Reaction = PartialFunction[KeeperState, Unit]

  val default: Reaction = { case _ => }
}


case class Watcher(reaction: Watcher.Reaction) extends ZooWatcher {
  import Watcher._

  def process(event: WatchedEvent) = {
    (reaction orElse default)(event.getState)
  }
}


// vim: set ts=2 sw=2 et:
