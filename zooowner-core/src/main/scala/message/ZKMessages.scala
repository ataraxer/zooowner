package zooowner
package message

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code

import scala.util.Failure


sealed trait ZKMessage

sealed trait ZKRequest extends ZKMessage
sealed trait ZKResponse extends ZKMessage

sealed trait ZKSuccess extends ZKResponse
sealed trait ZKFailure extends ZKResponse

sealed trait ZKEvent extends ZKResponse

sealed trait ZKDataEvent extends ZKEvent
sealed trait ZKChildrenEvent extends ZKEvent

sealed trait ZKConnectionEvent extends ZKEvent

sealed trait ZKConnectionSuccess extends ZKConnectionEvent with ZKSuccess
sealed trait ZKConnectionFailure extends ZKConnectionEvent with ZKFailure

case object Connected extends ZKConnectionSuccess
case object Disconnected extends ZKConnectionFailure
case object Expired extends ZKConnectionFailure

case class NodeMeta(path: ZKPath, meta: Option[ZKMeta]) extends ZKSuccess
case class Node(path: ZKPath, node: ZKNode) extends ZKSuccess
case class NodeChildren(path: ZKPath, children: Seq[ZKPath]) extends ZKSuccess

case class NodeCreated(path: ZKPath, node: Option[ZKNode]) extends ZKDataEvent
case class NodeChanged(path: ZKPath, data: Option[ZKNode]) extends ZKDataEvent
case class NodeDeleted(path: ZKPath) extends ZKDataEvent

case class NodeChildrenChanged(path: ZKPath, children: Seq[ZKPath])
  extends ZKChildrenEvent

case class NodeExists(path: ZKPath) extends ZKFailure
case class NodeNotExists(path: ZKPath) extends ZKFailure
case class NodeNotEmpty(path: ZKPath) extends ZKFailure
case class ChildrenNotAllowed(path: ZKPath) extends ZKFailure
case class BadVersion(path: ZKPath, version: Int) extends ZKFailure

case class ConnectionLost(path: ZKPath, status: ZKConnectionFailure)
  extends ZKFailure

case class UnexpectedFailure(path: ZKPath, failure: Failure[ZKException])
  extends ZKFailure


object CreateNode {
  def apply[T: ZKEncoder](
    path: ZKPath,
    value: T = NoData,
    persistent: Boolean = false,
    sequential: Boolean = false): CreateNode =
  {
    val data = encode(value).orNull
    new CreateNode(path, data, persistent, sequential)
  }
}

case class CreateNode(
    path: ZKPath,
    data: RawZKData,
    persistent: Boolean,
    sequential: Boolean)
  extends ZKMessage


object SetNodeValue {
  def apply[T: ZKEncoder](
    path: ZKPath,
    value: T,
    version: Int = AnyVersion): SetNodeValue =
  {
    val data = encode(value).orNull
    new SetNodeValue(path, data, version)
  }
}

case class SetNodeValue(
    path: ZKPath,
    data: RawZKData,
    version: Int)
  extends ZKRequest


case class DeleteNode(
    path: ZKPath,
    version: Int = AnyVersion)
  extends ZKRequest

case class GetNodeValue(path: ZKPath) extends ZKRequest
case class GetNodeChildren(path: ZKPath) extends ZKRequest
case class WatchNode(path: ZKPath, persistent: Boolean = true) extends ZKRequest


// vim: set ts=2 sw=2 et:
