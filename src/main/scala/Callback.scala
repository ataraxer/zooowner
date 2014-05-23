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
sealed abstract class Callback extends AsyncCallback


case class DataResponse(reaction: Reaction[Callback.Response])
    extends DataCallback
{
  import Callback._

  private val reactOn = reaction orElse default[Response]

  def processResult(returnCode: Int, path: String,
                    context: Any, data: Array[Byte],
                    stat: Stat) =
  {
    Code.get(returnCode) match {
      case Code.OK => reactOn {
        OK(Option(data) map { new String(_) })
      }

      case Code.NONODE => reactOn {
        NoNode(path)
      }

      case unexpectedCode => reactOn {
        Error(unexpectedCode)
      }
    }
  }
}


// vim: set ts=2 sw=2 et:
