package zooowner

import scala.language.implicitConversions


trait ZKPathDSL {
  implicit class SlashSeparatedPath(path: String) {
    def / (subpath: String) = path + "/" + subpath
  }
}


object ZKPathDSL extends ZKPathDSL


// vim: set ts=2 sw=2 et sts=2:
