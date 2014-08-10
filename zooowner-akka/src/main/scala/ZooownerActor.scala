package com.ataraxer.zooowner

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
  pathPrefix: String)
    extends Actor
{
  import Zooowner.SlashSeparatedPath

  implicit val futureTimeout = Timeout(5.seconds)
  implicit val ec = context.dispatcher

  val zk = new Zooowner(server, timeout, pathPrefix) with Async

  /**
   * Generates a partial function which will pass messages to specified actor.
   */
  def passTo(client: ActorRef): Zooowner.Reaction[Response] = {
    case message => client ! message
  }


  def receive = {
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
    case Create(path, data, persistent, sequential, recursive, filler) => {
      zk.async.create(path, data, persistent, sequential, recursive, filler) {
        passTo(sender)
      }
    }

    /*
     * Deletes node.
     *
     * @param path Path of node to be deleted.
     */
    case Delete(path, version) => {
      zk.async.delete(path, version = version) { passTo(sender) }
    }

    /*
     * Sets a new value for the node.
     *
     * @param path Path of the node to be updated.
     * @param data New value of the node.
     */
    case Set(path, data, version) => {
      zk.async.set(path, data, version) { passTo(sender) }
    }

    /*
     * Requests current value of the node.
     *
     * @param path Path of the node which value is requested.
     */
    case Get(path) => {
      zk.async.get(path) { passTo(sender) }
    }

    /*
     * Request children list of the node.
     *
     * @param path Path of the node which children are requested.
     */
    case GetChildren(path) => {
      zk.async.children(path) { passTo(sender) }
    }


    /*
     * Request list of children paths of the node.
     * @param path Path of the node which children paths are requested.
     */
    case GetChildrenPaths(path) => {
      val futureChildren = self ? GetChildren(path)

      val futureChildrenPaths = futureChildren map {
        case NodeChildren(_, children) =>
          NodeChildrenPaths(path, children.map( path/_ ))
      }

      futureChildrenPaths pipeTo sender
    }


    /*
     * Sets up a watcher on a node.
     *
     * @param path Path of the node to be watched.
     * @param persistent Whether watch should be persistent.
     */
    case Watch(path, persistent) => {
      zk.watch(path, persistent) { passTo(sender) }
    }
  }
}


// vim: set ts=2 sw=2 et:
