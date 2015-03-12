package com.ataraxer

import org.apache.zookeeper.ZooDefs.Ids


package object zooowner {
  type RawZKData = Array[Byte]
  type ZKData = Option[RawZKData]

  val AnyVersion = -1
  val AnyACL = Ids.OPEN_ACL_UNSAFE
}


// vim: set ts=2 sw=2 et:
