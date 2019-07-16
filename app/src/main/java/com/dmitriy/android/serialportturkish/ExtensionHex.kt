package com.dmitriy.android.serialportturkish

import kotlin.experimental.and

private val HEX_DIGITS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

fun ByteArray.dumpHexString(offset: Int = 0, length: Int = this.size): String {
    val result = StringBuilder()

    val line = ByteArray(16)
    var lineIndex = 0

    result.append("\n0x${offset.toHexString()}")

    for (i in offset until offset + length) {
        if (lineIndex == 16) {
            result.append(" ")
            for (j in 0..15) {
                if (line[j] > ' '.toByte() && line[j] < '~'.toByte()) {
                    result.append(String(line, j, 1))
                } else {
                    result.append(".")
                }
            }
            result.append("\n0x")
            result.append(i.toHexString())
            lineIndex = 0
        }
        val byte = this[i]
        result.append(" ")
        result.append(HEX_DIGITS[byte ushr (4) and 0x0F])
        result.append(HEX_DIGITS[byte and 0x0F])
        line[lineIndex++] = byte
    }

    if (lineIndex != 16) {
        var count = (16 - lineIndex) * 3
        count++
        for (i in 0 until count) {
            result.append(" ")
        }
        for (i in 0 until lineIndex) {
            if (line[i] > ' '.toByte() && line[i] < '~'.toByte()) {
                result.append(String(line, i, 1))
            } else {
                result.append(".")
            }
        }
    }

    return result.toString()
}

fun ByteArray.toHexString(offset: Int = 0, length: Int = this.size): String {
    val buffer = CharArray(length * 2)
    var bufferIndex = 0
    for (i in offset until offset + length) {
        val byte = this[i]
        buffer[bufferIndex++] = HEX_DIGITS[byte ushr (4) and 0x0F]
        buffer[bufferIndex++] = HEX_DIGITS[byte and 0x0F]
    }
    return String(buffer)
}

fun Byte.toHexString(): String = this.toByteArray().toHexString()
fun Short.toHexString(): String = this.toByteArray().toHexString()
fun Int.toHexString(): String = this.toByteArray().toHexString()

fun Byte.toByteArray(): ByteArray {
    val array = ByteArray(1)
    array[0] = this
    return array
}

fun Short.toByteArray(): ByteArray {
    val array = ByteArray(2)
    array[1] = (this and 0xFF).toByte()
    array[0] = (this shr 8 and 0xFF).toByte()
    return array
}

fun Int.toByteArray(): ByteArray {
    val array = ByteArray(4)
    array[3] = (this and 0xFF).toByte()
    array[2] = (this shr 8 and 0xFF).toByte()
    array[1] = (this shr 16 and 0xFF).toByte()
    array[0] = (this shr 24 and 0xFF).toByte()
    return array
}

fun String.hexStringToByteArray(short: Short): ByteArray {
    val hexString = this
    val buffer = ByteArray(hexString.length / 2)
    for (i in 0 until length step 2) {
        buffer[i / 2] = (hexString[i].hexCharToByte() shl 4 or hexString[i + 1].hexCharToByte())
    }
    return buffer
}

fun Char.hexCharToByte(): Byte {
    val hexChar = this
    return when (hexChar) {
        in '0'..'9' -> hexChar - '0'
        in 'A'..'F' -> hexChar - 'A' + 10
        in 'a'..'f' -> hexChar - 'a' + 10
        else -> throw RuntimeException("Invalid hex char '$hexChar'")
    }.toByte()
}
fun Long.toHexString(): String = this.toByteArray().toHexString()

fun Long.toByteArray(): ByteArray {
    val array = ByteArray(8)
    array[7] = (this and 0xFF).toByte()
    array[6] = (this shr 8 and 0xFF).toByte()
    array[5] = (this shr 16 and 0xFF).toByte()
    array[4] = (this shr 24 and 0xFF).toByte()
    array[3] = (this shr 32 and 0xFF).toByte()
    array[2] = (this shr 40 and 0xFF).toByte()
    array[1] = (this shr 48 and 0xFF).toByte()
    array[0] = (this shr 56 and 0xFF).toByte()
    return array
}

operator fun CharArray.get(index: Byte): Char = this[index.toInt()]

/** Shifts this value left by the [bitCount] number of bits. */
infix fun Byte.shl(bitCount: Int): Byte = (this.toInt() shl bitCount).toByte()

/** Shifts this value right by the [bitCount] number of bits, filling the leftmost bits with copies of the sign bit. */
infix fun Byte.shr(bitCount: Int): Byte = (this.toInt() shr bitCount).toByte()

/** Shifts this value right by the [bitCount] number of bits, filling the leftmost bits with zeros. */
infix fun Byte.ushr(bitCount: Int): Byte = (this.toInt() ushr bitCount).toByte()

/** Performs a bitwise AND operation between the two values. */
infix fun Byte.and(other: Byte): Byte = (this.toInt() and other.toInt()).toByte()

/** Performs a bitwise OR operation between the two values. */
infix fun Byte.or(other: Byte): Byte = (this.toInt() or other.toInt()).toByte()

/** Performs a bitwise XOR operation between the two values. */
infix fun Byte.xor(other: Byte): Byte = (this.toInt() xor other.toInt()).toByte()

/** Inverts the bits in this value. */
fun Byte.inv(): Byte = (this.toInt().inv()).toByte()

/** Shifts this value left by the [bitCount] number of bits. */
infix fun Short.shl(bitCount: Int): Short = (this.toInt() shl bitCount).toShort()

/** Shifts this value right by the [bitCount] number of bits, filling the leftmost bits with copies of the sign bit. */
infix fun Short.shr(bitCount: Int): Short = (this.toInt() shr bitCount).toShort()

/** Shifts this value right by the [bitCount] number of bits, filling the leftmost bits with zeros. */
infix fun Short.ushr(bitCount: Int): Short = (this.toInt() ushr bitCount).toShort()


