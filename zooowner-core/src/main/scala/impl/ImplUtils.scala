package zooowner
package impl

import org.apache.zookeeper.CreateMode._
import ZKPathDSL._


private[zooowner] object ImplUtils extends ZKPathDSL with StatConverter {
  def createMode(ephemeral: Boolean, sequential: Boolean) = {
    (ephemeral, sequential) match {
      case (true,  true)  => EPHEMERAL_SEQUENTIAL
      case (true,  false) => EPHEMERAL
      case (false, true)  => PERSISTENT_SEQUENTIAL
      case (false, false) => PERSISTENT
    }
  }
}


// vim: set ts=2 sw=2 et sts=2:
