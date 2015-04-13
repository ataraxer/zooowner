package zooowner
package actor

import zooowner.message._

import akka.actor.{Actor, ActorRef, Stash, Props}
import akka.actor.Actor.Receive
import akka.util.Timeout
import akka.pattern.{ask, pipe}

import scala.util.Failure
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext


object ZooownerActor {
  val StashTimeout = 3.seconds
  case object StashTimedOut

  def props(
    watcher: ActorRef,
    server: String,
    timeout: FiniteDuration) =
  {
    Props {
      new ZooownerActor(
        watcher,
        server,
        timeout)
    }
  }


  private type Recovery = PartialFunction[Throwable, ZKFailure]

  private def failureFor(path: ZKPath)(recover: Recovery): Recovery = {
    recover orElse {
      case _: ConnectionLossException => ConnectionLost(path, Disconnected)
      case _: SessionExpiredException => ConnectionLost(path, Expired)
      case other: ZKException => UnexpectedFailure(path, Failure(other))
    }
  }
}


class ZooownerActor(
    watcher: ActorRef,
    server: String,
    timeout: FiniteDuration)
  extends Actor
  with Stash
  with ZKPathDSL
  with DefaultSerializers
{
  import ZooownerActor._
  import context.dispatcher
  import context.system


  val connectionWatcher = ZKConnectionWatcher {
    // forward all connection events to current actor's
    // mailbox in order to preserve absolute order of events
    case event =>
      self ! event
      watcher ! event
  }


  val connection = ZKConnection(
    connectionString = server,
    sessionTimeout = timeout,
    connectionWatcher = connectionWatcher)

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
        system.scheduler.scheduleOnce(StashTimeout, self, StashTimedOut)
        context become connecting
      }
    }

    /*
     * Creates new node.
     *
     * @param path Path of node to be created.
     * @param data Data that should be stored in created node.
     * @param persistent Specifies whether created node should be persistent.
     * @param sequential Specifies whether created node should be sequential.
     */
    case CreateNode(path, data, persistent, sequential) => {
      val result = zk.async.create[RawZKData](path, data, persistent, sequential)

      result map { name =>
        NodeCreated(name, None)
      } recover failureFor(path) {
        case _: NodeExistsException => NodeExists(path)
        case _: ChildrenNotAllowedException => ChildrenNotAllowed(path)
      } pipeTo sender
    }

    /*
     * Deletes node.
     *
     * @param path Path of node to be deleted.
     * @param version Provides version to be checked against before deletion.
     */
    case DeleteNode(path, version) => {
      val result = zk.async.delete(path, version = version)

      result map { _ =>
        NodeDeleted(path)
      } recover failureFor(path) {
        case _: NoNodeException => NodeNotExists(path)
        case _: NodeNotEmptyException => NodeNotEmpty(path)
        case _: BadVersionException => BadVersion(path, version)
      } pipeTo sender
    }

    /*
     * Sets a new value for the node.
     *
     * @param path Path of the node to be updated.
     * @param data New value of the node.
     */
    case SetNodeValue(path, data, version) => {
      val result = zk.async.set(path, data, version)

      result map { meta =>
        NodeMeta(path, meta)
      } recover failureFor(path) {
        case _: NoNodeException => NodeNotExists(path)
        case _: BadVersionException => BadVersion(path, version)
      } pipeTo sender
    }

    /*
     * Requests current value of the node.
     *
     * @param path Path of the node which value is requested.
     */
    case GetNodeValue(path) => {
      val result = zk.async.get(path)

      result map { node =>
        Node(path, node)
      } recover failureFor(path) {
        case _: NoNodeException => NodeNotExists(path)
      } pipeTo sender
    }

    /*
     * Request children list of the node.
     *
     * @param path Path of the node which children are requested.
     */
    case GetNodeChildren(path) => {
      val result = zk.async.children(path)

      result map { children =>
        NodeChildren(path, children)
      } recover failureFor(path) {
        case _: NoNodeException => NodeNotExists(path)
      } pipeTo sender
    }

    /*
     * Sets up a watcher on a node.
     *
     * @param path Path of the node to be watched.
     * @param persistent Whether watch should be persistent.
     */
    case WatchNode(path, persistent) => {
      zk.watch(path) { passTo(sender) }
    }
  }
}


// vim: set ts=2 sw=2 et:
