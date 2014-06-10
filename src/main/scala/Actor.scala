package com.ataraxer.zooowner

import com.ataraxer.zooowner.message._

import akka.actor.{Actor, ActorRef}
import akka.actor.Actor.Receive

import scala.concurrent.duration._


class ZooownerActor(
  server: String,
  timeout: FiniteDuration,
  pathPrefix: String)
    extends Actor
{
  val zk = new Zooowner(server, timeout, pathPrefix) with Async

  /**
   * Generates a partial function which will pass messages to specified actor.
   */
  def passTo(client: ActorRef): Zooowner.Reaction[Response] = {
    case message => client ! message
  }


  def receive = {
    /**
     * Creates new node.
     *
     * @param path Path of node to be created.
     * @param maybeData Optional data that should be stored in created node.
     * @param persistent Specifies whether created node should be persistent.
     * @param sequential Specifies whether created node should be sequential.
     * @param recursive Specifies whether path to the node should be created.
     * @param filler Optional value with which path nodes should be created.
     */
    case Create(path, data, persistent, sequential, recursive, filler) => {
      zk.async.create(path, data, persistent, sequential, recursive, filler) {
        passTo(sender)
      }
    }

    /**
     * Deletes node.
     *
     * @param path Path of node to be deleted.
     */
    case Delete(path) => {
      zk.async.delete(path) { passTo(sender) }
    }
  }
}


// vim: set ts=2 sw=2 et:
