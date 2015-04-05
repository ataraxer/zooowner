package zooowner
package actor

import zooowner.message._

import akka.actor.{Actor, ActorRef, Stash, Props}
import akka.actor.Actor.Receive
import akka.util.Timeout
import akka.pattern.{ask, pipe}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext


object ZooownerActor {
  val StashTimeout = 3.seconds
  case object StashTimedOut

  def props(server: String, timeout: FiniteDuration) = {
    Props {
      new ZooownerActor(server, timeout)
    }
  }
}


class ZooownerActor(server: String, timeout: FiniteDuration)
  extends Actor with Stash
{
  import ZooownerActor._
  import ZKPathDSL._
  import DefaultSerializers._

  implicit val futureTimeout = Timeout(5.seconds)
  implicit val ec = context.dispatcher

  val connection = ZKConnection(
    connectionString = server,
    sessionTimeout = timeout,
    // forward all connection events to current actor's
    // mailbox in order to preserve absolute order of events
    connectionWatcher = { case event => self ! event })

  val zk = AsyncZooowner(connection)

  private var stashActive = true


  /**
   * Generates a partial function which will pass messages to specified actor.
   */
  def passTo(client: ActorRef): Reaction[ZKResponse] = {
    case message => client ! message
  }


  def receive = connecting

  /**
   * Waits for connection to ZooKeeper ensamble.
   */
  def connecting: Receive = {
    case Connected => {
      unstashAll()
      context become active
    }

    case StashTimedOut => {
      unstashAll()
      stashActive = false
    }

    case other => {
      if (stashActive && active.isDefinedAt(other)) {
        stash()
      }
    }
  }

  /**
   * Implements ZooownerActor primary API.
   */
  def active: Receive = {
    case Disconnected => {
      // since events are being processed via mailbox we need to
      // make sure that connection is still down
      if (!zk.isConnected) {
        stashActive = true
        context.system.scheduler.scheduleOnce(StashTimeout, self, StashTimedOut)
        context become connecting
      }
    }

    case Expired => {
      throw new Exception("ZK Session has expired")
    }

    /*
     * Creates new node.
     *
     * @param path Path of node to be created.
     * @param maybeData Optional data that should be stored in created node.
     * @param persistent Specifies whether created node should be persistent.
     * @param sequential Specifies whether created node should be sequential.
     * @param recursive Specifies whether path to the node should be created.
     * @param filler Optional value with which path nodes should be created.
     */
    case CreateNode(path, data, persistent, sequential, recursive, filler) => {
      zk.async.create(path, data, persistent, sequential) {
        passTo(sender)
      }
    }

    /*
     * Deletes node.
     *
     * @param path Path of node to be deleted.
     * @param recursive Specifies whether to remove underlying nodes.
     * @param version Provides version to be checked against before deletion.
     */
    case DeleteNode(path, recursive, version) => {
      zk.async.delete(path, version = version) {
        passTo(sender)
      }
    }

    /*
     * Sets a new value for the node.
     *
     * @param path Path of the node to be updated.
     * @param data New value of the node.
     */
    case SetNodeValue(path, data, version) => {
      zk.async.set(path, data, version) { passTo(sender) }
    }

    /*
     * Requests current value of the node.
     *
     * @param path Path of the node which value is requested.
     */
    case GetNodeValue(path) => {
      zk.async.get(path) { passTo(sender) }
    }

    /*
     * Request children list of the node.
     *
     * @param path Path of the node which children are requested.
     */
    case GetNodeChildren(path) => {
      zk.async.children(path) { passTo(sender) }
    }

    /*
     * Sets up a watcher on a node.
     *
     * @param path Path of the node to be watched.
     * @param persistent Whether watch should be persistent.
     */
    case WatchNode(path, persistent) => {
      zk.watch(path, persistent) { passTo(sender) }
    }
  }
}


// vim: set ts=2 sw=2 et:
