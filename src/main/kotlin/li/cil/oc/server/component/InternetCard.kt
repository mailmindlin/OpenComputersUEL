package li.cil.oc.server.component

import com.google.common.net.InetAddresses
import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractValue
import li.cil.oc.util.ThreadPoolFactory
import net.minecraftforge.fml.common.FMLCommonHandler
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

class InternetCard: ManagedEnvironmentKt(), DeviceInfoKt {
  override val node = nodeFactory(Visibility.Network).withComponent("internet", Visibility.Neighbors).create()

  private var owner: Context? = null

  private val connections = mutableSetOf<Closable>()

  // ----------------------------------------------------------------------- //

  override val deviceInfo = mapOf(
    DeviceAttribute.Class to DeviceClass.Communication,
    DeviceAttribute.Description to "Internet modem",
    DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product to "SuperLink X-D4NK"
  )

  // ----------------------------------------------------------------------- //

  @Callback(
    direct = true,
    doc = """function():boolean -- Returns whether HTTP requests can be made (config setting)."""
  )
  fun isHttpEnabled(context: Context, args: Arguments): Result = result(Settings.get.httpEnabled)

  @Callback(doc = """function(url:string[, postData:string[, headers:table[, method:string]]]):userdata -- Starts an HTTP request. If this returns true, further results will be pushed using `http_response` signals.""")
  fun request(context: Context, args: Arguments): Result {
    synchronized(this) {
      checkOwner(context)
      val address = args.checkString(0)
      if (!Settings.get.internetAccessAllowed) {
        return result(Unit, "internet access is unavailable")
      }
      if (!Settings.get.httpEnabled) {
        return result(Unit, "http requests are unavailable")
      }
      if (connections.size >= Settings.get.maxConnections)
        throw IOException("too many open connections")

      val post = if (args.isString(1)) args.checkString(1) else null
      val headers: Map<String, String> =
        if (args.isTable(2)) run {
          val raw = args.checkTable(2)
          val result = mutableMapOf<String, String>()
          for ((k, v) in raw)
            result[k.toString()] = v.toString()
          return@run result
        } else emptyMap()

      if (!Settings.get.httpHeadersEnabled && headers.isNotEmpty())
        return result(Unit, "http request headers are unavailable")

      val method = if (args.isString(3)) args.checkString(3) else null
      val request = HTTPRequest(this, checkAddress(address), post, headers, method)
      connections += request
      return result(request)
    }
  }

  @Callback(
    direct = true,
    doc = """function():boolean -- Returns whether TCP connections can be made (config setting)."""
  )
  fun isTcpEnabled(context: Context, args: Arguments): Result = result(Settings.get.tcpEnabled)

  @Callback(doc = """function(address:string[, port:number]):userdata -- Opens a new TCP connection. Returns the handle of the connection.""")
  fun connect(context: Context, args: Arguments): Result = synchronized(this) {
    checkOwner(context)
    val address = args.checkString(0)
    val port = args.optInteger(1, -1)
    if (!Settings.get.internetAccessAllowed) {
      return result(Unit, "internet access is unavailable")
    }
    if (!Settings.get.tcpEnabled)
      return result(Unit, "tcp connections are unavailable")
    if (connections.size >= Settings.get.maxConnections)
      throw IOException("too many open connections")

    val uri = checkUri(address, port)
    val socket = TCPSocket(this, uri, port)
    connections += socket
    result(socket)
  }

  private fun checkOwner(context: Context) {
    val owner = owner
    if (owner == null || context.node() != owner.node())
      throw IllegalArgumentException("can only be used by the owning computer")
  }

  // ----------------------------------------------------------------------- //

  override fun onConnect(node: Node) {
    super.onConnect(node)
    if (owner == null && node.host() is Context && node.isNeighborOf(this.node)) {
      owner = node.host() as Context
    }
  }

  override fun onDisconnect(node: Node) {
    super.onDisconnect(node)
    if (owner != null && (node == this.node || node.host() is Context && (node.host() == owner))) {
      owner = null
      synchronized(this) {
        connections.forEach { it.close() }
        connections.clear()
      }
    }
  }

  override fun onMessage(message: Message) {
    super.onMessage(message)
    val data = message.data()
    if (message.name() != "computer.stopped" && message.name() != "computer.started")
      return
    val owner = owner ?: return
    if (message.source().address() != owner.node().address())
      return
    synchronized(this) {
      connections.forEach { it.close() }
      connections.clear()
    }
  }

  // ----------------------------------------------------------------------- //

  private fun checkUri(address: String, port: Int): URI {
    try {
      val parsed = URI(address)
      if (parsed.host != null && (parsed.port > 0 || port > 0)) {
        return parsed
      }
    } catch (e: Exception) {
    }

    val simple = URI("oc://$address")
    if (simple.host == null) {
      if (simple.port > 0)
        return simple
      if (port > 0)
        return URI("$simple:$port")
    }

    throw IllegalArgumentException("address could not be parsed or no valid port given")
  }

  @Throws(FileNotFoundException::class)
  private fun checkAddress(address: String): URL {
    val url = try {
      URL(address)
    } catch (e: Exception) {
      throw FileNotFoundException("invalid address")
    }

    val protocol = url.protocol
    if (!protocol.matches("^https?$".toRegex()))
      throw FileNotFoundException("unsupported protocol")
    return url
  }

  interface Closable {
    fun close(): Unit
  }

  object TCPNotifier : Thread() {
    private var selector = Selector.open()
    private val toAccept = ConcurrentLinkedQueue<Pair<SocketChannel, () -> Unit>>()

    override fun run() {
      while (true) {
        try {
          while (true) {
            val (channel, action) = toAccept.poll() ?: break
            channel.register(selector, SelectionKey.OP_READ, action)
          }

          selector.select()

          val selectedKeys = selector.selectedKeys()
          val readableKeys = mutableSetOf<SelectionKey>()
          for (key in selectedKeys) {
            if (!key.isReadable) continue
            (key.attachment() as () -> Unit)()
            readableKeys.add(key)
          }

          if (readableKeys.isNotEmpty()) {
            val newSelector = Selector.open()
            selector.keys()
              .filter { it !in readableKeys }
              .forEach { key -> key.channel().register(newSelector, SelectionKey.OP_READ, key.attachment()) }
            selector.close()
            selector = newSelector
          }
        } catch (e: IOException) {
          OpenComputers.log.error("Error in TCP selector loop.", e)
        }
      }
    }

    fun add(channel: SocketChannel, action: () -> Unit) {
      toAccept.offer(Pair(channel, action))
      selector.wakeup()
    }
  }

  companion object {
    // For InternetFilteringRuleTest, where Settings.get is not provided.
    private val threadPool = ThreadPoolFactory.create("Internet", Settings.tryGet?.internetThreads ?: 1)

    init {
      TCPNotifier.start()
    }

    private fun isRequestAllowed(settings: Settings, inetAddress: InetAddress, host: String): Boolean {
      if (!settings.internetAccessAllowed)
        return false

      fun evaluateRules(address: InetAddress): Boolean? =
        settings.internetFilteringRules.firstNotNullOfOrNull { it.apply(address, host) }
      return when (inetAddress) {
        // IPv6 handling
        is Inet6Address -> {
          // If the IP address is an IPv6 address with an embedded IPv4 address, and the IPv4 address is blocked,
          // block this request.
          if (InetAddresses.hasEmbeddedIPv4ClientAddress(inetAddress)) {
            val inet4in6Address = InetAddresses.getEmbeddedIPv4ClientAddress(inetAddress)
            if (evaluateRules(inet4in6Address) == false)
              return false
          }

          // Process address as an IPv6 address.
          evaluateRules(inetAddress) ?: false
        }
        // IPv4 handling: Process address as an IPv4 address.
        is Inet4Address -> evaluateRules(inetAddress) ?: false
        else -> {
          // Unrecognized address type - block.
          OpenComputers.log.warn("Internet Card blocked unrecognized address type: $inetAddress")
          false
        }
      }
    }

    fun checkLists(inetAddress: InetAddress, host: String): Unit {
      if (!isRequestAllowed(Settings.get, inetAddress, host)) {
        throw FileNotFoundException("address is not allowed")
      }
    }
  }

  class TCPSocket private constructor() : AbstractValue(), Closable {

    private var owner: InternetCard? = null
    private var address: Future<InetAddress>? = null
    private var channel: SocketChannel? = null
    private var isAddressResolved = false
    private val id = UUID.randomUUID()

    constructor(owner: InternetCard, uri: URI, port: Int) : this() {
      this.owner = owner
      val channel = SocketChannel.open()!!
      this.channel = channel
      channel.configureBlocking(false)
      address = threadPool.submit(AddressResolver(channel, uri, port))
    }

    private fun setupSelector() {
      val channel = channel ?: return
      TCPNotifier.add(channel) {
        val owner = owner
        if (owner == null) {
          channel.close()
          return@add
        }
        owner.node.sendToVisible("computer.signal", "internet_ready", id.toString())
      }
    }

    @Callback(doc = """function():boolean -- Ensures a socket is connected. Errors if the connection failed.""")
    fun finishConnect(context: Context, args: Arguments): Result {
      val r = synchronized(this) { result(checkConnected()) }
      setupSelector()
      return r
    }

    @Callback(doc = """function([n:number]):string -- Tries to read data from the socket stream. Returns the read byte array.""")
    fun read(context: Context, args: Arguments): Result = synchronized(this) {
      val n = args.optInteger(0, Int.MAX_VALUE).coerceIn(0..Settings.get.maxReadBuffer)
      if (!checkConnected())
        return result(byteArrayOf())
      val buffer = ByteBuffer.allocate(n)
      val read = channel!!.read(buffer)
      if (read == -1)
        return result(Unit)
      setupSelector()
      buffer.flip()
      val result = ByteArray(read)
      buffer.get(result)
      result(result)
    }

    @Callback(doc = """function(data:string):number -- Tries to write data to the socket stream. Returns the number of bytes written.""")
    fun write(context: Context, args: Arguments): Result = synchronized(this) {
      if (checkConnected()) {
        val value = args.checkByteArray(0)
        result(channel!!.write(ByteBuffer.wrap(value)))
      } else result(0)
    }

    @Callback(direct = true, doc = """function() -- Closes an open socket stream.""")
    fun close(context: Context, args: Arguments): Result? = synchronized(this) {
      close()
      null
    }

    @Callback(direct = true, doc = """function():string -- Returns connection ID.""")
    fun id(context: Context, args: Arguments): Result = synchronized(this) {
      result(id.toString())
    }

    override fun dispose(context: Context): Unit {
      super.dispose(context)
      close()
    }

    override fun close(): Unit {
      val card = owner ?: return

      card.connections.remove(this)
      address?.cancel(true)
      channel?.close()
      owner = null
      address = null
      channel = null
    }

    private fun checkConnected(): Boolean {
      if (owner == null)
        throw IOException("connection lost")
      val channel = channel!!
      try {
        if (isAddressResolved)
          return channel.finishConnect()

        val address = address
        if (address?.isCancelled == true) {
          // I don't think this can ever happen, Justin Case.
          channel.close()
          throw IOException("bad connection descriptor")
        }

        if (!address!!.isDone)
          return false
        // Check for errors.
        try {
          address.get()
        } catch (e: ExecutionException) {
          throw e.cause!!
        }
        isAddressResolved = true
        return false
      } catch (e: Exception) {
        close()
        return false
      }
    }

    // This has to be an explicit internal class instead of an anonymous one
    // because the scala compiler breaks otherwise. Yay for compiler bugs.
    private class AddressResolver(val channel: SocketChannel, val uri: URI, val port: Int) : Callable<InetAddress> {
      override fun call(): InetAddress {
        val resolved = InetAddress.getByName(uri.host)
        checkLists(resolved, uri.host)
        val address = InetSocketAddress(resolved, if (uri.port != -1) uri.port else port)
        channel.connect(address)
        return resolved
      }
    }

  }

  class HTTPRequest private constructor() : AbstractValue(), Closable {
    private var owner: InternetCard? = null
    private var response: Triple<Int, String, Any?>? = null
    private var stream: Future<InputStream>? = null
    private val queue = ConcurrentLinkedQueue<Byte>()
    private var reader: Future<*>? = null
    private var eof = false

    constructor(owner: InternetCard, url: URL, post: String?, headers: Map<String, String>, method: String?) : this() {
      this.owner = owner
      this.stream = threadPool.submit(RequestSender(url, post, headers, method))
    }

    @Callback(doc = """function():boolean -- Ensures a response is available. Errors if the connection failed.""")
    fun finishConnect(context: Context, args: Arguments): Result = synchronized(this) { result(checkResponse()) }

    @Callback(direct = true, doc = """function():number, string, table -- Get response code, message and headers.""")
    fun response(context: Context, args: Arguments): Result = synchronized(this) {
      val (code, message, headers) = response ?: return result(Unit)
      result(code, message, headers)
    }

    @Callback(doc = """function([n:number]):string -- Tries to read data from the response. Returns the read byte array.""")
    fun read(context: Context, args: Arguments): Result = synchronized(this) {
      val n = args.optInteger(0, Int.MAX_VALUE).coerceIn(0..Settings.get.maxReadBuffer)
      if (!checkResponse())
        return result(byteArrayOf())
      if (eof && queue.isEmpty())
        return result(Unit)
      val buffer = ByteBuffer.allocate(n)
      var read = 0
      while (!queue.isEmpty() && read < n) {
        buffer.put(queue.poll())
        read += 1
      }
      if (read == 0)
        readMore()
      val result = ByteArray(read)
      buffer.flip()
      buffer.get(result)
      result(result)
    }

    @Callback(direct = true, doc = """function() -- Closes an open socket stream.""")
    fun close(context: Context, args: Arguments): Result? = synchronized(this) {
      close()
      null
    }

    override fun dispose(context: Context): Unit {
      super.dispose(context)
      close()
    }

    override fun close(): Unit {
      val card = owner ?: return
      card.connections.remove(this)
      stream!!.cancel(true)
      if (reader != null) {
        reader!!.cancel(true)
      }
      owner = null
      stream = null
      reader = null
    }

    private fun checkResponse(): Boolean = synchronized(this) {
      if (owner == null) throw IOException("connection lost")

      val stream = stream!!
      if (!stream.isDone)
        return false
      if (reader == null) {
        // Check for errors.
        try {
          stream.get()
        } catch (e: ExecutionException) {
          throw e.cause ?: e
        }
        readMore()
      }
      return true
    }

    private fun readMore(): Unit {
      val reader = reader
      if (reader != null && !reader.isCancelled && !reader.isDone)
        return
      if (eof)
        return
      this.reader = threadPool.submit {
        val buffer = ByteArray(Settings.get.maxReadBuffer)
        val count = stream!!.get().read(buffer)
        if (count < 0) {
          eof = true
        }
        for (i in 0 until count) {
          queue.add(buffer[i])
        }
      }
    }

    // This one doesn't (see comment in TCP socket), but I like to keep it consistent.
    private inner class RequestSender(
      val url: URL,
      val post: String?,
      val headers: Map<String, String>,
      val method: String?
    ) : Callable<InputStream> {
      override fun call(): InputStream {
        try {
          checkLists(InetAddress.getByName(url.host), url.host)
          val proxy = FMLCommonHandler.instance().minecraftServerInstance.serverProxy
            ?: java.net.Proxy.NO_PROXY

          val http = url.openConnection(proxy) as? HttpURLConnection ?: throw IOException("unexpected connection type")
          try {
            http.setDoInput(true)
            http.setDoOutput(post != null)
            http.setRequestMethod(method ?: if (post != null) "POST" else "GET")
            http.setRequestProperty(
              "User-Agent",
              Settings.get.httpUserAgent.replace("\$version", OpenComputers.Version)
            )
            headers.forEach { (k, v) -> http.setRequestProperty(k, v) }
            if (post != null) {
              http.setReadTimeout(Settings.get.httpTimeout)

              BufferedWriter(OutputStreamWriter(http.outputStream)).use {
                it.write(post)
              }
            }

            // Finish the connection. Call getInputStream a second time below to re-throw any exception.
            // This avoids getResponseCode() waiting for the connection to end in the synchronized block.
            try {
              http.inputStream
            } catch (e: Exception) {
            }

            synchronized(this@HTTPRequest) {
              response = Triple(http.responseCode, http.responseMessage, http.headerFields)
            }

            // TODO: This should allow accessing getErrorStream() for reading unsuccessful HTTP responses' output,
            // but this would be a breaking change for existing OC code.
            return http.inputStream
          } finally {
            http.disconnect()
          }
        } catch (e: UnknownHostException) {
          throw IOException("unknown host: " + (e.message ?: e.toString()), e)
        } catch (e: Exception) {
          throw IOException(e.message ?: e.toString(), e)
        }
      }
    }
  }
}