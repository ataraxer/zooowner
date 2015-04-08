package zooowner

import scala.language.postfixOps


trait DefaultSerializers {
  type Type
  def encoder: ZKEncoder[Type]
  def decoder: ZKDecoder[Type]
}


object DefaultSerializers {
  private val Encoding = "UTF-8"


  implicit val rawEncoder = {
    ZKEncoder[RawZKData] { data => Option(data) }
  }


  implicit val rawDecoder = {
    ZKDecoder[RawZKData] { data => data.orNull }
  }


  implicit val stringEncoder = {
    ZKEncoder[String] { string =>
      val wrappedString = Option(string)
      wrappedString.map(_.getBytes(Encoding))
    }
  }


  implicit val stringDecoder = {
    ZKDecoder[String] { data =>
      data map {
        new String(_, Encoding)
      } orNull
    }
  }


  implicit def optionEncoder[T](implicit valueEncoder: ZKEncoder[T]) = {
    ZKEncoder[Option[T]] { data =>
      data flatMap { value =>
        valueEncoder.encode(value)
      }
    }
  }


  implicit def optionDecoder[T](implicit valueDecoder: ZKDecoder[T]) = {
    ZKDecoder[Option[T]] { data =>
      data map { value =>
        valueDecoder.decode(Some(value))
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


sealed trait ZKSerializer


trait ZKDecoder[+T] extends ZKSerializer {
  def decode(data: ZKData): T
}


object ZKDecoder {
  def apply[T](decoder: ZKData => T) = {
    new ZKDecoder[T] { def decode(data: ZKData) = decoder(data) }
  }
}


trait ZKEncoder[-T] extends ZKSerializer {
  def encode(value: T): ZKData
}


object ZKEncoder {
  def apply[T](encoder: T => ZKData) = {
    new ZKEncoder[T] { def encode(data: T) = encoder(data) }
  }
}


// vim: set ts=2 sw=2 et:
