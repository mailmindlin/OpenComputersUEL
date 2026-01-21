package li.cil.oc.server.machine

import java.lang.reflect.Method

import li.cil.oc.OpenComputers
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Context
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

import scala.collection.mutable

interface CallbackCall {
  fun call(instance: Any, context: Context, args: Arguments): Array<Any>
}

internal object CallbackWrapper {
  private final val ObjectNameASM = Any.javaClass.getName.replace('.', '/')
  private final val CallbackCallDesc = Type.getMethodDescriptor(classOf[CallbackCall].getMethod("call", classOf[AnyRef], classOf[Context], classOf[Arguments]))
  private final val CallbackCallInterface = Array(CallbackCall.javaClass.getName.replace('.', '/'))
  private final val MethodIdCache = mutableMapOf<Method, String>()
  private final val CallbackWrapperCache = mutableMapOf<Method, CallbackCall>()

  fun createCallbackWrapper(method: Method): CallbackCall {
    synchronized(this) {
      CallbackWrapperCache.getOrElseUpdate(method, createWrapper(method, CallbackCallInterface, emitCallbackCall).asInstanceOf[CallbackCall])
    }
  }

  private fun createWrapper(m: Method, interfaces: Array[String], emitCode: (Method, ClassWriter) => Unit): AnyRef = {
    val className = "generated.li.cil.oc.CallWrapper_" + generateId(m)
    if (!GeneratedClassLoader.containsClass(className)) {
      val cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
      cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, className.replace('.', '/'), null, ObjectNameASM, interfaces)
      emitConstructor(cw)
      emitCode(m, cw)
      cw.visitEnd()
      GeneratedClassLoader.addClass(className, cw.toByteArray)
    }

    GeneratedClassLoader.findClass(className).newInstance().asInstanceOf[AnyRef]
  }

  private fun emitConstructor(cw: ClassWriter): Unit = {
    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, ObjectNameASM, "<init>", "()V", false)
    mv.visitInsn(Opcodes.RETURN)
    mv.visitMaxs(1, 1)
    mv.visitEnd()
  }

  private fun emitCallbackCall(m: Method, cw: ClassWriter): Unit = {
    val className = m.getDeclaringClass.getName.replace('.', '/')
    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "call", CallbackCallDesc, null, null)
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ALOAD, 1)
    mv.visitTypeInsn(Opcodes.CHECKCAST, className)
    mv.visitVarInsn(Opcodes.ALOAD, 2)
    mv.visitVarInsn(Opcodes.ALOAD, 3)
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, m.getName, Type.getMethodDescriptor(m), false)
    mv.visitInsn(Opcodes.ARETURN)
    mv.visitMaxs(3, 3)
    mv.visitEnd()
  }

  private def generateId(m: Method): String = MethodIdCache.getOrElseUpdate(m, m.getDeclaringClass.getName.replace('.', '_') + "_" + m.getName)

  private object GeneratedClassLoader: ClassLoader(OpenComputers.javaClass.classLoader) {
    private val GeneratedClasses = mutable.Map.empty[String, Class[_]]

    def containsClass(name: String) = GeneratedClasses.contains(name)

    def addClass(name: String, bytes: Array[Byte]): Unit = {
      GeneratedClasses += name -> defineClass(name, bytes, 0, bytes.length)
    }

    override def findClass(name: String): Class[_] = {
      GeneratedClasses.get(name) match {
        case Some(clazz) => clazz
        case _ => super.findClass(name)
      }
    }
  }

}
