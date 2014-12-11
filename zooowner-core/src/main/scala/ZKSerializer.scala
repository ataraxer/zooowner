package com.ataraxer.zooowner

import scala.language.postfixOps


trait DefaultSerializers {
  type Type
  def encoder: ZKEncoder[Type]
  def decoder: ZKDecoder[Type]
}


object DefaultSerializers {
  private val Encoding = "UTF-8"


  implicit val stringEncoder = {
    new ZKEncoder[String] {
      def encode(string: String) = {
        val wrappedString = Option(string)
        wrappedString.map(_.getBytes(Encoding))
      }
    }
  }


  implicit val stringDecoder = {
    new ZKDecoder[String] {
      def decode(data: ZKData) = {
        data map {
          new String(_, Encoding)
        } orNull
      }
    }
  }


  implicit def optionEncoder[T]
    (implicit valueEncoder: ZKEncoder[T]) =
  {
    new ZKEncoder[Option[T]] {
      def encode(data: Option[T]) = {
        data flatMap { value =>
          valueEncoder.encode(value)
        }
      }
    }
  }


  implicit def optionDecoder[T]
    (implicit valueDecoder: ZKDecoder[T]) =
  {
    new ZKDecoder[Option[T]] {
      def decode(data: ZKData) = {
        data map { value =>
          valueDecoder.decode(Some(value))
        }
      }
    }
  }


  implicit val defaults = {
    new DefaultSerializers {
      type Type = String
      def encoder = stringEncoder
      def decoder = stringDecoder
    }
  }
}


trait ZKDecoder[+T] {
  def decode(data: ZKData): T
}


trait ZKEncoder[-T] {
  def encode(value: T): ZKData
}


// vim: set ts=2 sw=2 et:
