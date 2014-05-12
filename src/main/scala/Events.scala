package com.ataraxer.zooowner


package event {
  sealed abstract class Event

  case class NodeCreated(path: String, data: Option[String]) extends Event
  case class NodeChanged(path: String, data: Option[String]) extends Event
  case class NodeDeleted(path: String) extends Event
  case class NodeChildrenChanged(path: String, children: Seq[String])
      extends Event
}


// vim: set ts=2 sw=2 et:
