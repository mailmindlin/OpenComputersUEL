package li.cil.oc.util

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object ThreadPoolFactory {
    @JvmField
    val priority: Int = run {
        // For InternetFilteringRuleTest, where Settings.get is not provided.
        val settings = Settings.get
        val custom = settings?.threadPriority ?: -1
        if (custom < 1) {
            Thread.MIN_PRIORITY + (Thread.NORM_PRIORITY - Thread.MIN_PRIORITY) / 2
        } else {
            custom.coerceIn(Thread.MIN_PRIORITY, Thread.MAX_PRIORITY)
        }
    }

    @JvmStatic
    fun create(name: String, threads: Int): ScheduledExecutorService = Executors.newScheduledThreadPool(threads,
        object : ThreadFactory {
            private val baseName = "OpenComputers-$name-"
            private val threadNumber = AtomicInteger(1)
            private val group: ThreadGroup = System.getSecurityManager()?.threadGroup
                ?: Thread.currentThread().threadGroup

            override fun newThread(r: Runnable): Thread {
                val thread = Thread(group, r, baseName + threadNumber.getAndIncrement())
                if (!thread.isDaemon) {
                    thread.isDaemon = true
                }
                if (thread.priority != priority) {
                    thread.priority = priority
                }
                return thread
            }
        })

    @JvmField
    val safePools: MutableList<SafeThreadPool> = mutableListOf()

    @JvmStatic
    fun createSafePool(name: String, threads: Int): SafeThreadPool {
        val handler = SafeThreadPool(name, threads)
        safePools.add(handler)
        return handler
    }
}

class SafeThreadPool(val name: String, val threads: Int) {
    private var _threadPool: ScheduledExecutorService? = null

    fun withPool(requiresPool: Boolean = true, f: (ScheduledExecutorService) -> Future<*>?): Future<*>? {
        val pool = _threadPool
        if (pool == null) {
            OpenComputers.log.warn("Error handling file saving: Did the server never start?")
            if (requiresPool) {
                OpenComputers.log.warn("Creating new thread pool.")
                newThreadPool()
            } else {
                return null
            }
        } else if (pool.isShutdown || pool.isTerminated) {
            OpenComputers.log.warn("Error handling file saving: Thread pool shut down!")
            if (requiresPool) {
                OpenComputers.log.warn("Creating new thread pool.")
                newThreadPool()
            } else {
                return null
            }
        }
        return _threadPool?.let { f(it) }
    }

    fun newThreadPool() {
        _threadPool?.let { pool ->
            if (!pool.isTerminated) {
                pool.shutdownNow()
            }
        }
        _threadPool = ThreadPoolFactory.create(name, threads)
    }

    fun waitForCompletion() {
        withPool(false) { threadPool ->
            try {
                threadPool.shutdown()
                var terminated = threadPool.awaitTermination(15, TimeUnit.SECONDS)
                if (!terminated) {
                    OpenComputers.log.warn("Warning: Completing all tasks has already taken 15 seconds!")
                    terminated = threadPool.awaitTermination(105, TimeUnit.SECONDS)
                    if (!terminated) {
                        OpenComputers.log.error("Warning: Completing all tasks has already taken two minutes! Aborting")
                        threadPool.shutdownNow()
                    }
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            null
        }
    }
}
