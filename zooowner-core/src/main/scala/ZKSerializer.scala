package com.ataraxer.zooowner

import scala.language.postfixOps


object DefaultSerializers {
  import ZKSerializer._

  implicit val stringSerializer = {
    new ZKSerializer[String] {
      private val Encoding = "UTF-8"

      def encode(string: String) = {
        val wrappedString = Option(string)
        wrappedString.map(_.getBytes(Encoding))
      }

      def decode(data: ZKData) = {
        data.map {
          new String(_, Encoding)
        } orNull
      }
    }
  }
}


object ZKSerializer {
  type ZKData = Option[Array[Byte]]
}


trait ZKSerializer[T] {
  import ZKSerializer._

  def encode(value: T): ZKData
  def decode(data: ZKData): T
}


// vim: set ts=2 sw=2 et: