package com.ataraxer.zooowner

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.AsyncCallback
import org.apache.zookeeper.AsyncCallback._

import com.ataraxer.zooowner.Zooowner.{Reaction, default}
import com.ataraxer.zooowner.message._
import com.ataraxer.zooowner.ZKNodeMeta.StatConverter

import java.util.{List => JavaList}

import scala.collection.JavaConversions._


/**
 * Callback class represents abstract callback for asynchronous ZooKeeper
 * operation.
 */
sealed abstract class Callback
  (reaction: Reaction[Response])
    extends AsyncCallback
{
  import Code._

  protected val reactOn = reaction orElse default[Response]


  protected def processCode
    (code: Int, path: String)
    (response: => Response) =
  {
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


/**
 * Fires up on node creation.
 */
case class OnCreated(reaction: Reaction[Response])
  extends Callback(reaction) with StringCallback
{
  def processResult(returnCode: Int, path: String, context: Any,
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
case class OnDeleted(reaction: Reaction[Response])
    extends Callback(reaction) with VoidCallback
{
  private var counter = 0

  def processResult(returnCode: Int, path: String, context: Any) = {
    processCode(code = returnCode, path = path) {
      new NodeDeleted(path)
    }
  }
}


/**
 * Fires up on node stat retreival.
 */
case class OnStat(reaction: Reaction[Response])
    extends Callback(reaction) with StatCallback
{
  def processResult(returnCode: Int, path: String, context: Any,
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
case class OnData(reaction: Reaction[Response])
    extends Callback(reaction) with DataCallback
{
  def processResult(returnCode: Int, path: String, context: Any,
                    data: RawZKData, stat: Stat): Unit =
  {
    processCode(code = returnCode, path = path) {
      // wrap in option to guard from null
      val wrappedData = Option(data)
      Node(path, wrappedData, stat.toMeta)
    }
  }
}


/**
 * Fire up on node's children retreival.
 */
case class OnChildren(reaction: Reaction[Response])
  extends Callback(reaction) with Children2Callback
{
  def processResult(returnCode: Int, path: String, context: Any,
                    children: JavaList[String], stat: Stat) =
  {
    processCode(code = returnCode, path = path) {
      NodeChildren(path, children.toList)
    }
  }
}


// vim: set ts=2 sw=2 et:
