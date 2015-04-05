import org.apache.zookeeper.ZooDefs.Ids
import org.apache.zookeeper.{KeeperException => KE}


package object zooowner {
  type ZooKeeper = org.apache.zookeeper.ZooKeeper

  type RawZKData = Array[Byte]
  type ZKData = Option[RawZKData]

  type ZKSessionId = Long
  type ZKSessionPassword = Array[Byte]

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

  private[zooowner] type Reaction[T] = PartialFunction[T, Unit]

  private[zooowner] object Reaction {
    def empty[T]: Reaction[T] = { case _ => }
  }
}


// vim: set ts=2 sw=2 et:
