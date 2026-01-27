package li.cil.oc.server.network

import li.cil.oc.Settings
import li.cil.oc.api.network.Connector as NetConnector
import li.cil.oc.api.network.Node as ImmutableNode
import li.cil.oc.common.item.data.NodeData
import net.minecraft.nbt.NBTTagCompound
import kotlin.math.max
import kotlin.math.min

interface Connector : NetConnector, Node {
  var localBufferSizeKt: Double
  override fun localBufferSize(): Double = localBufferSizeKt
  var localBufferKt: Double
  override fun localBuffer(): Double = localBufferKt

  var distributor: Distributor?

  // ----------------------------------------------------------------------- //

  override fun globalBuffer(): Double = distributor?.globalBuffer ?: localBufferKt

  override fun globalBufferSize(): Double = distributor?.globalBufferSize ?: localBufferSizeKt

  // ----------------------------------------------------------------------- //

  override fun changeBuffer(delta: Double): Double {
    if (delta == 0.0) return 0.0
    if (Settings.get.ignorePower) {
      return if (delta < 0) 0.0 else delta
    }
    return synchronized(this) {
      val dist = distributor
      if (dist != null) {
        synchronized(dist) {
          dist.changeBuffer(change(delta))
        }
      } else {
        change(delta)
      }
    }
  }

  private fun change(delta: Double): Double {
    if (localBufferSizeKt <= 0) return delta
    val oldBuffer = localBufferKt
    localBufferKt += delta
    val remaining = if (localBufferKt < 0) {
      val rem = localBufferKt
      localBufferKt = 0.0
      rem
    } else if (localBufferKt > localBufferSizeKt) {
      val rem = localBufferKt - localBufferSizeKt
      localBufferKt = localBufferSizeKt
      rem
    } else {
      0.0
    }
    if (localBufferKt != oldBuffer) {
      val dist = distributor
      if (dist != null) {
        dist.globalBuffer = max(0.0, min(dist.globalBufferSize, dist.globalBuffer - oldBuffer + localBufferKt))
      }
    }
    return remaining
  }

  override fun tryChangeBuffer(delta: Double): Boolean {
    if (delta == 0.0) return true
    if (Settings.get.ignorePower) return delta < 0
    return synchronized(this) {
      val dist = distributor
      if (dist != null) {
        synchronized(dist) {
          if (localBufferKt > localBufferSizeKt) {
            dist.changeBuffer(localBufferKt - localBufferSizeKt)
            localBufferKt = localBufferSizeKt
          }
          val newGlobalBuffer = globalBuffer() + delta
          (delta > 0 || newGlobalBuffer >= 0) && (delta < 0 || newGlobalBuffer <= globalBufferSize()) && dist.changeBuffer(delta) == 0.0
        }
      } else {
        val newLocalBuffer = localBufferKt + delta
        if ((delta < 0 && newLocalBuffer < 0) || (delta > 0 && newLocalBuffer > localBufferSizeKt)) {
          false
        } else {
          localBufferKt = newLocalBuffer
          true
        }
      }
    }
  }

  override fun setLocalBufferSize(size: Double) {
    val clampedSize = max(size, 0.0)
    synchronized(this) {
      val dist = distributor
      if (dist != null) {
        synchronized(dist) {
          val oldSize = localBufferSizeKt
          // Must apply new size before trying to register with distributor, else
          // we get ignored if our size is zero.
          localBufferSizeKt = clampedSize
          if (network != null) {
            if (oldSize <= 0 && clampedSize > 0) dist.addConnector(this)
            else if (oldSize > 0 && clampedSize == 0.0) dist.removeConnector(this)
            else dist.globalBufferSize = max(dist.globalBufferSize - oldSize + clampedSize, 0.0)
          }
          val surplus = max(localBufferKt - clampedSize, 0.0)
          changeBuffer(-surplus)
          dist.changeBuffer(surplus)
        }
      } else {
        localBufferSizeKt = clampedSize
        localBufferKt = min(localBufferKt, localBufferSizeKt)
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override fun onDisconnect(node: ImmutableNode) {
    super.onDisconnect(node)
    if (node == this) {
      synchronized(this) {
        distributor = null
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override fun load(nbt: NBTTagCompound) {
    super.load(nbt)
    localBufferKt = nbt.getDouble(NodeData.BufferTag)
  }

  override fun save(nbt: NBTTagCompound) {
    super.save(nbt)
    nbt.setDouble(NodeData.BufferTag, min(localBufferKt, localBufferSizeKt))
  }
}
