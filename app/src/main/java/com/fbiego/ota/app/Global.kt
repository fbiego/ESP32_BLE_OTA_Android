package com.fbiego.ota.app

import androidx.core.app.NotificationCompat

fun Byte.toPInt() = toInt() and 0xFF

fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte()
}

fun priority(pr: Int): Int{
    return when (pr){
        0 -> NotificationCompat.PRIORITY_MIN
        1 -> NotificationCompat.PRIORITY_LOW
        2 -> NotificationCompat.PRIORITY_DEFAULT
        3 -> NotificationCompat.PRIORITY_HIGH
        4 -> NotificationCompat.PRIORITY_MAX
        else -> NotificationCompat.PRIORITY_DEFAULT
    }
}