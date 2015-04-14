package zooowner

import scala.concurrent.TimeoutException


class ZKConnectionTimeoutException(message: String)
  extends TimeoutException(message)

class ZKNodeDataIsNullException(message: String)
  extends NullPointerException(message)


// vim: set ts=2 sw=2 et sts=2:
