package com.dmitriy.android.serialportturkish

import android.util.Log

object FrameBuilder{
    const val EOT = 0x04.toChar()
    const val ENQ = 0x05.toChar()
    const val STX = 0x02.toChar()
    const val ESC = 0x1B.toChar()
    const val ETX = 0x03.toChar()
    const val ACK = 0x06.toChar()
    const val NAC = 0x15.toChar()
    fun text(value:String,length:Int):String = String.format("%$length.${length}s",value)
    fun number(value:Number,length: Int):String = String.format("%$length.${length}s",value).replace(" ","0")
    fun frameNumber(value:Int):String = number(value, length = 2)
    operator fun Char.plus(other:Char): String = "$this$other"
}

class Frame(val byteArray:ByteArray){
    companion object
}

fun Frame.Companion.build(builder:FrameBuilder.()->String):Frame{
    return Frame(builder.invoke(FrameBuilder).toByteArray())
}

fun Frame.Companion.transmit(unitPrice: Number, tareValue: Number? = null, text:String? = null) = build{
    EOT+ STX+ frameNumber(when {
        tareValue == null && text == null -> 1
        tareValue != null -> 3
        text != null -> 4
        else -> 5
    })+ ESC + listOfNotNull(
            number(value = unitPrice,length = 6),
            tareValue?.let { number(value = it,length = 4) },
            text?.let { text(value = it,length = 13) }
    ).joinToString(separator = ESC.toString()) + ESC + ETX
}

fun Frame.Companion.requestForStatusInformation() = build{ EOT + STX + frameNumber(8) + ETX }

fun Frame.Companion.requestForSaleData() = build { EOT + ENQ }

fun Frame.Companion.checksum(checksum: Long) = build { EOT + STX + frameNumber(10) + ESC + checksum.toHexString() + ETX }

fun Frame.Companion.confirm() = build{ EOT + ENQ }

fun Frame.Companion.checkErrorStatus()=build { EOT + STX + number(8,2)+ EOT }

fun Frame.Companion.testFunc(){
    Log.i("@@@","TEST " + (0x3C - 1).toString())
}

