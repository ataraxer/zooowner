package zooowner

import scala.util.Try


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
    components.foreach(impl.ZKPath.validateComponent)
    val path = components.mkString("/", "/", "")
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
  def parse(input: String) = impl.ZKPath.parse(input)

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
 * Construct paths:
 * {{{
 * // $ is an alias for path root.
 * val path = $ / "foo" / "bar"
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
 * }
 * }}}
 *
 * You can also match strings:
 * {{{
 * val $/foo/bar = "/foo/bar"
 * }}}
 */
trait ZKPathDSL {
  implicit class SlashSeparatedPath(path: String) {
    def / (subpath: String) = path + "/" + subpath
  }

  object / extends impl.PathSuffixMatcher
  object /: extends impl.PathPrefixMatcher

  val $ = ZKPath.Root

  type InvalidPathException = ZKPath.InvalidPathException
}


// vim: set ts=2 sw=2 et sts=2:
