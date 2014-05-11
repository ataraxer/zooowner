package com.ataraxer.zooowner

import org.apache.zookeeper.{Watcher => ZKWatcher, WatchedEvent}
import org.apache.zookeeper.Watcher.{Event => ZKEvent}
import org.apache.zookeeper.Watcher.Event.{KeeperState, EventType}


object Watcher {
  type Reaction[T] = PartialFunction[T, Unit]

  def default[T]: Reaction[T] = { case _ => }
}


sealed abstract class Watcher[T]
  (reaction: Watcher.Reaction[T])
    extends ZKWatcher
{
  import Watcher._

  def process(event: WatchedEvent) = {
    (reaction orElse default[T]) {
      extract(event)
    }
  }

  def extract(event: WatchedEvent): T
}


case class StateWatcher(reaction: Watcher.Reaction[KeeperState])
    extends Watcher[KeeperState](reaction)
{ def extract(event: WatchedEvent) = event.getState }

case class EventWatcher(reaction: Watcher.Reaction[EventType])
    extends Watcher[EventType](reaction)
{ def extract(event: WatchedEvent) = event.getType }


// vim: set ts=2 sw=2 et:
