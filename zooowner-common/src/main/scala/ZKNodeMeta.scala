package com.ataraxer.zooowner.common

import org.apache.zookeeper.data.Stat
import scala.language.implicitConversions


object ZKNodeMeta {
  implicit def statToMeta(stat: Stat): ZKNodeMeta = {
    val ephemeralOwner = stat.getEphemeralOwner
    val isEphemeral = (ephemeralOwner != 0)

    ZKNodeMeta(
      creationTime = stat.getCtime,
      modificationTime = stat.getMtime,
      size = stat.getDataLength,
      version = stat.getVersion,
      childrenVersion = stat.getCversion,
      childrenNumber = stat.getNumChildren,
      ephemeral = isEphemeral,
      session = ephemeralOwner)
  }


  implicit class StatConverter(stat: Stat) {
    def toMeta = statToMeta(stat)
  }
}


case class ZKNodeMeta(
  creationTime: Long,
  modificationTime: Long,
  size: Long,
  version: Int,
  childrenVersion: Int, // children updates number
  childrenNumber: Int,
  ephemeral: Boolean,
  session: Long)


// vim: set ts=2 sw=2 et:
