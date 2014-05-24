package com.ataraxer.zooowner

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.AsyncCallback
import org.apache.zookeeper.AsyncCallback._

import com.ataraxer.zooowner.Zooowner.{Reaction, default}


object Callback {
  sealed abstract class Response

  case class OK(result: Option[String]) extends Response
  case class NoNode(path: String) extends Response
  case class Error(code: Code) extends Response
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

      case unexpectedCode => reactOn {
        Error(unexpectedCode)
      }
    }
  }
}


case class OnData(reaction: Reaction[Callback.Response])
    extends Callback(reaction) with DataCallback
{
  import Callback._

  def processResult(returnCode: Int, path: String, context: Any,
                    data: Array[Byte], stat: Stat): Unit =
  {
    processCode(returnCode) {
      case Code.OK =>
        OK(Option(data) map { new String(_) })

      case Code.NONODE =>
        NoNode(path)
    }
  }
}


// vim: set ts=2 sw=2 et:
