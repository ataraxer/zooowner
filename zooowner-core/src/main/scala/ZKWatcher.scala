package zooowner

import org.apache.zookeeper.{Watcher, WatchedEvent}
import org.apache.zookeeper.Watcher.{Event => ZKEvent}
import org.apache.zookeeper.Watcher.Event.{KeeperState, EventType}


sealed trait ZKWatcher[T] extends Watcher {
  protected def reaction: Reaction[T]
  protected def extract(event: WatchedEvent): T

  private val processor = reaction orElse Reaction.empty[T]

  private var active = true

  def isActive = active
  def stop(): Unit = active = false

  def dispatch(event: T): Unit = {
    if (isActive) processor(event)
  }

  def process(event: WatchedEvent): Unit = {
    dispatch(extract(event))
  }
}


abstract class ZKStateWatcher extends ZKWatcher[KeeperState] {
  protected def extract(event: WatchedEvent) = event.getState
}


abstract class EventWatcher extends ZKWatcher[EventType] {
  protected def extract(event: WatchedEvent) = event.getType
}


object ZKStateWatcher {
  def apply(react: Reaction[KeeperState]) = {
    new ZKStateWatcher { def reaction = react }
  }
}


object EventWatcher {
  def apply(react: Reaction[EventType]) = {
    new EventWatcher { def reaction = react }
  }
}


// vim: set ts=2 sw=2 et:
