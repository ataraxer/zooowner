package zooowner
package impl

import org.apache.zookeeper.CreateMode._
import ZKPathDSL._


private[zooowner] object ImplUtils extends ZKPathDSL with StatConverter {
  def createMode(persistent: Boolean, sequential: Boolean) = {
    (persistent, sequential) match {
      case (true, true)   => PERSISTENT_SEQUENTIAL
      case (true, false)  => PERSISTENT
      case (false, true)  => EPHEMERAL_SEQUENTIAL
      case (false, false) => EPHEMERAL
    }
  }
}


// vim: set ts=2 sw=2 et sts=2:
