package zooowner
package impl

import zooowner.message._
import org.apache.zookeeper.Watcher.Event.EventType
import scala.util.Try
import scala.concurrent.{Future, ExecutionContext}


private[zooowner] class DefaultNodeWatcher(
    client: Zooowner,
    path: String,
    callback: Reaction[Try[ZKEvent]])
    (implicit executor: ExecutionContext)
  extends ZKEventWatcher
{
  def self = Some(this)

  def reactOn(action: => ZKEvent) = Future(action).onComplete(callback)

  def watchData() = client.exists(path, watcher = Some(this))
  def watchChildren() = client.children(path, watcher = Some(this))


  def reaction = {
    case EventType.NodeCreated => reactOn {
      watchChildren()
      NodeCreated(path, client.get(path, watcher = self))
    }

    case EventType.NodeDataChanged => reactOn {
      watchChildren()
      NodeChanged(path, client.get(path, watcher = self))
    }

    case EventType.NodeChildrenChanged => reactOn {
      watchData()
      NodeChildrenChanged(path, client.children(path, watcher = self))
    }

    case EventType.NodeDeleted => reactOn {
      // after node deletion we still may be interested
      // in watching it, in that case -- reset watcher
      client.watch(path, watcher = this)
      NodeDeleted(path)
    }
  }
}


// vim: set ts=2 sw=2 et sts=2:
