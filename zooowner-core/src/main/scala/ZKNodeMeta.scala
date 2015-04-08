package zooowner


case class ZKNodeMeta(
  creationTime: Long,
  modificationTime: Long,
  size: Long,
  version: Int,
  // children updates number
  childrenVersion: Int,
  childrenCount: Int,
  ephemeral: Boolean,
  session: Long)


// vim: set ts=2 sw=2 et:
