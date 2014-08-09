package com.ataraxer.zooowner.common

import org.apache.zookeeper.ZooDefs.Ids


object Constants {
  type ZKData = Option[Array[Byte]]
  val AnyVersion = -1
  val AnyACL = Ids.OPEN_ACL_UNSAFE
}


// vim: set ts=2 sw=2 et:
