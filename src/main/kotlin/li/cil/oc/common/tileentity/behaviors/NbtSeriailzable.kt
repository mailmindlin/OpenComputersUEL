package li.cil.oc.common.tileentity.behaviors

import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

interface NbtSeriailzable: Behavior {
    fun readFromNBTForServer(nbt: NBTTagCompound) {}

    fun writeToNBTForServer(nbt: NBTTagCompound) {}

    @SideOnly(Side.CLIENT)
    fun readFromNBTForClient(nbt: NBTTagCompound) {}

    fun writeToNBTForClient(nbt: NBTTagCompound) {}
}