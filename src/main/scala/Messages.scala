package com.ataraxer.zooowner

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code


object message {
  sealed trait Message
  sealed trait Response
  sealed trait Event
  sealed trait ConnectionEvent extends Event

  case object Connected    extends ConnectionEvent
  case object Disconnected extends ConnectionEvent

  case object ReadOnly extends Response

  case class NodeStat(path: String, stat: Stat)
    extends Message with Response

  case class NodeData(path: String, data: Option[String])
    extends Message with Event with Response

  case class NodeChildren(path: String, children: List[String])
    extends Message with Event with Response

  case class NodeCreated(path: String, data: Option[String])
    extends Message with Event with Response

  case class NodeChanged(path: String, data: Option[String])
    extends Message with Event

  case class NodeChildrenChanged(path: String, children: Seq[String])
    extends Message with Event

  case class NodeDeleted(path: String, counter: Int)
    extends Message with Event with Response

  case class NoNode(path: String)
    extends Message with Response

  case class NotEmpty(path: String)
    extends Message with Response

  case class Error(code: Code)
    extends Message with Response

  case class Create(
    path: String,
    maybeData: Option[String] = None,
    persistent: Boolean = false,
    sequential: Boolean = false,
    recursive: Boolean = false,
    filler: Option[String] = None)

  case class Delete(path: String)

  type ZKData = Array[Byte]
}


// vim: set ts=2 sw=2 et:
