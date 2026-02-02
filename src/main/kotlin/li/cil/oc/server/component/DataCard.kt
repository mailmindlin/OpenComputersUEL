package li.cil.oc.server.component

import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

import com.google.common.hash.Hashing
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractValue
import net.minecraft.nbt.NBTTagCompound
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.output.ByteArrayOutputStream

import java.security.*
import kotlin.jvm.Throws

internal typealias Result = Array<Any?>
abstract class DataCard: ManagedEnvironmentKt(), li.cil.oc.api.driver.DeviceInfo {
  override val node = nodeFactory()
    .withComponent("data", Visibility.Neighbors)
    .withConnector()
    .create()

  // ----------------------------------------------------------------------- //

  private fun checkCost(context: Context, args: Arguments, baseCost: Double, byteCost: Double): ByteArray {
    val data = args.checkByteArray(0)
    if (data.size > Settings.get.dataCardHardLimit) throw IllegalArgumentException("data size limit exceeded")
    val cost = baseCost + data.size * byteCost
    if (!node!!.tryChangeBuffer(-cost)) throw Exception("not enough energy")
    if (data.size > Settings.get.dataCardSoftLimit) context.pause(Settings.get.dataCardTimeout)
    return data
  }

  protected fun checkCost(baseCost: Double) {
    if (!node!!.tryChangeBuffer(-baseCost)) throw Exception("not enough energy")
  }

  protected fun trivialCost(context: Context, args: Arguments) =
    checkCost(context, args, Settings.get.dataCardTrivial, Settings.get.dataCardTrivialByte)

  protected fun simpleCost(context: Context, args: Arguments) =
    checkCost(context, args, Settings.get.dataCardSimple, Settings.get.dataCardSimpleByte)

  protected fun complexCost(context: Context, args: Arguments) =
    checkCost(context, args, Settings.get.dataCardComplex, Settings.get.dataCardComplexByte)

  protected fun asymmetricCost(context: Context, args: Arguments) =
    checkCost(context, args, Settings.get.dataCardAsymmetric, Settings.get.dataCardComplexByte)

  // ----------------------------------------------------------------------- //

  @Suppress("unused", "unused_parameter")
  @Callback(direct = true, doc = """function():number -- The maximum size of data that can be passed to other functions of the card.""")
  fun getLimit(context: Context, args: Arguments): Array<Any?> = result(Settings.get.dataCardHardLimit)

  companion object {
    val SecureRandomInstance: ThreadLocal<SecureRandom> = ThreadLocal.withInitial { SecureRandom.getInstance("SHA1PRNG") }
  }

  open class Tier1: DataCard() {
    companion object {
      private val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Processor,
        DeviceAttribute.Description to "Data processor card",
        DeviceAttribute.Vendor to "S.C. Ltd.",
        DeviceAttribute.Product to "SC01D H45h3r"
      )
    }

    override fun getDeviceInfo() = Companion.deviceInfo

    // ----------------------------------------------------------------------- //

    @Callback(direct = true, limit = 32, doc = """function(data:string):string -- Applies base64 encoding to the data.""")
    fun encode64(context: Context, args: Arguments): Result
      = result(Base64.encodeBase64(trivialCost(context, args)))

    @Callback(direct = true, limit = 32, doc = """function(data:string):string -- Applies base64 decoding to the data.""")
    fun decode64(context: Context, args: Arguments): Result
      = result(Base64.decodeBase64(trivialCost(context, args)))

    @Callback(direct = true, limit = 4, doc = """function(data:string):string -- Applies deflate compression to the data.""")
    fun deflate(context: Context, args: Arguments): Result {
      val data = complexCost(context, args)
      val baos = ByteArrayOutputStream(512)
      val deos = DeflaterOutputStream(baos)
      deos.write(data)
      deos.finish()
      return result(baos.toByteArray())
    }

    @Callback(direct = true, limit = 4, doc = """function(data:string):string -- Applies inflate decompression to the data.""")
    fun inflate(context: Context, args: Arguments): Result {
      val data = complexCost(context, args)
      val baos = ByteArrayOutputStream(512)
      val inos = InflaterOutputStream(baos)
      inos.write(data)
      inos.finish()
      return result(baos.toByteArray())
    }

    @Callback(direct = true, limit = 32, doc = """function(data:string):string -- Computes CRC-32 hash of the data. Result is binary data.""")
    fun crc32(context: Context, args: Arguments): Result {
      val data = trivialCost(context, args)
      return result(Hashing.crc32().hashBytes(data).asBytes())
    }

    @Callback(direct = true, limit = 8, doc = """function(data:string):string -- Computes MD5 hash of the data. Result is binary data.""")
    open fun md5(context: Context, args: Arguments): Result {
      val data = simpleCost(context, args)
      return result(Hashing.md5().hashBytes(data).asBytes())
    }

    @Callback(direct = true, limit = 4, doc = """function(data:string):string -- Computes SHA2-256 hash of the data. Result is binary data.""")
    open fun sha256(context: Context, args: Arguments): Result {
      val data = complexCost(context, args)
      return result(Hashing.sha256().hashBytes(data).asBytes())
    }
  }

  open class Tier2: Tier1() {
    companion object {
      val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Processor,
        DeviceAttribute.Description to "Data processor card",
        DeviceAttribute.Vendor to "S.C. Ltd.",
        DeviceAttribute.Product to "SC02D Cryptic"
      )
    }
    override fun getDeviceInfo() = Companion.deviceInfo

    // ----------------------------------------------------------------------- //

    @Callback(direct = true, limit = 8, doc = """function(data:string[, hmacKey:string]):string -- Computes MD5 hash of the data. Result is binary data.""")
    override fun md5(context: Context, args: Arguments): Result {
      if (args.count() > 1) {
        val data = simpleCost(context, args)
        val key = args.checkByteArray(1)
        return hash(data, key, "MD5", "HmacMD5")
      }
      return super.md5(context, args)
    }

    @Callback(direct = true, limit = 4, doc = """function(data:string[, hmacKey:string]):string -- Computes SHA2-256 hash of the data. Result is binary data.""")
    override fun sha256(context: Context, args: Arguments): Result {
      if (args.count() > 1) {
        val data = complexCost(context, args)
        val key = args.checkByteArray(1)
        return hash(data, key, "SHA-256", "HmacSHA256")
      }
      return super.sha256(context, args)
    }

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, limit = 8, doc = """function(data:string, key: string, iv:string):string -- Encrypt data with AES. Result is binary data.""")
    fun encrypt(context: Context, args: Arguments): Result = crypt(context, args, Cipher.ENCRYPT_MODE)

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, limit = 8, doc = """function(data:string, key:string, iv:string):string -- Decrypt data with AES.""")
    fun decrypt(context: Context, args: Arguments): Result = crypt(context, args, Cipher.DECRYPT_MODE)

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, limit = 4, doc = """function(len:number):string -- Generates secure random binary data.""")
    fun random(context: Context, args: Arguments): Result {
      val len = args.checkInteger(0)

      if (len <= 0 || len > 1024)
        throw IllegalArgumentException("length must be in range [1..1024]")

      checkCost(Settings.get.dataCardComplex + Settings.get.dataCardComplexByte * len)
      val target = ByteArray(len)
      SecureRandomInstance.get().nextBytes(target)
      return result(target)
    }

    // ----------------------------------------------------------------------- //

    private fun crypt(context: Context, args: Arguments, mode: Int): Result {
      val data = simpleCost(context, args)

      val key = args.checkByteArray(1)
      if (key.size != 16)
        throw IllegalArgumentException("expected a 128-bit AES key")

      val iv = args.checkByteArray(2)
      if (iv.size != 16)
        throw IllegalArgumentException("expected a 128-bit AES IV")

      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      cipher.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
      return result(cipher.doFinal(data))
    }

    private fun hash(data: ByteArray, key: ByteArray, mode: String, hmacMode: String): Result {
      val hmac = Mac.getInstance(hmacMode)
      hmac.init(SecretKeySpec(key, hmacMode))
      return result(hmac.doFinal(data))
    }
  }

  class Tier3: Tier2() {
    companion object {
      val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Processor,
        DeviceAttribute.Description to "Data processor card",
        DeviceAttribute.Vendor to "S.C. Ltd.",
        DeviceAttribute.Product to "SC03D Signer"
      )
    }

    override fun getDeviceInfo() = Companion.deviceInfo

    // ----------------------------------------------------------------------- //

    @Suppress("unused")
    @Callback(direct = true, limit = 1, doc = """function([bitLen:number]):userdata, userdata -- Generates key pair. Returns: public, private keys. Allowed key lengths: 256, 384 bits.""")
    fun generateKeyPair(context: Context, args: Arguments): Result {
      checkCost(Settings.get.dataCardAsymmetric)

      val bitLen = args.optInteger(0, 384)
      if (bitLen != 256 && bitLen != 384)
        throw IllegalArgumentException("invalid key length, must be 256 or 384")

      val kpg = KeyPairGenerator.getInstance("EC")
      kpg.initialize(bitLen, SecureRandomInstance.get())
      val kp = kpg.generateKeyPair()

      return result(ECUserdata(kp.public), ECUserdata(kp.private))
    }

    @Suppress("unused")
    @Callback(direct = true, limit = 8, doc = """function(data:string, type:string):userdata -- Restores key from its string representation.""")
    fun deserializeKey(context: Context, args: Arguments): Result {
      val data = simpleCost(context, args)
      val t = args.checkString(1)

      return result(ECUserdata(ECUserdata.deserializeKey(t, data)))
    }

    @Suppress("unused")
    @Callback(direct = true, limit = 1, doc = """function(priv:userdata, pub:userdata):string -- Generates a shared key. ecdh(a.priv, b.pub) == ecdh(b.priv, a.pub)""")
    fun ecdh(context: Context, args: Arguments): Result {
      checkCost(Settings.get.dataCardAsymmetric)
      val privKey = checkUserdata(args, 0, isPublic = false).value
      val pubKey = checkUserdata(args, 1, isPublic = true).value

      val ka = KeyAgreement.getInstance("ECDH")
      ka.init(privKey)
      ka.doPhase(pubKey, true)
      return result(ka.generateSecret())
    }

    @Suppress("unused")
    @Callback(direct = true, limit = 1, doc = """function(data:string, key:userdata[, sig:string]):string or boolean -- Signs or verifies data.""")
    fun ecdsa(context: Context, args: Arguments): Result {
      val data = asymmetricCost(context, args)
      val key = checkUserdata(args, 1)
      val sig = args.optByteArray(2, null)

      val sign = Signature.getInstance("SHA256withECDSA")
      if (sig != null) {
        // Verify mode
        val public = key.value as? PublicKey ?: throw IllegalArgumentException("public key expected")
        sign.initVerify(public)
        sign.update(data)
        return result(sign.verify(sig))
      }
      // Sign mode
      val k = key.value as? PrivateKey ?: throw IllegalArgumentException("private key expected")
      sign.initSign(k)
      sign.update(data)
      return result(sign.sign())
    }

    // ----------------------------------------------------------------------- //

    private fun checkUserdata(args: Arguments, i: Int, isPublic: Boolean? = null): ECUserdata {
      when (val value = args.checkAny(i)) {
        is ECUserdata -> {
          if ((isPublic ?: true) != value.isPublic)
            throw IllegalArgumentException ("${if (isPublic ?: true) "public" else "private"} key expected at ${i+1}")
          return value
        }
        null -> throw IllegalArgumentException("bad argument #${i+1} (userdata expected, got no value)")
        else -> throw IllegalArgumentException ("bad argument #${i+1} (userdata expected, got ${value.javaClass.name})")
      }
    }
  }

  class ECUserdata private constructor(var value: Key?): AbstractValue() {
    // Empty constructor for deserialization.
    @Suppress("unused")
    private constructor(): this(null)
    constructor(value: Key, flag: Boolean = false): this(value as Key?)

    val isPublic: Boolean get() = value is ECPublicKey

    private val keyType get() = if (isPublic) ECUserdata.PublicTypeName else ECUserdata.PrivateTypeName

    // ----------------------------------------------------------------------- //

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = "function():boolean -- Returns whether key is public.")
    fun isPublic(context: Context, args: Arguments): Result = result(isPublic)

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = "function():string -- Returns type of key.")
    fun keyType(context: Context, args: Arguments): Result = result(keyType)

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, limit = 4, doc = "function():string -- Returns string representation of key. Result is binary data.")
    fun serialize(context: Context, args: Arguments): Result = result(value!!.encoded)

    // ----------------------------------------------------------------------- //

    override fun load(nbt: NBTTagCompound): Unit {
      val keyType = nbt.getString("Type")
      val data = nbt.getByteArray("Data")
      value = deserializeKey(keyType, data)
    }

    override fun save(nbt: NBTTagCompound): Unit {
      nbt.setString("Type", keyType)
      nbt.setByteArray("Data", value!!.encoded)
    }

    companion object {
      const val PrivateTypeName = "ec-private"
      const val PublicTypeName = "ec-public"

      @JvmStatic
      @Throws(IllegalArgumentException::class)
      fun deserializeKey(typeName: String, data: ByteArray): Key {
        return when (typeName) {
          PrivateTypeName -> KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(data))
          PublicTypeName -> KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(data))
          else -> throw IllegalArgumentException("invalid key type, must be ec-public or ec-private")
        }
      }
    }
  }
}