package zooowner

import scala.language.implicitConversions


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
