@file:OptIn(ExperimentalUnsignedTypes::class)

package industries.hannah.pixelblaze.sensor

import kotlinx.coroutines.*
import org.jtransforms.fft.FloatFFT_1D
import java.lang.Math.random
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*


private const val SENSOR_BOARD_FRAME_SIZE = 32 * UShort.SIZE_BYTES + // Frequency data
        UShort.SIZE_BYTES + // energyAvg
        UShort.SIZE_BYTES + // maxFreqMagnitude
        UShort.SIZE_BYTES + // maxFreq
        3 * Short.SIZE_BYTES + // accel
        UShort.SIZE_BYTES + // light
        5 * UShort.SIZE_BYTES// analog inputs

private const val DISC_HEADER_SIZE = 4 + // Packet type header, C++ doesn't specify enum bit width but 4 seems to be it
        UInt.SIZE_BYTES + // senderId
        UInt.SIZE_BYTES // sender ts

private const val SENSOR_BOARD_START = DISC_HEADER_SIZE +
        1 + // expansion type
        3 // padding bytes

private const val PACKET_SIZE = SENSOR_BOARD_START + SENSOR_BOARD_FRAME_SIZE

typealias SensorTargetID = UUID

private val SENSOR_ZERO = ByteArray(SENSOR_BOARD_FRAME_SIZE)


/**
 * THIS CLIENT IS NOT THREADSAFE!
 *
 * At present, this client only supports audio data, but if there's interes
 * it can certainly be expanded
 */
class SensorClient(
    sampleCountPerFrame: UInt,
    private val senderId: Int = (random() * Int.MAX_VALUE.toDouble()).toInt(),
    private val socket: DatagramSocket = DatagramSocket(),
    private val coroutineScope: CoroutineScope? = null,
    pixelblazePort: UShort = 1889u,
) {
    private val sampleBuffer = FloatArray(sampleCountPerFrame.toInt())
    private val sendBuffer = coroutineScope?.run { null } ?: ByteArray(PACKET_SIZE)
    private val fft = coroutineScope?.run { null } ?: FloatFFT_1D(sampleCountPerFrame.toLong())

    private var frameIdx: Int = 0
    private val targets: MutableMap<SensorTargetID, InetAddress> = HashMap()
    private val port = pixelblazePort.toInt()

    fun addTarget(address: InetAddress): SensorTargetID {
        val id: SensorTargetID = UUID.randomUUID()
        targets[id] = address
        return id
    }

    fun removeTarget(id: SensorTargetID) {
        targets.remove(id)
    }

    fun addAudioSample(energy: Float) {
        sampleBuffer[frameIdx] = energy
        frameIdx++
        if (frameIdx == sampleBuffer.size) {
            frameIdx = 0
            if (coroutineScope == null) {
                processAndSendWindow(fft, sampleBuffer, sendBuffer)
            } else {
                val sampleBuf = sampleBuffer.copyOf()
                coroutineScope.launch {
                    val sendBuf = writeHeaders(ByteArray(PACKET_SIZE))
                    processAndSendWindow(FloatFFT_1D(sampleBuffer.size.toLong()), sampleBuf, sendBuf)
                }
            }
        }
    }

    private fun processAndSendWindow(fft: FloatFFT_1D, sampleBuf: FloatArray, sendBuf: ByteArray) {
        val fftResult = computeFFT(fft, sampleBuf)
        serializeSensorData(sendBuf, fftResult)
        sendBytes(sendBuf)
    }

    private fun computeFFT(fft: FloatFFT_1D, buffer: FloatArray): Triple<UShortArray, UShort, UShort> {
        fft.realForward(buffer)

        TODO()
    }

    private fun sendBytes(buf: ByteArray) {
        val wrapping = ByteBuffer.wrap(buf, 8, 4)
        wrapping.putInt(Instant.now().toEpochMilli().toUInt().toInt())

        targets.values.forEach {
            try {
                val packet = DatagramPacket(buf, 0, SENSOR_BOARD_FRAME_SIZE, it, port)
                socket.send(packet)
            } catch (t: Throwable) {
                //TODO: Expose that this is happening. Callback? Record and expose?
            }
        }
    }

    private fun writeHeaders(buf: ByteArray): ByteArray {
        val wrapping = ByteBuffer.wrap(buf)

        wrapping.putInt(DiscoveryPacketType.EXPANSIONBOARD.flag)
        wrapping.putInt(senderId)
        wrapping.putInt(0) // Timestamp, filled in before sending
        wrapping.put(1) // It's a sensor board
        // Not pictured: 3 bytes of padding

        return buf
    }

    private fun serializeSensorData(
        sendBuf: ByteArray,
        frequencies: Triple<UShortArray, UShort, UShort>? = null,
        accel: ShortArray? = null,
        light: UShort? = null,
        inputs: UShortArray? = null
    ) {
        SENSOR_ZERO.copyInto(sendBuf, SENSOR_BOARD_START)
        val wrapping = ByteBuffer.wrap(sendBuf, SENSOR_BOARD_START, SENSOR_BOARD_FRAME_SIZE)

        if (frequencies != null) {
            var freqSum = 0u
            for (idx in frequencies.first.indices) {
                val freq = frequencies.first[idx]
                wrapping.putShort(freq.toShort())
                freqSum += freq
            }
            val avgEnergy = freqSum / frequencies.first.size.toUInt()
            wrapping.putShort(avgEnergy.toShort())
            wrapping.putShort(frequencies.second.toShort())
            wrapping.putShort(frequencies.third.toShort())
        } else {
            wrapping.position(wrapping.position() + 32 * UShort.SIZE_BYTES)
        }

        if (accel != null) {
            accel.forEach { wrapping.putShort(it) }
        } else {
            wrapping.position(wrapping.position() + 3 * Short.SIZE_BYTES)
        }

        wrapping.putShort(light?.toShort() ?: 0)
        inputs?.forEach { wrapping.putShort(it.toShort()) }
    }
}

/**
 * Not really used, but kept around for future projects
 */

internal enum class DiscoveryPacketType(val flag: Int) {
    BEACONPACKET(42), //DiscoveryBeaconPacket
    TIMESYNC(43), //DiscoveryTimeSyncPacket
    TRANSITION(44), //DiscoveryTransitionSimple
    DETAILREQUEST(45), //DiscoveryBeaconPacket specialization
    DETAILPACKET(46), //DiscoveryDetailPacket
    FOLLOW(47), //DiscoverySubscribePacket
    CHUNKREQUEST(48), //DiscoveryChunkRequest
    CHUNKDATA(49), //DiscoveryChunkHeader
    EXPANSIONBOARD(50), //DiscoveryExpansionHeader <---
    SEQUENCERNEXT(51), //DiscoveryBeaconPacket specialization
}

internal data class DiscoveryHeader(
    val type: DiscoveryPacketType,
    val senderId: UInt,
    val senderTime: UInt
)

internal data class DiscoveryExpansionHeader(
    val header: DiscoveryHeader,
    val expansionType: UByte, // 1 for sensor frames
    // 3 padding bytes
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiscoveryExpansionHeader) return false

        if (header != other.header) return false
        if (expansionType != other.expansionType) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + expansionType.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

//data class SensorBoardFrame(
//    val frequencyData: UShortArray,
//    val energyAvg: UShort,
//    val maxFrequencyMagnitude: UShort,
//    val maxFrequency: UShort,
//    val accelerometer: ShortArray,
//    val light: UShort,
//    val analogInputs: UShortArray,
//) {
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (other !is SensorBoardFrame) return false
//
//        if (frequencyData != other.frequencyData) return false
//        if (energyAvg != other.energyAvg) return false
//        if (maxFrequencyMagnitude != other.maxFrequencyMagnitude) return false
//        if (maxFrequency != other.maxFrequency) return false
//        if (!accelerometer.contentEquals(other.accelerometer)) return false
//        if (light != other.light) return false
//        if (analogInputs != other.analogInputs) return false
//
//        return true
//    }
//
//    override fun hashCode(): Int {
//        var result = frequencyData.hashCode()
//        result = 31 * result + energyAvg.hashCode()
//        result = 31 * result + maxFrequencyMagnitude.hashCode()
//        result = 31 * result + maxFrequency.hashCode()
//        result = 31 * result + accelerometer.contentHashCode()
//        result = 31 * result + light.hashCode()
//        result = 31 * result + analogInputs.hashCode()
//        return result
//    }
//}

//    private fun frameToBytes(frame: SensorBoardFrame): ByteArray {
//        val wrapping = ByteBuffer.wrap(buffer, SENSOR_BOARD_START, SENSOR_BOARD_FRAME_SIZE)
//        frame.frequencyData.forEach { wrapping.putShort(it.toShort()) }
//        wrapping.putShort(frame.energyAvg.toShort())
//        wrapping.putShort(frame.maxFrequencyMagnitude.toShort())
//        wrapping.putShort(frame.maxFrequency.toShort())
//        frame.accelerometer.forEach { wrapping.putShort(it) }
//        wrapping.putShort(frame.light.toShort())
//        frame.analogInputs.forEach { wrapping.putShort(it.toShort()) }
//        return buffer
//    }