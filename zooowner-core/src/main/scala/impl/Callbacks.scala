package zooowner
package impl

import zooowner.message._

import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.KeeperException.Code
import org.apache.zookeeper.AsyncCallback
import org.apache.zookeeper.AsyncCallback._

import java.util.{List => JavaList}

import scala.util.{Try, Success, Failure}
import scala.concurrent.Promise
import scala.collection.JavaConversions._


/**
 * Callback class represents abstract callback for asynchronous ZooKeeper
 * operation.
 */
private[zooowner] sealed trait ZKCallback extends AsyncCallback


private[zooowner] object ZKCallback {
  import ImplUtils._
  import Code._


  def processCode[T](code: Int, path: String)(result: => T): Try[T] = {
    Code.get(code) match {
      case OK => Success(result)
      case other => Failure(KeeperException.create(other, path))
    }
  }


  /**
   * Fires up on node creation.
   */
  case class OnCreated(resultPromise: Promise[ZKPath])
    extends ZKCallback with StringCallback
  {
    def processResult(
      returnCode: Int,
      path: String,
      context: Any,
      name: String) =
    {
      val result = processCode(code = returnCode, path = path)(ZKPath(name))
      resultPromise.complete(result)
    }
  }


  /**
   * Fires up on node deletion.
   */
  case class OnDeleted(resultPromise: Promise[Unit])
    extends ZKCallback with VoidCallback
  {
    def processResult(returnCode: Int, path: String, context: Any) = {
      val result = processCode(code = returnCode, path = path)({})
      resultPromise.complete(result)
    }
  }


  /**
   * Fires up on node stat retreival.
   */
  case class OnStat(resultPromise: Promise[ZKNodeMeta])
    extends ZKCallback with StatCallback
  {
    def processResult(
      returnCode: Int,
      path: String,
      context: Any,
      stat: Stat): Unit =
    {
      val result = processCode(code = returnCode, path = path)(stat.toMeta)
      resultPromise.complete(result)
    }
  }


  /**
   * Fires up on node value retrieval.
   */
  case class OnData(resultPromise: Promise[ZKNode])
    extends ZKCallback with DataCallback
  {
    def processResult(
      returnCode: Int,
      path: String,
      context: Any,
      data: RawZKData,
      stat: Stat): Unit =
    {
      val result = processCode(code = returnCode, path = path) {
        // wrap in option to guard from null
        val wrappedData = Option(data)
        val meta = Some(stat.toMeta)
        ZKNode(path, wrappedData, meta)
      }

      resultPromise.complete(result)
    }
  }


  /**
   * Fire up on node's children retreival.
   */
  case class OnChildren(resultPromise: Promise[Seq[ZKPath]])
    extends ZKCallback with Children2Callback
  {
    def processResult(
      returnCode: Int,
      path: String,
      context: Any,
      children: JavaList[String],
      stat: Stat) =
    {
      val result = processCode(code = returnCode, path = path) {
        children.toSeq map { child => ZKPath(path) / child }
      }

      resultPromise.complete(result)
    }
  }
}


// vim: set ts=2 sw=2 et:
