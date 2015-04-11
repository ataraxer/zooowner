package zooowner
package impl

import zooowner.ZKPath._

import org.parboiled2._

import scala.util.{Failure, Success}
import scala.language.postfixOps

import java.util.regex.Pattern


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


private[zooowner] abstract class ZKStringInterpolator(context: StringContext) {
  // `s` stands for default Scala string interpolation
  def apply(args: Any*) = ZKPath(context.s(args: _*))


  def unapplySeq(path: ZKPath) = {
    val pseudoPath = ZKPath(context.parts.mkString("match"))

    if (pseudoPath.depth != path.depth) None else {
      val quotedParts = context.parts map Pattern.quote
      val regex = quotedParts.mkString("(.+)").r
      regex.unapplySeq(path.asString)
    }
  }
}


private[zooowner] object ZKPathUtils {
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


  def parse(input: String): ZKPath = {
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
    ZKPathUtils.validateComponent(child)
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

  val ForbiddenToken = Set("zookeeper", ".", "..")

  def Check(token: String) = rule {
    test(!ForbiddenToken.contains(token)) ~ push(token)
  }

  def Path = rule { (ComponentList | Root) ~ EOI ~> ZKPathImpl }

  def ComponentList = rule { Component + }
  def Component = rule { Slash ~ Token }

  def Token = rule { capture(TokenValue) ~> Check _ }
  def TokenValue = rule { TokenChar + }
  def TokenChar = rule { !Slash ~ Printable }

  def Slash = rule { '/' }
  def Root = rule { capture(Slash) ~> ( _ => Nil ) }
}


// vim: set ts=2 sw=2 et sts=2:
