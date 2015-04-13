package zooowner

import scala.language.postfixOps


object DefaultSerializers extends DefaultSerializers


trait DefaultSerializers {
  private val Encoding = "UTF-8"


  implicit val rawEncoder = {
    ZKEncoder[RawZKData] { data => data }
  }


  implicit val rawDecoder = {
    ZKDecoder[RawZKData] { data => data }
  }


  implicit val stringEncoder = {
    ZKEncoder[String] { _.getBytes(Encoding) }
  }


  implicit val stringDecoder = {
    ZKDecoder[String] { data => new String(data, Encoding) }
  }


  implicit def optionEncoder[T](implicit valueEncoder: ZKEncoder[T]) = {
    ZKEncoder[Option[T]] { data =>
      data.map(valueEncoder.encode).orNull
    }
  }


  implicit def optionDecoder[T](implicit valueDecoder: ZKDecoder[T]) = {
    ZKDecoder[Option[T]] { data =>
      Option(data).map(valueDecoder.decode)
    }
  }
}


sealed trait ZKSerializer


trait ZKDecoder[+T] extends ZKSerializer {
  def decode(data: RawZKData): T
}


trait ZKEncoder[-T] extends ZKSerializer {
  def encode(value: T): RawZKData
}


object ZKDecoder {
  def apply[T](decoder: RawZKData => T) = {
    new ZKDecoder[T] { def decode(data: RawZKData) = decoder(data) }
  }
}



object ZKEncoder {
  def apply[T](encoder: T => RawZKData) = {
    new ZKEncoder[T] { def encode(data: T) = encoder(data) }
  }
}


// vim: set ts=2 sw=2 et:
