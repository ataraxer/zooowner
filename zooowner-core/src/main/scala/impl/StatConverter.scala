package zooowner
package impl

import org.apache.zookeeper.data.Stat
import scala.language.implicitConversions


private[zooowner] trait StatConverter {
  implicit def statToMeta(stat: Stat): ZKMeta = {
    val ephemeralOwner = stat.getEphemeralOwner
    val isEphemeral = (ephemeralOwner != 0)

    ZKMeta(
      creationTime = stat.getCtime,
      modificationTime = stat.getMtime,
      size = stat.getDataLength,
      version = stat.getVersion,
      childrenVersion = stat.getCversion,
      childrenCount = stat.getNumChildren,
      ephemeral = isEphemeral,
      session = ephemeralOwner)
  }


  implicit class StatConverter(stat: Stat) {
    def toMeta = statToMeta(stat)
  }
}


// vim: set ts=2 sw=2 et sts=2:
