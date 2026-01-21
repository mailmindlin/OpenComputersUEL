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
  private final val ObjectNameASM = Any::class.java.name.replace('.', '/')
  private final val CallbackCallDesc = Type.getMethodDescriptor(CallbackCall::class.java.getMethod("call", Any::class.java, Context::class.java, Arguments::class.java))
  private final val CallbackCallInterface = arrayOf(CallbackCall::class.java.name.replace('.', '/'))
  private final val MethodIdCache = mutableMapOf<Method, String>()
  private final val CallbackWrapperCache = mutableMapOf<Method, CallbackCall>()

  fun createCallbackWrapper(method: Method): CallbackCall {
    synchronized(this) {
      CallbackWrapperCache.getOrPut(method) { createWrapper(method, CallbackCallInterface, this::emitCallbackCall) as CallbackCall }
    }
  }

  private fun createWrapper(m: Method, interfaces: Array<String>, emitCode: (Method, ClassWriter) -> Unit): Any {
    val className = "generated.li.cil.oc.CallWrapper_" + generateId(m)
    if (!GeneratedClassLoader.containsClass(className)) {
      val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
      cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER, className.replace('.', '/'), null, ObjectNameASM, interfaces)
      emitConstructor(cw)
      emitCode(m, cw)
      cw.visitEnd()
      GeneratedClassLoader.addClass(className, cw.toByteArray)
    }

    GeneratedClassLoader.findClass(className).newInstance() as Any
  }

  private fun emitConstructor(cw: ClassWriter) {
    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, ObjectNameASM, "<init>", "()V", false)
    mv.visitInsn(Opcodes.RETURN)
    mv.visitMaxs(1, 1)
    mv.visitEnd()
  }

  private fun emitCallbackCall(m: Method, cw: ClassWriter) {
    val className = m.declaringClass.name.replace('.', '/')
    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "call", CallbackCallDesc, null, null)
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ALOAD, 1)
    mv.visitTypeInsn(Opcodes.CHECKCAST, className)
    mv.visitVarInsn(Opcodes.ALOAD, 2)
    mv.visitVarInsn(Opcodes.ALOAD, 3)
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, m.name, Type.getMethodDescriptor(m), false)
    mv.visitInsn(Opcodes.ARETURN)
    mv.visitMaxs(3, 3)
    mv.visitEnd()
  }

  private fun generateId(m: Method): String = MethodIdCache.getOrPut(m) {m.declaringClass.name.replace('.', '_') + "_" + m.name }

  private object GeneratedClassLoader: ClassLoader(OpenComputers.javaClass.classLoader) {
    private val GeneratedClasses = mutableMapOf<String, Class<*>>()

    fun containsClass(name: String) = GeneratedClasses.contains(name)

    fun addClass(name: String, bytes: ByteArray): Unit {
      GeneratedClasses[name] = defineClass(name, bytes, 0, bytes.size)
    }

    override fun findClass(name: String): Class<*> {
      GeneratedClasses.get(name) match {
        case Some(clazz) => clazz
        case _ => super.findClass(name)
      }
    }
  }
}