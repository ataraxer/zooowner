package com.ataraxer.zooowner

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.AsyncCallback
import org.apache.zookeeper.AsyncCallback._

import com.ataraxer.zooowner.Zooowner.{Reaction, default}

import java.util.{List => JavaList}

import scala.collection.JavaConversions._


object Callback {
  sealed abstract class Response

  case object ReadOnly extends Response

  case class NodeStat(stat: Stat) extends Response
  case class NodeData(data: Option[String]) extends Response
  case class NodeChildren(children: List[String]) extends Response
  case class NodeCreated(path: String) extends Response
  case class NodeDeleted(path: String, counter: Int) extends Response
  case class NoNode(path: String) extends Response
  case class NotEmpty(path: String) extends Response
  case class Error(code: Code) extends Response

  type ZKData = Array[Byte]

  def serialize(data: ZKData) =
    Option(data) map { new String(_) }
}


/**
 * Callback class represents abstract callback for asynchronous ZooKeeper
 * operation.
 */
sealed abstract class Callback
  (reaction: Reaction[Callback.Response])
    extends AsyncCallback
{
  import Callback._

  protected val reactOn = reaction orElse default[Response]

  protected def processCode(codeNumber: Int)
                           (process: PartialFunction[Code, Response]) =
  {
    Code.get(codeNumber) match {
      case code if process.isDefinedAt(code) => reactOn {
        process(code)
      }

      // default context-free codes reactions
      case Code.NOTREADONLY => reactOn(ReadOnly)

      case unexpectedCode => reactOn {
        Error(unexpectedCode)
      }
    }
  }
}


/**
 * Fires up on node creation.
 */
case class OnCreated(reaction: Reaction[Callback.Response])
  extends Callback(reaction) with StringCallback
{
  import Callback._

  def processResult(returnCode: Int, path: String, context: Any,
                    name: String) =
  {
    processCode(returnCode) {
      case Code.OK => NodeCreated(name)
    }
  }
}


/**
 * Fires up on node deletion.
 */
case class OnDeleted(reaction: Reaction[Callback.Response])
    extends Callback(reaction) with VoidCallback
{
  import Callback._

  private var counter = 0

  def processResult(returnCode: Int, path: String, context: Any) = {
    counter += 1
    processCode(returnCode) {
      case Code.OK       => NodeDeleted(path, counter)
      case Code.NONODE   => NoNode(path)
      case Code.NOTEMPTY => NotEmpty(path)
    }
  }
}


/**
 * Fires up on node stat retreival.
 */
case class OnStat(reaction: Reaction[Callback.Response])
    extends Callback(reaction) with StatCallback
{
  import Callback._

  def processResult(returnCode: Int, path: String, context: Any,
                    stat: Stat) =
  {
    processCode(returnCode) {
      case Code.OK     => NodeStat(stat)
      case Code.NONODE => NoNode(path)
    }
  }
}


/**
 * Fires up on node value retrieval.
 */
case class OnData(reaction: Reaction[Callback.Response])
    extends Callback(reaction) with DataCallback
{
  import Callback._

  def processResult(returnCode: Int, path: String, context: Any,
                    data: ZKData, stat: Stat): Unit =
  {
    processCode(returnCode) {
      case Code.OK     => NodeData(serialize(data))
      case Code.NONODE => NoNode(path)
    }
  }
}


/**
 * Fire up on node's children retreival.
 */
case class OnChildren(reaction: Reaction[Callback.Response])
  extends Callback(reaction) with Children2Callback
{
  import Callback._

  def processResult(returnCode: Int, path: String, context: Any,
                    children: JavaList[String], stat: Stat) =
  {
    processCode(returnCode) {
      case Code.OK => NodeChildren(children.toList)
    }
  }
}


// vim: set ts=2 sw=2 et:
