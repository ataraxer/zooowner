package zooowner

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code


object message {
  sealed trait ZKMessage
  sealed trait ZKResponse
  sealed trait ZKEvent extends ZKResponse
  sealed trait ConnectionEvent extends ZKEvent

  case object Connected extends ConnectionEvent
  case object Disconnected extends ConnectionEvent
  case object Expired extends ConnectionEvent

  case object ReadOnly extends ZKResponse
  case object BadVersion extends ZKResponse

  case class NodeMeta(path: String, meta: ZKNodeMeta)
    extends ZKMessage with ZKResponse

  case class Node(node: ZKNode)
    extends ZKMessage with ZKEvent with ZKResponse

  case class NodeChildren(path: String, children: List[String])
    extends ZKMessage with ZKEvent with ZKResponse

  case class NodeCreated(path: String, data: Option[ZKNode])
    extends ZKMessage with ZKEvent with ZKResponse

  case class NodeChanged(path: String, data: Option[ZKNode])
    extends ZKMessage with ZKEvent

  case class NodeChildrenChanged(path: String, children: Seq[String])
    extends ZKMessage with ZKEvent

  case class NodeDeleted(path: String)
    extends ZKMessage with ZKEvent with ZKResponse

  case class NoNode(path: String)
    extends ZKMessage with ZKResponse

  case class NotEmpty(path: String)
    extends ZKMessage with ZKResponse

  case class NodeExists(path: String)
    extends ZKMessage with ZKResponse

  case class NodeIsEphemeral(path: String)
    extends ZKMessage with ZKResponse

  case class Error(code: Code)
    extends ZKMessage with ZKResponse

  case class CreateNode(
      path: String,
      maybeData: Option[String] = None,
      persistent: Boolean = false,
      sequential: Boolean = false,
      recursive: Boolean = false,
      filler: Option[String] = None)
    extends ZKMessage

  case class DeleteNode(
      path: String,
      recursive: Boolean = false,
      version: Int = AnyVersion)
    extends ZKMessage

  case class SetNodeValue(path: String, data: String, version: Int = AnyVersion)
    extends ZKMessage

  case class GetNodeValue(path: String)
    extends ZKMessage

  case class GetNodeChildren(path: String)
    extends ZKMessage

  case class WatchNode(path: String, persistent: Boolean = true)
}


// vim: set ts=2 sw=2 et:
