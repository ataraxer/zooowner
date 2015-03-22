package zooowner

import org.apache.zookeeper.{Watcher, WatchedEvent}
import org.apache.zookeeper.Watcher.{Event => ZKEvent}
import org.apache.zookeeper.Watcher.Event.{KeeperState, EventType}

import zooowner.Zooowner.Reaction


sealed trait ZKWatcher[T] extends Watcher {
  def reaction: Reaction[T]
  def extract(event: WatchedEvent): T


  def process(event: WatchedEvent) = {
    if (active) {
      (reaction orElse Reaction.empty[T]) {
        extract(event)
      }
    }
  }


  private var active = true

  def stop(): Unit = {
    active = false
  }
}


abstract class StateWatcher extends ZKWatcher[KeeperState] {
  def extract(event: WatchedEvent) = event.getState
}


abstract class EventWatcher extends ZKWatcher[EventType] {
  def extract(event: WatchedEvent) = event.getType
}


object StateWatcher {
  def apply(react: Reaction[KeeperState]) = {
    new StateWatcher { def reaction = react }
  }
}


object EventWatcher {
  def apply(react: Reaction[EventType]) = {
    new EventWatcher { def reaction = react }
  }
}


// vim: set ts=2 sw=2 et:
