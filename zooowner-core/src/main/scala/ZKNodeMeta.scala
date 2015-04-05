package zooowner


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
