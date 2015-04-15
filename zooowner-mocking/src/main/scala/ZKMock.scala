package zooowner
package mocking

import org.apache.zookeeper.AsyncCallback
import org.apache.zookeeper.AsyncCallback._
import org.apache.zookeeper.CreateMode._
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.KeeperException._
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.Watcher.Event._
import org.apache.zookeeper.ZooKeeper.States
import org.apache.zookeeper.data.{Stat, ACL}
import org.apache.zookeeper.{CreateMode, KeeperException}
import org.apache.zookeeper.{ZooKeeper, Watcher => ZKWatcher}

import org.mockito.Matchers._
import org.mockito.Matchers.{eq => matches}
import org.mockito.Mockito._
import org.mockito.invocation._
import org.mockito.stubbing._

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.util.control.Exception.catching
import scala.util.Try

import java.util.{List => JavaList}


object ZKMock {

  def cleanPath(path: String) = path.stripPrefix("/").stripSuffix("/")

  def pathComponents(path: String) = {
    val clean = cleanPath(path)
    if (clean.isEmpty) Nil else clean.split("/").toList
  }

  def nodeParent(path: String) = "/" + pathComponents(path).init.mkString("/")
  def nodeName(path: String) = pathComponents(path).last


  val persistentModes = List(PERSISTENT_SEQUENTIAL, PERSISTENT)
  val sequentialModes = List(PERSISTENT_SEQUENTIAL, EPHEMERAL_SEQUENTIAL)


  def anyStatCallback = any(classOf[StatCallback])
  def anyDataCallback = any(classOf[DataCallback])
  def anyVoidCallback = any(classOf[VoidCallback])
  def anyStringCallback = any(classOf[StringCallback])
  def anyChildrenCallback = any(classOf[Children2Callback])
  def anyWatcher = any(classOf[ZKWatcher])
  def anyStat = any(classOf[Stat])
  def anyData = any(classOf[Array[Byte]])
  def anyVersion = anyInt
  def anyCreateMode = any(classOf[CreateMode])
  def anyACL = any(classOf[JavaList[ACL]])


  def answer[T](code: Array[Object] => T) = {
    new Answer[T] {
      override def answer(invocation: InvocationOnMock): T =
        code(invocation.getArguments)
    }
  }


  def catchExceptionCode[T](action: => T): (Option[T], Int) = {
    try {
      (Some(action), Code.OK.intValue)
    } catch {
      case e: KeeperException => (None, e.code.intValue)
    }
  }
}


trait ZKMock {
  import ZKMock._
  import EventType.{NodeCreated, NodeDataChanged, NodeDeleted}
  import EventType.NodeChildrenChanged

  private var nodeTree: ZKNodeTree = new ZKNodeTree()


  object zkMock {

    /** `exists(String, Watcher)` stub answer.  */
    private val existsAnswer = answer { args =>
      val Array(path: String, rawWatcher) = args
      nodeTree.exists(path, rawWatcher.asInstanceOf[ZKWatcher])
    }

    private val asyncExistsAnswer = answer {
      case Array(path: String, rawWatcher, rawCallback, context) =>
      val watcher = rawWatcher.asInstanceOf[ZKWatcher]
      val callback = rawCallback.asInstanceOf[StatCallback]
      val (result, code) = catchExceptionCode { nodeTree.exists(path, watcher) }
      callback.processResult(code, path, context, result.orNull)
    }

    /** `create(String, Array[Byte], Array[ACL], CreateMode)` stub answer.  */
    private val createAnswer = answer { args =>
      val Array(path: String, rawData, _, rawCreateMode) = args
      val data = rawData.asInstanceOf[Array[Byte]]
      val createMode = rawCreateMode.asInstanceOf[CreateMode]
      nodeTree.create(path, data, createMode)
    }

    private val asyncCreateAnswer = answer {
      case Array(path: String, rawData, _, rawCreateMode, rawCallback, context) =>
      val data = rawData.asInstanceOf[Array[Byte]]
      val createMode = rawCreateMode.asInstanceOf[CreateMode]
      val callback = rawCallback.asInstanceOf[StringCallback]
      val (result, code) = catchExceptionCode {
        nodeTree.create(path, data, createMode)
      }
      callback.processResult(code, path, context, result.orNull)
    }

    /** Generate `setData(String, Int)` stub answer.  */
    private val setAnswer = answer { args =>
      val Array(path: String, newData: Array[Byte], _) = args
      nodeTree.set(path, newData)
    }

    private val asyncSetAnswer = answer {
      case Array(path: String, rawData, _, rawCallback, context) =>
      val newData = rawData.asInstanceOf[Array[Byte]]
      val callback = rawCallback.asInstanceOf[StatCallback]
      val (_, code) = catchExceptionCode { nodeTree.set(path, newData) }
      val stat = Try(nodeTree.exists(path, null)).toOption.orNull
      callback.processResult(code, path, context, stat)
    }

    /** Generate `getData(String, Watcher, Stat)` stub answer.  */
    private val getAnswer = answer { args =>
      val Array(path: String, rawWatcher, _) = args
      val watcher = rawWatcher.asInstanceOf[ZKWatcher]
      nodeTree.get(path, watcher)
    }

    private val asyncGetAnswer = answer {
      case Array(path: String, rawWatcher, rawCallback, context) =>
      val watcher = rawWatcher.asInstanceOf[ZKWatcher]
      val callback = rawCallback.asInstanceOf[DataCallback]
      val (result, code) = catchExceptionCode { nodeTree.get(path, watcher) }
      val stat = Try(nodeTree.exists(path, null)).toOption.orNull
      callback.processResult(code, path, context, result.orNull, stat)
    }

    /** `delete(String, Int)` stub answer.  */
    private val deleteAnswer = answer { args =>
      val Array(path: String, _) = args
      nodeTree.delete(path)
    }

    private val asyncDeleteAnswer = answer {
      case Array(path: String, _, rawCallback, context) =>
      val callback = rawCallback.asInstanceOf[VoidCallback]
      val (_, code) = catchExceptionCode { nodeTree.delete(path) }
      callback.processResult(code, path, context)
    }

    /** `getChildren(String, Watcher)` stub answer.  */
    private val childrenAnswer = answer { args =>
      val Array(path: String, rawWatcher) = args
      val watcher = rawWatcher.asInstanceOf[ZKWatcher]
      nodeTree.children(path, watcher)
    }

    private val asyncChildrenAnswer = answer {
      case Array(path: String, rawWatcher, rawCallback, context) =>
      val watcher = rawWatcher.asInstanceOf[ZKWatcher]
      val callback = rawCallback.asInstanceOf[Children2Callback]
      val (result, code) = catchExceptionCode {
        nodeTree.children(path, watcher)
      }
      val stat = Try(nodeTree.exists(path, null)).toOption.orNull
      callback.processResult(code, path, context, result.getOrElse(Nil), stat)
    }


    /**
     * Internal ZK mock reference.
     */
    private var client = generate


    /**
     * Updates internal ZK mock and returns it.
     */
    def createMock(): ZooKeeper = {
      val newClient = generate
      client = newClient
      newClient
    }


    /**
     * Return new ZK mock with default stubbing.
     */
    def generate = {
      val zk = mock(classOf[ZooKeeper])

      when(zk.getState).thenReturn(States.CONNECTED)

      // Exists
      when(zk.exists(anyString, anyWatcher))
        .thenAnswer(existsAnswer)

      when(zk.exists(anyString, anyWatcher, anyStatCallback, anyObject))
        .thenAnswer(asyncExistsAnswer)

      // Create
      when(zk.create(anyString, anyData, anyACL, anyCreateMode))
        .thenAnswer(createAnswer)

      when(zk.create(anyString, anyData, anyACL, anyCreateMode,
                     anyStringCallback, anyObject))
        .thenAnswer(asyncCreateAnswer)

      // Set
      when(zk.setData(anyString, anyData, anyVersion))
        .thenAnswer(setAnswer)

      when(zk.setData(anyString, anyData, anyVersion, anyStatCallback, anyObject))
        .thenAnswer(asyncSetAnswer)

      // Get
      when(zk.getData(anyString, anyWatcher, anyStat))
        .thenAnswer(getAnswer)

      when(zk.getData(anyString, anyWatcher, anyDataCallback, anyObject))
        .thenAnswer(asyncGetAnswer)

      // Children
      when(zk.getChildren(anyString, anyWatcher))
        .thenAnswer(childrenAnswer)

      when(zk.getChildren(anyString, anyWatcher, anyChildrenCallback, anyObject))
        .thenAnswer(asyncChildrenAnswer)

      // Delete
      when(zk.delete(anyString, anyVersion))
        .thenAnswer(deleteAnswer)

      when(zk.delete(anyString, anyVersion, anyVoidCallback, anyObject))
        .thenAnswer(asyncDeleteAnswer)

      zk
    }


    /**
     * Change ZK mock status to connected.
     */
    def connect() = when(client.getState).thenReturn(States.CONNECTED)

    /**
     * Change ZK mock status to disconnected.
     */
    def disconnect() = when(client.getState).thenReturn(States.NOT_CONNECTED)

    /**
     * Simulate session expiration.
     */
    def expireSession() = nodeTree.expire()

    /**
     * Dispatch event to be passed to watchers associated with a path.
     */
    def fireEvent(path: String, event: EventType) = {
      nodeTree.fireEvent(path, event)
    }

    def fireNodeChangedEvent(path: String) = {
      fireEvent(path, EventType.NodeDataChanged)
    }

    def fireNodeDeletedEvent(path: String) = {
      fireEvent(path, EventType.NodeDeleted)
    }

    def fireChildrenChangedEvent(path: String) = {
      fireEvent(path, EventType.NodeChildrenChanged)
    }


    object check {
      /**
       * Check that ZooKeeper has been requested to create node with
       * specified path and optional data.
       *
       * @param path Path of the created node.
       * @param maybeData Optional value of the node.
       */
      def created(path: String, maybeData: Option[String] = None) = {
        val data = maybeData.map( _.getBytes("utf8") ).orNull
        verify(client).create(
          matches(path), matches(data), anyACL, anyCreateMode)
      }
    }
  }
}


// vim: set ts=2 sw=2 et:
