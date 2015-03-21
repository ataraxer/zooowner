package zooowner

import scala.concurrent.TimeoutException


trait ZKException { self: Exception => }


class ZKConnectionTimeoutException(message: String)
  extends TimeoutException(message)
  with ZKException


// vim: set ts=2 sw=2 et sts=2:
