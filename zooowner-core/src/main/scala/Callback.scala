package com.ataraxer.zooowner

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.AsyncCallback
import org.apache.zookeeper.AsyncCallback._

import com.ataraxer.zooowner.Zooowner.{Reaction, default}
import com.ataraxer.zooowner.message._
import com.ataraxer.zooowner.common.ZKNodeMeta.StatConverter

import java.util.{List => JavaList}

import scala.collection.JavaConversions._


object Callback {
  def serialize(data: ZKData) =
    Option(data) map { new String(_) }
}


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


  protected def processCode(code: Int, path: String)
                           (process: PartialFunction[Code, Response]) =
  {
    reactOn {
      Code.get(code) match {
        case codeEnum if process.isDefinedAt(codeEnum) => process(codeEnum)

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
      case Code.OK => NodeCreated(name, None)
    }
  }
}


trait Counter {
  def count: Int
}


/**
 * Fires up on node deletion.
 */
case class OnDeleted(reaction: Reaction[Response])
    extends Callback(reaction) with VoidCallback
{
  private var counter = 0

  def processResult(returnCode: Int, path: String, context: Any) = {
    counter += 1
    processCode(code = returnCode, path = path) {
      case Code.OK => new NodeDeleted(path) with Counter {
        val count = counter
      }
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
      case Code.OK => NodeMeta(path, stat.toMeta)
    }
  }
}


/**
 * Fires up on node value retrieval.
 */
case class OnData(reaction: Reaction[Response])
    extends Callback(reaction) with DataCallback
{
  import Callback._

  def processResult(returnCode: Int, path: String, context: Any,
                    data: ZKData, stat: Stat): Unit =
  {
    processCode(code = returnCode, path = path) {
      case Code.OK => Node(path, serialize(data), stat.toMeta)
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
      case Code.OK => NodeChildren(path, children.toList)
    }
  }
}


// vim: set ts=2 sw=2 et:
