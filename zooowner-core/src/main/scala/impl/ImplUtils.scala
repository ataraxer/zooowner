package zooowner
package impl

import org.apache.zookeeper.CreateMode._


private[zooowner] object ImplUtils extends ZKPathDSL with StatConverter {
  val Root = ""

  def createMode(persistent: Boolean, sequential: Boolean) = {
    (persistent, sequential) match {
      case (true, true)   => PERSISTENT_SEQUENTIAL
      case (true, false)  => PERSISTENT
      case (false, true)  => EPHEMERAL_SEQUENTIAL
      case (false, false) => EPHEMERAL
    }
  }

  def cleanPath(path: String) = path.stripPrefix("/").stripSuffix("/")
  def pathComponents(path: String) = ("/"  + cleanPath(path)).split("/")

  def child(path: String) = pathComponents(path).lastOption getOrElse ""

  def parent(path: String) = {
    val components = pathComponents(path)
    if (components.isEmpty) Root else components.init.mkString("/")
  }

  def resolvePath(path: String) = {
    if (path startsWith "/") path else Root/path
  }
}


// vim: set ts=2 sw=2 et sts=2:
