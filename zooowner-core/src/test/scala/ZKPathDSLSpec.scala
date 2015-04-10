package zooowner

import zooowner.ZKPathDSL._


class ZKPathDSLSpec extends UnitSpec {
  "ZKPath" should "parse string as path" in {
    val path = ZKPath("/foo/bar")

    path.parent should be (ZKPath("/foo"))
    path.child should be ("bar")
  }


  it should "construct path from components" in {
    val path = ZKPath("foo", "bar", "baz")
    path should be (ZKPath("/foo/bar/baz"))
  }


  it should "deconstruct valid paths into components" in {
    val ZKPath(components) = ZKPath("foo", "bar")
    components should be (Seq("foo", "bar"))
  }


  it should "deconstruct valid path strings into components" in {
    val ZKPath(components) = "/foo/bar"
    components should be (Seq("foo", "bar"))
  }


  it should "append children to path" in {
    val path = ZKPath("/foo/bar")
    val child = "baz"
    val subpath = path / child

    subpath.parent should be (path)
    subpath.child should be (child)
  }


  it should "extend path with subpath" in {
    val path = ZKPath("/foo/bar")
    val subpath = ZKPath("/sub/path")

    (path / subpath) should be (ZKPath("/foo/bar/sub/path"))
  }


  it should "validate path" in {
    def isInvalid(path: String) = {
      intercept[InvalidPathException] { ZKPath(path) }
    }

    val invalidPaths = List(
      "",
      "//",
      "/foo/",
      "/foo//bar",
      "/zookeeper",
      "/foo/zookeeper",
      "/foo/.",
      "/foo/..")

    invalidPaths.foreach(isInvalid)
  }


  it should "vaildate components on creation" in {
    def isInvalid(code: => Unit) = {
      intercept[IllegalArgumentException] { code }
    }

    isInvalid { ZKPath("foo", "zookeeper") }
    isInvalid { ZKPath("foo", ".") }
    isInvalid { ZKPath("foo", "..") }
    isInvalid { ZKPath("foo", "/") }
    isInvalid { ZKPath("foo", "//") }
    isInvalid { ZKPath("foo", "/foo") }
    isInvalid { ZKPath("foo", "foo/") }
    isInvalid { ZKPath("foo", "foo/bar") }
  }


  it should "vaildate children component" in {
    def isInvalid(code: => Unit) = {
      intercept[IllegalArgumentException] { code }
    }

    val path = ZKPath("/foo/bar")
    isInvalid { path / "zookeeper" }
    isInvalid { path / "." }
    isInvalid { path / ".." }
    isInvalid { path / "/" }
    isInvalid { path / "//" }
    isInvalid { path / "/foo" }
    isInvalid { path / "foo/" }
    isInvalid { path / "foo/bar" }
  }


  it should "detect root node" in {
    val root = ZKPath("/")

    // `isRoot` method
    root should be ('root)
    // Constant `Root`
    root should be (ZKPath.Root)
    // `Root` alias in `ZKPathDSL`
    root should be ($)
  }


  it should "create correct path for root children" in {
    val path = ZKPath.Root / "foo"
    path.asString should not be ("//foo")
    path.asString should be ("/foo")
  }


  "ZKPathDSL" should "be pretty damn awesome" in {
    val path = $ / "foo" / "bar"
    val $ /foo/bar = path
    foo should be ("foo")
    bar should be ("bar")
  }


  it should "consturct path from root" in {
    val path = $ / "foo" / "bar" / "baz"
    path should be (ZKPath("/foo/bar/baz"))
  }


  it should "match path suffixes" in {
    val path = $ / "foo" / "bar" / "baz"
    val parent/child = path

    parent should be ($ / "foo" / "bar")
    child should be ("baz")
  }


  it should "match complete path via suffixes" in {
    val path = $ / "foo" / "bar" / "baz"
    val $ /"foo"/bar/baz = path

    bar should be ("bar")
    baz should be ("baz")
  }


  it should "match path prefixes" in {
    val path = $ / "foo" / "bar" / "baz"
    val foo/:rest = path

    foo should be ("foo")
    rest should be (ZKPath("/bar/baz"))
  }


  it should "match complete path via prefixes" in {
    val path = $ / "foo" / "bar" / "baz"
    val foo/:bar/:"baz"/: $ = path

    foo should be ("foo")
    bar should be ("bar")
  }


  it should "match valid path strings" in {
    val $ /foo/bar = "/foo/bar"

    foo should be ("foo")
    bar should be ("bar")
  }
}


// vim: set ts=2 sw=2 et:
