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

case object Connected extends ZKConnectionEvent with ZKSuccess
case object Disconnected extends ZKConnectionEvent with ZKFailure
case object Expired extends ZKConnectionEvent with ZKFailure

case object ReadOnly extends ZKFailure
case object BadVersion extends ZKFailure

case class NodeMeta(path: String, meta: ZKNodeMeta) extends ZKSuccess
case class Node(path: String, node: ZKNode) extends ZKSuccess
case class NodeChildren(path: String, children: Seq[String]) extends ZKSuccess

case class NodeCreated(path: String, node: Option[ZKNode]) extends ZKEvent
case class NodeChanged(path: String, data: Option[ZKNode]) extends ZKEvent
case class NodeChildrenChanged(path: String, children: Seq[String]) extends ZKEvent
case class NodeDeleted(path: String) extends ZKEvent

case class NoNode(path: String) extends ZKFailure
case class NotEmpty(path: String) extends ZKFailure
case class NodeExists(path: String) extends ZKFailure
case class NodeIsEphemeral(path: String) extends ZKFailure
case class Error(code: Code) extends ZKFailure


object CreateNode {
  def apply[T: ZKEncoder](
    path: String,
    value: T = NoData,
    persistent: Boolean = false,
    sequential: Boolean = false): CreateNode =
  {
    val data = encode(value).orNull
    new CreateNode(path, data, persistent, sequential)
  }
}

case class CreateNode(
    path: String,
    data: RawZKData,
    persistent: Boolean,
    sequential: Boolean)
  extends ZKMessage


object SetNodeValue {
  def apply[T: ZKEncoder](
    path: String,
    value: T,
    version: Int = AnyVersion): SetNodeValue =
  {
    val data = encode(value).orNull
    new SetNodeValue(path, data, version)
  }
}

case class SetNodeValue(
    path: String,
    data: RawZKData,
    version: Int)
  extends ZKRequest


case class DeleteNode(
    path: String,
    version: Int = AnyVersion)
  extends ZKRequest

case class GetNodeValue(path: String) extends ZKRequest
case class GetNodeChildren(path: String) extends ZKRequest
case class WatchNode(path: String, persistent: Boolean = true) extends ZKRequest


// vim: set ts=2 sw=2 et:
