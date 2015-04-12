package zooowner


case class ZKNode(path: ZKPath, data: ZKData, meta: Option[ZKNodeMeta]) {
  def apply[T](implicit decoder: ZKDecoder[T]) = {
    decoder.decode(data)
  }

  def extract[T](implicit decoder: ZKDecoder[T]) = {
    decoder.decode(data)
  }
}


// vim: set ts=2 sw=2 et sts=2:
