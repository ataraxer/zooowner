package zooowner

import java.util.regex.Pattern
import scala.util.Try
import scala.language.implicitConversions


/**
 * Represents ZooKeeper path.
 *
 * {{{
 * val path = ZKPath("/foo/bar")
 * val subpath = path / "baz"
 * subpath.asString == "/foo/bar/baz"
 * }}}
 *
 * It is guaranteed, that ZooKeeper path generated via [ZKPath]
 * is going to be valid.
 */
trait ZKPath {
  /**
   * Creates new path with given subcomponent.
   */
  def / (child: String): ZKPath

  /**
   * Creates new path by appnding given subpath to current one.
   */
  def / (subpath: ZKPath): ZKPath

  /**
   * Converts path to a string representation of valid ZooKeeper path.
   */
  def asString: String

  /**
   * Tests if path points to root: `/`
   */
  def isRoot: Boolean

  /**
   * Name of a last path component.
   * Invoking this method on root path will cause exception to be thrown.
   */
  def child: String

  /**
   * Path of a parent  of this path.
   * Invoking this method on root path will cause exception to be thrown.
   */
  def parent: ZKPath

  /**
   * Ordered list of path components.
   */
  def components: Seq[String]

  /**
   * Amount of components in a path.
   * Root depth is zero.
   */
  def depth = components.size

  // Used internally for prefix pattern matching
  private[zooowner] def tail: ZKPath
  private[zooowner] def head: String
}


object ZKPath {
  type Components = Seq[String]

  /**
   * Builds new path from provided components.
   * @throws InvalidPathException
   */
  def apply(components: String*): ZKPath = {
    components.foreach(impl.ZKPathUtils.validateComponent)
    new impl.ZKPathImpl(components)
  }

  /**
   * Creates new [[ZKPath]] from string.
   * @throws InvalidPathException
   */
  def apply(input: String): ZKPath = parse(input)

  /**
   * Destructs path into components.
   */
  def unapply(path: ZKPath): Option[Components] = Some(path.components)

  /**
   * Destructs valid path string into components.
   */
  def unapply(input: String): Option[Components] = unapply(ZKPath(input))

  /**
   * Creates new [[ZKPath]] from string.
   * @throws InvalidPathException
   */
  def parse(input: String) = impl.ZKPathUtils.parse(input)

  /**
   * Thrown on any error during path parsing.
   */
  class InvalidPathException(message: String)
    extends RuntimeException("\n" + message)

  /**
   * Returns new empty path.
   */
  def empty = ZKPath()

  /**
   * Constant pointing to root path: `/`
   */
  val Root = empty
}


/**
 * Used to enable path DSL:
 * {{{
 * import zooowner.ZKPathDSL._
 * }}}
 *
 * Refer to `ZKPathDSL` trait for DSL description.
 */
object ZKPathDSL extends ZKPathDSL


/**
 * Provides utilities for constructing and pattern matching [[ZKPath]]s.
 *
 * You can either extend class where you intend to use path DSL to
 * make it available only inside that class or use global import:
 * `import zooowner.ZKPathDSL._`
 *
 * After enabling path DSL you can:
 *
 * Construct and parse paths:
 * {{{
 * // $ is an alias for root path:
 * val path = $ / "foo" / "bar"
 * val path = zk"/foo/bar"
 * }}}
 *
 * Deconstruct paths:
 * {{{
 * ZKPath("/foo/bar") match {
 *   // parent = ZKPath("/foo"), child = "bar"
 *   case parent/child =>
 *   // foo = "foo", bar = "bar"
 *   case $/foo/bar =>
 *   // foo = "foo", rest = ZKPath("/bar")
 *   case foo/:rest =>
 *   // foo = "foo", bar = "bar"
 *   case foo/:bar/:$ =>
 *   // true
 *   case zk"/foo/bar" =>
 *   // foo = "foo", bar = "bar"
 *   case zk"/\$foo/\$bar" =>
 * }
 * }}}
 *
 * You can also match strings, if they contain valid ZooKeeper paths:
 * {{{
 * val $/foo/bar = "/foo/bar"
 * }}}
 */
trait ZKPathDSL {
  object / extends impl.PathSuffixMatcher
  object /: extends impl.PathPrefixMatcher

  /**
   * An alias for [[ZKPath.Root]] for convinience in pattern matching.
   */
  val $ = ZKPath.Root

  type InvalidPathException = ZKPath.InvalidPathException

  implicit def pathToString(path: ZKPath): String = path.asString

  implicit class ZKStringContext(context: StringContext) {
    object zk extends impl.ZKStringInterpolator(context)
  }
}


// vim: set ts=2 sw=2 et sts=2:
