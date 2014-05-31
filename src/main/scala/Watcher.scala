package com.ataraxer.zooowner

import org.apache.zookeeper.{Watcher => ZKWatcher, WatchedEvent}
import org.apache.zookeeper.Watcher.{Event => ZKEvent}
import org.apache.zookeeper.Watcher.Event.{KeeperState, EventType}

import com.ataraxer.zooowner.Zooowner.{Reaction, default}


sealed abstract class Watcher[T]
    extends ZKWatcher
{
  def process(event: WatchedEvent) = {
    if (active) {
      (reaction orElse default[T]) {
        extract(event)
      }
    }
  }

  def reaction: Reaction[T]
  def extract(event: WatchedEvent): T

  private var active = true

  def stop(): Unit = {
    active = false
  }
}


abstract class StateWatcher extends Watcher[KeeperState] {
  def extract(event: WatchedEvent) = event.getState
}

abstract class EventWatcher extends Watcher[EventType] {
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
