package com.ataraxer.zooowner
package actor

import com.ataraxer.zooowner.{Zooowner, Async}

import com.ataraxer.zooowner.message._

import akka.actor.{Actor, ActorRef}
import akka.actor.Actor.Receive
import akka.util.Timeout
import akka.pattern.{ask, pipe}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext


class ZooownerActor(
  server: String,
  timeout: FiniteDuration,
  pathPrefix: Option[String] = None)
    extends Actor
{
  import Zooowner.SlashSeparatedPath

  implicit val futureTimeout = Timeout(5.seconds)
  implicit val ec = context.dispatcher

  val zk = new Zooowner(server, timeout, pathPrefix) with Async

  override def preStart(): Unit = {
    // forward all connection events to current actor's
    // mailbox in order to preserve absolute order of events
    zk watchConnection { case event => self ! event }
  }


  /**
   * Generates a partial function which will pass messages to specified actor.
   */
  def passTo(client: ActorRef): Zooowner.Reaction[Response] = {
    case message => client ! message
  }

  def receive = active

  /**
   * Waits for connection to ZooKeeper ensamble.
   */
  def connecting: Receive = {
    case Connected => context become active
  }

  /**
   * Implements ZooownerActor primary API.
   */
  def active: Receive = {
    case Disconnected => {
      // since events are being processed via mailbox we need to
      // make sure that connection is still down
      if (!zk.isConnected) {
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
