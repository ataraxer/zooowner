# Zooowner [![Build Status](https://travis-ci.org/ataraxer/zooowner.svg?branch=master)](https://travis-ci.org/ataraxer/zooowner)

ZooKeeper client that doesn't make you cry.


## Installation

Zooowner is cross compiled for Scala 2.10 and 2.11.

Just add follwing lines to `build.sbt`:

```scala
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies += "com.ataraxer" %% "zooowner-core" % "0.3.0-SNAPSHOT"
```


## Synchronous API

Create new Zooowner client which will operate on nodes under specified preifx:

```scala
import zooowner.Zooowner
import scala.concurrent.duration._

val zk = Zooowner(
  servers = "localhost:2181",
  timeout = 5.seconds,
  prefix = Some("/prefix"))
```


### Monitor connection

Set up callbacks on connection status:

```scala
import zooowner.messages._

zk.watchConnection {
  case Connected    => println("Yay!")
  case Disconnected => println("Nay!")
}
```


### Create nodes

```scala
// Ephemeral node with no value (null)
zk.create("node")
// Ephermeral node with provided value
zk.create("node", "value")
// Persistent node
zk.create("node", persistent = true)
// Sequential node
zk.create("node", sequential = true)
// "Why not both?!"
zk.create("node", persistent = true, sequential = true)
// Recursive node creation. Last node will have provided value,
// it's predecesors -- no value (null)
zk.create("node", Some("value"), recursive = true)
// Same as previous, but predecessors will be created with filler-value
zk.create("node", "foo", filler = "bar", recursive = true)
```


### Get node value

```scala
// as a byte array
zk.get[Array[Byte]]("node")
// as a UTF-8 encoded string
zk.get[String]("node")
```


### Delete nodes

```scala
// Delete only node itself
zk.delete("node")
// Delete node and all it's children. And it's grandchildren.
// And all of his other descendants.
// You cruel bastard.
zk.delete("node", recursive = true)
```


### Get nodes children

```scala
// Get list of node's children names
zk.children("node")
// Get list of node's children paths
zk.children("node", absolutePaths = true)
```


### Watch node values and children

```scala
import zooowner.message._

zk.watch("node") {
  case NodeCreated(path, value) =>
  case NodeChanged(path, value) =>
  case NodeDeleted(path) =>
  case NodeChildrenChanged(path, children) =>
}
```

