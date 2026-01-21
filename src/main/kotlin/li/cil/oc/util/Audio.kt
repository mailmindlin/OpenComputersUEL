package li.cil.oc.util

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import net.minecraft.client.Minecraft
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.init.SoundEvents
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.OpenALException
import java.nio.ByteBuffer
import kotlin.experimental.xor
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin

/**
 * This class contains the logic used by computers' internal "speakers".
 * It can generate square waves with a specific frequency and duration
 * and will play them through OpenAL, acquiring sources as necessary.
 * Tones that have finished playing are disposed automatically in the
 * tick handler.
 */
object Audio {
    private val sampleRate: Int
        get() = Settings.get.beepSampleRate

    private val amplitude: Int
        get() = Settings.get.beepAmplitude

    private val maxDistance: Float
        get() = Settings.get.beepRadius.toFloat()

    private val sources = mutableSetOf<Source>()

    private val volume: Float
        get() = Minecraft.getMinecraft().gameSettings.getSoundLevel(SoundCategory.BLOCKS)

    private var disableAudio = false

    @JvmStatic
    fun play(x: Float, y: Float, z: Float, frequencyInHz: Int, durationInMilliseconds: Int) {
        play(x, y, z, ".", frequencyInHz, durationInMilliseconds)
    }

    @JvmStatic
    @JvmOverloads
    fun play(x: Float, y: Float, z: Float, pattern: String, frequencyInHz: Int = 1000, durationInMilliseconds: Int = 200) {
        val mc = Minecraft.getMinecraft()
        val distanceBasedGain = max(0.0, 1.0 - mc.player.getDistance(x.toDouble(), y.toDouble(), z.toDouble()) / maxDistance).toFloat()
        val gain = distanceBasedGain * volume
        if (gain <= 0 || amplitude <= 0) return

        if (disableAudio) {
            // Fallback audio generation, using built-in Minecraft sound. This can be
            // necessary on certain systems with audio cards that do not have enough
            // memory. May still fail, but at least we can say we tried!
            // Valid range is 20-2000Hz, clamp it to that and get a relative value.
            // MC's pitch system supports a minimum pitch of 0.5, however, so up it
            // by that.
            val clampedFrequency = ((frequencyInHz - 20).coerceIn(0, 1980)) / 1980f + 0.5f
            var delay = 0
            for (ch in pattern) {
                val record = PositionedSoundRecord(SoundEvents.BLOCK_NOTE_HARP, SoundCategory.BLOCKS, gain, clampedFrequency, BlockPos(x.toDouble(), y.toDouble(), z.toDouble()))
                if (delay == 0) mc.soundHandler.playSound(record)
                else mc.soundHandler.playDelayedSound(record, delay)
                delay += max(1, ((if (ch == '.') durationInMilliseconds else 2 * durationInMilliseconds) * 20 / 1000))
            }
        } else {
            if (AL.isCreated()) {
                val sampleCounts = pattern.toCharArray()
                    .map { ch -> if (ch == '.') durationInMilliseconds else 2 * durationInMilliseconds }
                    .map { it * sampleRate / 1000 }
                // 50ms pause between pattern parts.
                val pauseSampleCount = 50 * sampleRate / 1000
                val data = BufferUtils.createByteBuffer(sampleCounts.sum() + (sampleCounts.size - 1) * pauseSampleCount)
                val step = frequencyInHz / sampleRate.toFloat()
                var offset = 0f
                for (sampleCount in sampleCounts) {
                    for (sample in 0 until sampleCount) {
                        val angle = 2 * PI * offset
                        val value = (sign(sin(angle)) * amplitude).toInt().toByte() xor 0x80.toByte()
                        offset += step
                        if (offset > 1) offset -= 1
                        data.put(value)
                    }
                    if (data.hasRemaining()) {
                        for (sample in 0 until pauseSampleCount) {
                            data.put(127.toByte())
                        }
                    }
                }
                data.rewind()

                // Watch out for sound cards running out of memory... this apparently
                // really does happen. I'm assuming this is due to too many sounds being
                // kept loaded, since from what I can see OC's releasing its audio
                // memory as it should.
                try {
                    synchronized(sources) {
                        sources.add(Source(x, y, z, data, gain))
                    }
                } catch (e: LessUselessOpenALException) {
                    if (e.errorCode == AL10.AL_OUT_OF_MEMORY) {
                        // Well... let's just stop here.
                        OpenComputers.log.info("Couldn't play computer speaker sound because your sound card ran out of memory. Either your sound card is just really low-end, or there are just too many sounds in use already by other mods. Disabling computer speakers to avoid spamming your log file now.")
                        disableAudio = true
                    } else {
                        OpenComputers.log.warn("Error playing computer speaker sound.", e)
                    }
                }
            }
        }
    }

    @JvmStatic
    fun update() {
        if (!disableAudio) {
            synchronized(sources) {
                sources.removeAll { it.checkFinished() }
            }

            // Clear error stack.
            if (AL.isCreated()) {
                try {
                    AL10.alGetError()
                } catch (e: UnsatisfiedLinkError) {
                    OpenComputers.log.warn("Negotiations with OpenAL broke down, disabling sounds.")
                    disableAudio = true
                }
            }
        }
    }

    private class Source(val x: Float, y: Float, z: Float, val data: ByteBuffer, val gain: Float) {
        val source: Int
        val buffer: Int

        init {
            // Clear error stack.
            AL10.alGetError()

            buffer = AL10.alGenBuffers()
            checkALError()

            try {
                AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO8, data, sampleRate)
                checkALError()

                source = AL10.alGenSources()
                checkALError()

                try {
                    AL10.alSourceQueueBuffers(source, buffer)
                    checkALError()

                    AL10.alSource3f(source, AL10.AL_POSITION, x, y, z)
                    AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, maxDistance)
                    AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, maxDistance)
                    AL10.alSourcef(source, AL10.AL_GAIN, gain * 0.3f)
                    checkALError()

                    AL10.alSourcePlay(source)
                    checkALError()
                } catch (t: Throwable) {
                    AL10.alDeleteSources(source)
                    throw t
                }
            } catch (t: Throwable) {
                AL10.alDeleteBuffers(buffer)
                throw t
            }
        }

        fun checkFinished(): Boolean {
            return if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                AL10.alDeleteSources(source)
                AL10.alDeleteBuffers(buffer)
                true
            } else {
                false
            }
        }
    }

    // Having the error code in an accessible way is really cool, you know.
    class LessUselessOpenALException(val errorCode: Int) : OpenALException(errorCode)

    // Custom implementation of Util.checkALError() that uses our custom exception.
    @JvmStatic
    fun checkALError() {
        val errorCode = AL10.alGetError()
        if (errorCode != AL10.AL_NO_ERROR) {
            throw LessUselessOpenALException(errorCode)
        }
    }

    init {
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onTick(e: ClientTickEvent) {
        update()
    }
}
