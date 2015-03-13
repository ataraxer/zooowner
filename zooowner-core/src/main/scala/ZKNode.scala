package zooowner


case class ZKNode(path: String, data: ZKData, meta: ZKNodeMeta) {
  def extract[T](implicit decoder: ZKDecoder[T]) = {
    decoder.decode(data)
  }

  def get(implicit defaults: DefaultSerializers): defaults.Type = {
    defaults.decoder.decode(data)
  }
}


// vim: set ts=2 sw=2 et sts=2:
