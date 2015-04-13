package zooowner


case class ZKNode(path: ZKPath, data: ZKData, meta: ZKMeta) {
  def apply[T: ZKDecoder] = extract
  def extract[T: ZKDecoder] = implicitly[ZKDecoder[T]].decode(data)

  def isPersistent = meta.ephemeral
  def isEphemeral = !isPersistent

  def creationTime = meta.creationTime
  def modificationTime = meta.modificationTime
  def version = meta.version
  def childrenCount = meta.childrenCount
}


// vim: set ts=2 sw=2 et sts=2:
