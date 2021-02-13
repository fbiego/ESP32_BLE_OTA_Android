package com.fbiego.ota.app

import no.nordicsemi.android.ble.data.Data

interface DataListener {
    fun onDataReceived(data: Data)
}