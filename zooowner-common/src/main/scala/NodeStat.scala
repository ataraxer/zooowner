package com.ataraxer.zooowner.common

import org.apache.zookeeper.data.Stat
import scala.language.implicitConversions


object NodeStat {
  implicit def convertStat(stat: Stat): NodeStat = {
    val ephemeralOwner = stat.getEphemeralOwner
    val isEphemeral = (ephemeralOwner != 0)

    NodeStat(
      creationTime = stat.getCtime,
      modificationTime = stat.getMtime,
      size = stat.getDataLength,
      version = stat.getVersion,
      childrenVersion = stat.getCversion,
      childrenNumber = stat.getNumChildren,
      ephemeral = isEphemeral,
      session = ephemeralOwner)
  }
}


case class NodeStat(
  creationTime: Long,
  modificationTime: Long,
  size: Long,
  version: Int,
  childrenVersion: Int, // children updates number
  childrenNumber: Int,
  ephemeral: Boolean,
  session: Long)


// vim: set ts=2 sw=2 et:
