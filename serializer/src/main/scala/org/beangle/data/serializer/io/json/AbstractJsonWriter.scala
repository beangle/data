package org.beangle.data.serializer.io.json

import org.beangle.data.serializer.io.AbstractWriter
import java.{ util => ju }
import java.{ lang => jl }
import java.io.Externalizable
import org.beangle.data.serializer.SerializeException
import org.beangle.commons.collection.FastStack

object AbstractJsonWriter {
  val DROP_ROOT_MODE = 1
  val STRICT_MODE = 2
  val EXPLICIT_MODE = 4
  val IEEE_754_MODE = 8

  class Type {}
  val NULL = new Type()
  val STRING = new Type()
  val NUMBER = new Type()
  val BOOLEAN = new Type()

  class StackElement(val clazz: Class[_], var status: Int) {}

  val ROOT = 1 << 0
  val EndObject = 1 << 1
  val StartObject = 1 << 2
  val StartAttributes = 1 << 3
  val NextAttribute = 1 << 4
  val EndAttributes = 1 << 5
  val StartElements = 1 << 6
  val NextElement = 1 << 7
  val EndElements = 1 << 8
  val SetValue = 1 << 9

  def getState(state: Int): String = {
    state match {
      case ROOT => "ROOT"
      case EndObject => "END_OBJECT"
      case StartObject => "START_OBJECT"
      case StartAttributes => "START_ATTRIBUTES"
      case NextAttribute => "NEXT_ATTRIBUTE"
      case EndAttributes => "END_ATTRIBUTES"
      case StartElements => "START_ELEMENTS"
      case NextElement => "NEXT_ELEMENT"
      case EndElements => "END_ELEMENTS"
      case SetValue => "SET_VALUE"
      case _ => throw new IllegalArgumentException("Unknown state provided: " + state + ", cannot create message for IllegalWriterStateException")
    }
  }
  class IllegalWriterStateException(from: Int, to: Int, element: String)
    extends IllegalStateException("Cannot turn from state " + getState(from) + " into state " + getState(to) + (if (element == null) "" else " for property " + element)) {
  }

  val NUMBER_TYPES: Set[Class[_]] =
    Set(classOf[jl.Byte], classOf[Byte],
      classOf[jl.Short], classOf[Short],
      classOf[jl.Integer], classOf[Int],
      classOf[jl.Long], classOf[Long],
      classOf[jl.Float], classOf[Float],
      classOf[jl.Double], classOf[Double],
      classOf[java.math.BigInteger], classOf[java.math.BigDecimal],
      classOf[scala.math.BigInt], classOf[scala.math.BigDecimal])
}

abstract class AbstractJsonWriter(val mode: Int) extends AbstractWriter {
  import AbstractJsonWriter._

  private val stack = new FastStack[StackElement](16)
  private var expectedStates: Int = StartObject
  //this.mode = (mode & EXPLICIT_MODE) > 0 ? EXPLICIT_MODE : mode
  stack.push(new StackElement(null, ROOT))

  override def startNode(name: String, clazz: Class[_]): Unit = {
    stack.push(new StackElement(clazz, stack.peek().status))
    handleCheckedStateTransition(StartObject, name, null)
    expectedStates = SetValue | NextAttribute | StartObject | NextElement | ROOT
  }

  override def addAttribute(name: String, value: String): Unit = {
    handleCheckedStateTransition(NextAttribute, name, value)
    expectedStates = SetValue | NextAttribute | StartObject | NextElement | ROOT
  }

  override def setValue(text: String): Unit = {
    val clazz = stack.peek().clazz
    if ((clazz == classOf[jl.Character] || clazz == classOf[Char]) && "" == text) {
      handleCheckedStateTransition(SetValue, null, "\u0000")
    } else {
      handleCheckedStateTransition(SetValue, null, text)
    }
    expectedStates = NextElement | ROOT
  }

  override def endNode(): Unit = {
    val size = stack.size
    val nextState = if (size > 2) NextElement else ROOT
    handleCheckedStateTransition(nextState, null, null)
    stack.pop()
    stack.peek().status = nextState
    expectedStates = StartObject
    if (size > 2) expectedStates |= NextElement | ROOT
  }

  private def handleCheckedStateTransition(requiredState: Int, elementToAdd: String, valueToAdd: String): Unit = {
    val stackElement = stack.peek()
    if ((expectedStates & requiredState) == 0) {
      throw new IllegalWriterStateException(stackElement.status, requiredState, elementToAdd)
    }
    val currentState = handleStateTransition(stackElement.status, requiredState, elementToAdd, valueToAdd)
    stackElement.status = currentState
  }

  private def handleStateTransition(currentState: Int, requiredState: Int, elementToAdd: String, valueToAdd: String): Int = {
    val size = stack.size()
    val currentType = stack.peek().clazz
    val isArray = size > 1 && isArrayType(currentType)
    val isArrayElement = size > 1 && isArrayType(stack.get(size - 2).clazz)
    var state = currentState
    state match {
      case ROOT =>
        if (requiredState == StartObject) {
          handleStateTransition(StartElements, StartObject, elementToAdd, null)
          return requiredState
        }
        throw new IllegalWriterStateException(state, requiredState, elementToAdd)

      case EndObject =>
        requiredState match {
          case StartObject =>
            state = handleStateTransition(state, NextElement, null, null)
            handleStateTransition(state, StartObject, elementToAdd, null)
          case NextElement =>
            nextElement()
          case ROOT =>
            if (((mode & DROP_ROOT_MODE) == 0 || size > 2) && (mode & EXPLICIT_MODE) == 0) endObject()
          case _ =>
            throw new IllegalWriterStateException(state, requiredState, elementToAdd)
        }
        return requiredState

      case StartObject =>
        requiredState match {
          case SetValue | StartObject =>
            if (!isArrayElement || (mode & EXPLICIT_MODE) != 0) {
              state = handleStateTransition(state, StartAttributes, null, null)
              state = handleStateTransition(state, EndAttributes, null, null)
            }
            state = StartElements
            if (requiredState == SetValue) handleStateTransition(state, SetValue, null, valueToAdd)
            else handleStateTransition(state, StartObject, elementToAdd, null)
            return requiredState

          case ROOT | NextElement =>
            if (!isArrayElement || (mode & EXPLICIT_MODE) != 0) {
              state = handleStateTransition(state, StartAttributes, null, null)
              handleStateTransition(state, EndAttributes, null, null)
            }
            state = StartElements
            state = handleStateTransition(state, SetValue, null, null)
            handleStateTransition(state, requiredState, null, null)
            return requiredState

          case StartAttributes =>
            if ((mode & EXPLICIT_MODE) != 0) startArray()
            return requiredState

          case NextAttribute =>
            if ((mode & EXPLICIT_MODE) != 0 || !isArray) {
              state = handleStateTransition(state, StartAttributes, null, null)
              handleStateTransition(state, NextAttribute, elementToAdd, valueToAdd)
              return requiredState
            } else {
              return StartObject
            }
          case _ => throw new IllegalWriterStateException(state, requiredState, elementToAdd)
        }

      case NextElement =>
        requiredState match {
          case StartObject =>
            nextElement()
            if (!isArrayElement && (mode & EXPLICIT_MODE) == 0) {
              addLabel(elementToAdd)
              if ((mode & EXPLICIT_MODE) == 0 && isArray) startArray()
            } else {
              handleStartElements(state, requiredState, elementToAdd, valueToAdd, currentType, isArray, isArrayElement, size)
            }
            return requiredState
          case ROOT =>
            state = handleStateTransition(state, EndObject, null, null)
            handleStateTransition(state, ROOT, null, null)
            return requiredState
          case NextElement | EndObject =>
            state = handleStateTransition(state, EndElements, null, null)
            handleStateTransition(state, EndObject, null, null)
            if ((mode & EXPLICIT_MODE) == 0 && !isArray) endObject()
            return requiredState
          case EndElements =>
            if ((mode & EXPLICIT_MODE) == 0 && isArray) endArray()
            return requiredState
          case _ => throw new IllegalWriterStateException(state, requiredState, elementToAdd)
        }
      case StartElements =>
        handleStartElements(state, requiredState, elementToAdd, valueToAdd, currentType, isArray, isArrayElement, size)
        return requiredState

      case EndElements =>
        requiredState match {
          case EndObject =>
            if ((mode & EXPLICIT_MODE) != 0) {
              endArray()
              endArray()
              endObject()
            }
            return requiredState
          case _ => throw new IllegalWriterStateException(state, requiredState, elementToAdd)
        }

      case NextAttribute | StartAttributes =>
        if (state == StartAttributes && requiredState == NextAttribute) {
          if (elementToAdd != null) {
            val name = (if ((mode & EXPLICIT_MODE) == 0) "@" else "") + elementToAdd
            startObject()
            addLabel(name)
            addValue(valueToAdd, STRING)
          }
          return requiredState
        }
        requiredState match {
          case EndAttributes =>
            if ((mode & EXPLICIT_MODE) != 0) {
              if (state == NextAttribute) endObject()
              endArray()
              nextElement()
              startArray()
            }
            return requiredState
          case NextAttribute =>
            if (!isArray || (mode & EXPLICIT_MODE) != 0) {
              nextElement()
              val name = (if ((mode & EXPLICIT_MODE) == 0) "@" else "") + elementToAdd
              addLabel(name)
              addValue(valueToAdd, STRING)
            }
            return requiredState
          case SetValue | StartObject =>
            state = handleStateTransition(state, EndAttributes, null, null)
            state = handleStateTransition(state, StartElements, null, null)

            requiredState match {
              case SetValue =>
                if ((mode & EXPLICIT_MODE) == 0) addLabel("_") //encodeNode("$")
                handleStateTransition(state, SetValue, null, valueToAdd)
                if ((mode & EXPLICIT_MODE) == 0) endObject()
              case StartObject =>
                handleStateTransition(state, StartObject, elementToAdd, if ((mode & EXPLICIT_MODE) == 0) "" else null)
              case EndObject =>
                state = handleStateTransition(state, SetValue, null, null)
                handleStateTransition(state, EndObject, null, null)
            }
            return requiredState
          case NextElement =>
            state = handleStateTransition(state, EndAttributes, null, null)
            handleStateTransition(state, EndObject, null, null)
            return requiredState
          case ROOT =>
            state = handleStateTransition(state, EndAttributes, null, null)
            state = handleStateTransition(state, EndObject, null, null)
            handleStateTransition(state, ROOT, null, null)
            return requiredState
          case _ => throw new IllegalWriterStateException(state, requiredState, elementToAdd)
        }

      case EndAttributes =>
        requiredState match {
          case StartElements =>
            if ((mode & EXPLICIT_MODE) == 0) nextElement()
          case EndObject =>
            state = handleStateTransition(StartElements, EndElements, null, null)
            handleStateTransition(state, EndObject, null, null)
          case _ =>
            throw new IllegalWriterStateException(state, requiredState, elementToAdd)
        }
        return requiredState

      case SetValue =>
        requiredState match {
          case EndElements =>
            if ((mode & EXPLICIT_MODE) == 0 && isArray) {
              endArray()
            }
            return requiredState
          case NextElement =>
            state = handleStateTransition(state, EndElements, null, null)
            handleStateTransition(state, EndObject, null, null)
            return requiredState
          case ROOT =>
            state = handleStateTransition(state, EndElements, null, null)
            state = handleStateTransition(state, EndObject, null, null)
            handleStateTransition(state, ROOT, null, null)
            return requiredState
          case _ => throw new IllegalWriterStateException(state, requiredState, elementToAdd)
        }
    }
    throw new IllegalWriterStateException(state, requiredState, elementToAdd)
  }

  def handleStartElements(state: Int, requiredState: Int, elementToAdd: String, valueToAdd: String, currentType: Class[_], isArray: Boolean, isArrayElement: Boolean, size: Int): Unit = {
    requiredState match {
      case StartObject =>
        if ((mode & DROP_ROOT_MODE) == 0 || size > 2) {
          if (!isArrayElement || (mode & EXPLICIT_MODE) != 0) {
            if (!"".equals(valueToAdd)) startObject()
            addLabel(elementToAdd)
          }
          if ((mode & EXPLICIT_MODE) != 0) startArray()
        }
        if ((mode & EXPLICIT_MODE) == 0) {
          if (isArray) startArray()
        }
      case SetValue =>
        if ((mode & STRICT_MODE) != 0 && size == 2) throw new SerializeException("Single value cannot be root element")
        if (valueToAdd == null) {
          if (currentType == classOf[Null]) {
            addValue("null", NULL)
          } else if ((mode & EXPLICIT_MODE) == 0 && !isArray) {
            startObject()
            endObject()
          }
        } else {
          if (((mode & IEEE_754_MODE) != 0)
            && (currentType == classOf[Long] || currentType == classOf[jl.Long])) {
            val longValue = jl.Long.parseLong(valueToAdd)
            // JavaScript supports a maximum of 2^53
            if (longValue > 9007199254740992L || longValue < -9007199254740992L) {
              addValue(valueToAdd, STRING)
            } else {
              addValue(valueToAdd, getType(currentType))
            }
          } else {
            addValue(valueToAdd, getType(currentType))
          }
        }
      case EndElements | NextElement =>
        if ((mode & EXPLICIT_MODE) == 0) {
          if (isArray) endArray()
          else endObject()
        }
      case _ =>
        throw new IllegalWriterStateException(state, requiredState, elementToAdd)
    }
  }
  /**
   * Method to return the appropriate JSON type for a Java type.
   */
  protected def getType(clazz: Class[_]): Type = {
    if (clazz == classOf[Null]) NULL
    else {
      if (clazz == classOf[Boolean] || clazz == classOf[jl.Boolean]) BOOLEAN
      else {
        if (NUMBER_TYPES.contains(clazz)) NUMBER else STRING
      }
    }
  }

  protected def isArrayType(clazz: Class[_]): Boolean = {
    return clazz != null && (clazz.isArray()
      || classOf[ju.Collection[_]].isAssignableFrom(clazz)
      || classOf[Externalizable].isAssignableFrom(clazz)
      || classOf[ju.Map[_, _]].isAssignableFrom(clazz)
      || classOf[ju.Map.Entry[_, _]].isAssignableFrom(clazz)
      || classOf[collection.Map[_, _]].isAssignableFrom(clazz)
      || classOf[collection.Iterable[_]].isAssignableFrom(clazz)
      || (classOf[Product].isAssignableFrom(clazz) && clazz.getSimpleName.startsWith("Tuple")))
  }

  protected def startObject(): Unit

  protected def addLabel(name: String): Unit

  protected def addValue(value: String, ty: Type): Unit

  protected def startArray(): Unit

  protected def nextElement(): Unit

  protected def endArray(): Unit

  protected def endObject(): Unit
}
