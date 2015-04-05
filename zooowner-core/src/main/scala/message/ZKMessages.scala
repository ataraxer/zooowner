package zooowner
package message

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code


sealed trait ZKMessage

sealed trait ZKRequest extends ZKMessage
sealed trait ZKResponse extends ZKMessage

sealed trait ZKSuccess extends ZKResponse
sealed trait ZKFailure extends ZKResponse

sealed trait ZKEvent extends ZKResponse

sealed trait ZKConnectionEvent extends ZKEvent

case object Connected extends ZKConnectionEvent
case object Disconnected extends ZKConnectionEvent
case object Expired extends ZKConnectionEvent

case object ReadOnly extends ZKFailure
case object BadVersion extends ZKFailure

case class NodeMeta(path: String, meta: ZKNodeMeta) extends ZKSuccess
case class Node(node: ZKNode) extends ZKSuccess
case class NodeChildren(path: String, children: List[String]) extends ZKSuccess

case class NodeCreated(path: String, data: Option[ZKNode]) extends ZKEvent
case class NodeChanged(path: String, data: Option[ZKNode]) extends ZKEvent
case class NodeChildrenChanged(path: String, children: Seq[String]) extends ZKEvent
case class NodeDeleted(path: String) extends ZKEvent

case class NoNode(path: String) extends ZKFailure
case class NotEmpty(path: String) extends ZKFailure
case class NodeExists(path: String) extends ZKFailure
case class NodeIsEphemeral(path: String) extends ZKFailure
case class Error(code: Code) extends ZKFailure

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
  extends ZKRequest

case class SetNodeValue(
    path: String,
    data: String,
    version: Int = AnyVersion)
  extends ZKRequest

case class GetNodeValue(path: String) extends ZKRequest
case class GetNodeChildren(path: String) extends ZKRequest
case class WatchNode(path: String, persistent: Boolean = true) extends ZKRequest


// vim: set ts=2 sw=2 et:
