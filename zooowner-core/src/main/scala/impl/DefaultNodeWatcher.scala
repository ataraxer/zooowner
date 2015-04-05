package zooowner
package impl

import zooowner.message._
import org.apache.zookeeper.Watcher.Event.EventType


private[zooowner] class DefaultNodeWatcher(
    client: Zooowner,
    path: String,
    callback: Reaction[ZKEvent],
    persistent: Boolean)
  extends EventWatcher
{
  def self: Option[EventWatcher] = {
    if (persistent) Some(this) else None
  }


  def reactOn(action: => ZKEvent) = {
    val event = try action catch {
      case _: SessionExpiredException => Expired
      case _: ConnectionLossException => Disconnected
    }

    if (event == Expired) stop()
    if (event != Disconnected) callback(event)
  }


  def watchData() = client.exists(path, watcher = Some(this))
  def watchChildren() = client.children(path, watcher = Some(this))


  def reaction = {
    case EventType.NodeCreated => reactOn {
      if (persistent) watchChildren()
      NodeCreated(path, client.getNode(path))
    }

    case EventType.NodeDataChanged => reactOn {
      if (persistent) watchChildren()
      NodeChanged(path, client.getNode(path, watcher = self))
    }

    case EventType.NodeChildrenChanged => reactOn {
      if (persistent) watchData()
      NodeChildrenChanged(path, client.children(path, watcher = self))
    }

    case EventType.NodeDeleted => reactOn {
      // after node deletion we still may be interested
      // in watching it, in that case -- reset watcher
      if (persistent) client.watch(path, watcher = this)
      NodeDeleted(path)
    }
  }
}


// vim: set ts=2 sw=2 et sts=2:
