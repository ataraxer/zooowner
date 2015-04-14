package zooowner

import scala.util.Try


case class ZKNode(path: ZKPath, data: ZKData, meta: ZKMeta) {
  def apply[T: ZKDecoder] = extract[T]

  def extract[T: ZKDecoder] = {
    try implicitly[ZKDecoder[T]].decode(data.orNull) catch {
      case _: NullPointerException =>
        val message = "Node data is null: " + path.asString
        throw new ZKNodeDataIsNullException(message)
    }
  }

  def tryExtract[T: ZKDecoder] = Try(extract[T])


  def isPersistent = meta.ephemeral
  def isEphemeral = !isPersistent

  def creationTime = meta.creationTime
  def modificationTime = meta.modificationTime
  def version = meta.version
  def childrenCount = meta.childrenCount
}


// vim: set ts=2 sw=2 et sts=2:
