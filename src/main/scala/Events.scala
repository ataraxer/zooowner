package com.ataraxer.zooowner

import org.apache.zookeeper.{Watcher => ZKWatcher, WatchedEvent}
import org.apache.zookeeper.Watcher.{Event => ZKEvent}
import org.apache.zookeeper.Watcher.Event.{KeeperState, EventType}


package event {
  private[zooowner] sealed abstract class Event

  case class NodeCreated(path: String, data: String) extends Event
  case class NodeChanged(path: String, data: String) extends Event
  case class NodeDeleted(path: String, data: String) extends Event
  case class NodeChildrenChanged(path: String, children: Seq[String])
      extends Event
}


// vim: set ts=2 sw=2 et:
