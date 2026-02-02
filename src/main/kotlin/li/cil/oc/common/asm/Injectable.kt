package li.cil.oc.common.asm

/**
 * This interface is kind of the opposite to FML's Optional annotations.
 *
 * Instead of stripping interfaces if they are not present, it will inject them
 * when they *are* present. This helps with some strange cases where
 * stripping does not work as it should.
 */
internal object Injectable {
    /** Mark a list of interfaces as injectable */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    annotation class InterfaceList(vararg val value: Interface)

    /** Used to inject optional interfaces */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    annotation class Interface(
        /**
         * The fully qualified name of the interface to inject.
         */
        val value: String,
        /**
         * The modid that is required to be present for the injecting to occur.
         *
         * Note that injection will not occur if the interface is not fully
         * implemented.
         */
        val modid: String
    )
}
