package zooowner
package impl

import org.apache.zookeeper.Watcher.Event.EventType
import scala.concurrent.{Promise, ExecutionContext}


private[zooowner] class OneTimeWatcher
    (connection: ZKConnection)
    (implicit executor: ExecutionContext)
  extends ZKEventWatcher
{
  private val eventPromise = Promise[EventType]()

  def futureEvent = eventPromise.future

  def reaction = { case event => eventPromise.success(event) }
}


// vim: set ts=2 sw=2 et sts=2:
