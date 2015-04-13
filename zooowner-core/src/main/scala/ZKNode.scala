package zooowner


case class ZKNode(path: ZKPath, data: ZKData, meta: ZKMeta) {
  def apply[T: ZKDecoder] = extract
  def extract[T: ZKDecoder] = implicitly[ZKDecoder[T]].decode(data)
}


// vim: set ts=2 sw=2 et sts=2:
