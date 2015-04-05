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


  def parent(path: String) = {
    val clean = path.stripPrefix("/").stripSuffix("/")
    if (clean.isEmpty) "/" else "/" + clean.split("/").init.mkString("/")
  }


  def resolvePath(path: String) = {
    if (path startsWith "/") path else Root/path
  }


  def encode[T](data: T)(implicit encoder: ZKEncoder[T]) = {
    encoder.encode(data)
  }
}


// vim: set ts=2 sw=2 et sts=2:
