package zooowner
package impl

import zooowner.message._

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code
import org.apache.zookeeper.AsyncCallback
import org.apache.zookeeper.AsyncCallback._

import java.util.{List => JavaList}

import scala.collection.JavaConversions._


/**
 * Callback class represents abstract callback for asynchronous ZooKeeper
 * operation.
 */
private[zooowner] sealed trait ZKCallback extends AsyncCallback {
  import Code._

  def reaction: Reaction[ZKResponse]

  protected val reactOn = reaction orElse Reaction.empty[ZKResponse]


  protected def processCode(code: Int, path: String)(response: => ZKResponse) = {
    reactOn {
      Code.get(code) match {
        case OK => response

        case NONODE => NoNode(path)
        case NODEEXISTS => NodeExists(path)
        case NOTEMPTY => NotEmpty(path)
        case NOCHILDRENFOREPHEMERALS => NodeIsEphemeral(path)

        // default context-free reactions
        case NOTREADONLY => ReadOnly
        case BADVERSION => BadVersion
        case CONNECTIONLOSS => Disconnected
        case SESSIONEXPIRED => Expired

        case unexpectedCode => Error(unexpectedCode)
      }
    }
  }
}


private[zooowner] object Callbacks {
  import ImplUtils._

  /**
   * Fires up on node creation.
   */
  private[zooowner] case class OnCreated(reaction: Reaction[ZKResponse])
    extends ZKCallback with StringCallback
  {
    def processResult(
      returnCode: Int,
      path: String,
      context: Any,
      name: String) =
    {
      processCode(code = returnCode, path = path) {
        NodeCreated(name, None)
      }
    }
  }


  /**
   * Fires up on node deletion.
   */
  private[zooowner] case class OnDeleted(reaction: Reaction[ZKResponse])
    extends ZKCallback with VoidCallback
  {
    def processResult(returnCode: Int, path: String, context: Any) = {
      processCode(code = returnCode, path = path) {
        new NodeDeleted(path)
      }
    }
  }


  /**
   * Fires up on node stat retreival.
   */
  private[zooowner] case class OnStat(reaction: Reaction[ZKResponse])
    extends ZKCallback with StatCallback
  {
    def processResult(
      returnCode: Int,
      path: String,
      context: Any,
      stat: Stat) =
    {
      processCode(code = returnCode, path = path) {
        NodeMeta(path, stat.toMeta)
      }
    }
  }


  /**
   * Fires up on node value retrieval.
   */
  private[zooowner] case class OnData(reaction: Reaction[ZKResponse])
    extends ZKCallback with DataCallback
  {
    def processResult(
      returnCode: Int,
      path: String,
      context: Any,
      data: RawZKData,
      stat: Stat): Unit =
    {
      processCode(code = returnCode, path = path) {
        // wrap in option to guard from null
        val wrappedData = Option(data)
        val node = ZKNode(path, wrappedData, stat.toMeta)
        Node(node)
      }
    }
  }


  /**
   * Fire up on node's children retreival.
   */
  private[zooowner] case class OnChildren(reaction: Reaction[ZKResponse])
    extends ZKCallback with Children2Callback
  {
    def processResult(
      returnCode: Int,
      path: String,
      context: Any,
      children: JavaList[String],
      stat: Stat) =
    {
      processCode(code = returnCode, path = path) {
        NodeChildren(path, children.toList)
      }
    }
  }
}


// vim: set ts=2 sw=2 et:
