package zooowner
package impl

import zooowner.ZKPath._

import org.parboiled2._

import scala.util.{Failure, Success}
import scala.language.postfixOps


private[zooowner] trait PathSuffixMatcher {
  private type Match = Option[(ZKPath, String)]

  def unapply(input: String): Match = {
    val path = ZKPath.parse(input)
    unapply(path)
  }

  def unapply(path: ZKPath): Match = {
    if (path.isRoot) None
    else Some(path.parent -> path.child)
  }
}


private[zooowner] trait PathPrefixMatcher {
  private type Match = Option[(String, ZKPath)]

  def unapply(input: String): Match = {
    val path = ZKPath.parse(input)
    unapply(path)
  }

  def unapply(path: ZKPath): Match = {
    if (path.isRoot) None
    else Some(path.head -> path.tail)
  }
}


private[zooowner] object ZKPath {
  val readComponents = ZKPathImpl.apply _


  def isComponentValid(component: String) = {
    val noSlash = !(component contains '/')

    noSlash && {
      val parser = new ZKPathParser(component)
      parser.Token.run().isSuccess
    }
  }


  def validateComponent(component: String) = {
    require(
      isComponentValid(component),
      "Invalid path component: " + component)
  }


  def parse(input: String) = {
    val parser = new ZKPathParser(input)
    val result = parser.Path.run()

    result match {
      case Success(path) => path
      case Failure(error: ParseError) =>
        throw new InvalidPathException(parser.formatError(error))
      case Failure(other) =>
        throw new InvalidPathException(other.getMessage)
    }
  }
}


private[zooowner] case class ZKPathImpl(components: Seq[String])
  extends ZKPath
{
  def / (child: String) = {
    ZKPath.validateComponent(child)
    this.copy(components :+ child)
  }

  def / (subpath: ZKPath) = this.copy(components ++ subpath.components)

  override def toString = "ZKPath(%s)".format(asString)
  def asString = components.mkString("/", "/", "")

  def isRoot = components.isEmpty

  def child = components.last
  def parent = this.copy(components.init)

  private[zooowner] def tail = this.copy(components.tail)
  private[zooowner] def head = components.head
}


private[zooowner] class ZKPathParser(val input: ParserInput) extends Parser {
  import CharPredicate._

  def Path = rule { (ComponentList | Root) ~ EOI ~> ZKPath.readComponents }
  def ComponentList = rule { Component + }
  def Component = rule { Slash ~ Token }

  def Token = rule { !ForbiddenToken ~ capture(TokenValue) ~> ( _.toString ) }
  def TokenValue = rule { !Slash ~ Printable + }
  def ForbiddenToken = rule { "zookeeper" | "." | ".." }

  def Slash = rule { '/' }
  def Root = rule { capture(Slash) ~> ( _ => Nil ) }
}


// vim: set ts=2 sw=2 et sts=2:
