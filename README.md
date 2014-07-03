# Zooowner [![Build Status](https://travis-ci.org/ataraxer/zooowner.svg?branch=master)](https://travis-ci.org/ataraxer/zooowner)

ZooKeeper client that doesn't make you cry.

## Installation

If you're using SBT, simply add following lines to your build config (usually
found in `build.sbt`):
```scala
resolvers += "Ataraxer Nexus" at "http://nexus.ataraxer.com/repo/releases"

libraryDependencies += "com.ataraxer" %% "zooowner" % "0.2.1"
```

## Synchronous API

Create new Zooowner client which will operate on nodes under specified preifx:
```(scala)
import com.ataraxer.zooowner.Zooowner

val zk = new Zooowner("localhost:2181", timeout = 30.seconds, "prefix")
```

### Monitor connection

Set up callbacks on connection status:
```(scala)
import com.ataraxer.zooowner.messages._

zk.watchConnection {
  case Connected    => println("Yay!")
  case Disconnected => println("Nay!")
}
```


### Create nodes
```(scala)
// Ephemeral node with no value (null)
zk.create("node")
// Ephermeral node with provided value
zk.create("node", Some("value"))
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
zk.create("node", Some("value"), recursive = true, filler = Some("filler"))
```


### Delete nodes
```(scala)
// Delete only node itself
zk.delete("node")
// Delete node and all it's children. And it's grandchildren.
// And all of his other descendants.
// You cruel bastard.
zk.delete("node", recursive = true)
```


### Get nodes children
```(scala)
// Get list of node's children names
zk.children("node")
// Get list of node's children paths
zk.children("node", absolutePaths = true)
```

### Watch node values and children
```(scala)
// Type declaration is just for demonstration
zk.watch("node") {
  case NodeCreated(node: String, value: Option[String]) =>
  case NodeChanged(node: String, value: Option[String]) =>
  case NodeDeleted(node: String) =>
  case NodeChildrenChanged(node: String, children: Seq[String]) =>
}
```

