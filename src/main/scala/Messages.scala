package com.ataraxer.zooowner

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code


object message {
  sealed trait Message
  sealed trait Response
  sealed trait Event

  case object ReadOnly extends Response

  case class NodeStat(stat: Stat)
    extends Message with Response

  case class NodeData(data: Option[String])
    extends Message with Event with Response

  case class NodeChildren(children: List[String])
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

  type ZKData = Array[Byte]
}


// vim: set ts=2 sw=2 et:
