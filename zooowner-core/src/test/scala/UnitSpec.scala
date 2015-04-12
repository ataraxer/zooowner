package zooowner

import org.scalatest._
import scala.concurrent.ExecutionContext


trait UnitSpec
  extends FlatSpecLike
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll
  with concurrent.ScalaFutures
{
  implicit val executor = ExecutionContext.Implicits.global
}



// vim: set ts=2 sw=2 et:
