import org.apache.zookeeper.ZooDefs.Ids
import org.apache.zookeeper.{KeeperException => KE}

import zooowner.message.ZKConnectionEvent


package object zooowner {
  type ZKClient = org.apache.zookeeper.ZooKeeper

  type RawZKData = Array[Byte]
  type ZKData = Option[RawZKData]

  type ZKSessionId = Long
  type ZKSessionPassword = Array[Byte]

  type ZKConnectionWatcher = Reaction[ZKConnectionEvent]
  type Reaction[T] = PartialFunction[T, Unit]

  val AnyVersion = -1
  val AnyACL = Ids.OPEN_ACL_UNSAFE

  /* === Exceptions === */
  type APIErrorException = KE.APIErrorException
  type AuthFailedException = KE.AuthFailedException
  type AuthRequiredException = KE.NoAuthException
  type BadArgumentsException = KE.BadArgumentsException
  type BadVersionException = KE.BadVersionException
  type ChildrenNotAllowedException = KE.NoChildrenForEphemeralsException
  type ConnectionLossException = KE.ConnectionLossException
  type DataInconsistencyException = KE.DataInconsistencyException
  type NoNodeException = KE.NoNodeException
  type NodeExistsException = KE.NodeExistsException
  type NodeNotEmptyException = KE.NotEmptyException
  type SessionExpiredException = KE.SessionExpiredException

  /* === Internal utils === */
  private[zooowner] object Reaction {
    def empty[T]: Reaction[T] = { case _ => }
  }

  private[zooowner] val NoWatcher = Reaction.empty[ZKConnectionEvent]
  private[zooowner] val NoData = Option.empty[RawZKData]

  private[zooowner] def encode[T](data: T)(implicit encoder: ZKEncoder[T]) = {
    encoder.encode(data)
  }
}


// vim: set ts=2 sw=2 et:
