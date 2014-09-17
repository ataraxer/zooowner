package com.ataraxer.zooowner

import com.ataraxer.zooowner.common.Constants.AnyVersion
import com.ataraxer.zooowner.common.ZKNodeMeta

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code


object message {
  sealed trait Message
  sealed trait Response
  sealed trait Event extends Response
  sealed trait ConnectionEvent extends Event

  case object Connected extends ConnectionEvent
  case object Disconnected extends ConnectionEvent
  case object Expired extends ConnectionEvent

  case object ReadOnly extends Response
  case object BadVersion extends Response

  case class NodeMeta(path: String, meta: ZKNodeMeta)
    extends Message with Response

  case class Node(path: String, data: Option[String], meta: ZKNodeMeta)
    extends Message with Event with Response

  case class NodeChildren(path: String, children: List[String])
    extends Message with Event with Response

  case class NodeCreated(path: String, data: Option[String])
    extends Message with Event with Response

  case class NodeChanged(path: String, data: Option[String])
    extends Message with Event

  case class NodeChildrenChanged(path: String, children: Seq[String])
    extends Message with Event

  case class NodeDeleted(path: String)
    extends Message with Event with Response

  case class NoNode(path: String)
    extends Message with Response

  case class NotEmpty(path: String)
    extends Message with Response

  case class NodeExists(path: String)
    extends Message with Response

  case class NodeIsEphemeral(path: String)
    extends Message with Response

  case class Error(code: Code)
    extends Message with Response

  case class CreateNode(
    path: String,
    maybeData: Option[String] = None,
    persistent: Boolean = false,
    sequential: Boolean = false,
    recursive: Boolean = false,
    filler: Option[String] = None)
      extends Message

  case class DeleteNode(
    path: String,
    recursive: Boolean = false,
    version: Int = AnyVersion)
      extends Message

  case class SetNodeValue(path: String, data: String, version: Int = AnyVersion)
      extends Message

  case class GetNodeValue(path: String)
      extends Message

  case class GetNodeChildren(path: String)
      extends Message

  case class WatchNode(path: String, persistent: Boolean = true)

  type ZKData = Array[Byte]
}


// vim: set ts=2 sw=2 et:
