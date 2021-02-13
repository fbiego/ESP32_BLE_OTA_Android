package com.fbiego.ota.app

import no.nordicsemi.android.ble.data.Data

class DataReceiver {
    companion object {
        private lateinit var mListener:DataListener
        fun bindListener(listener:DataListener) {
            mListener = listener
        }
    }

    fun getData(data: Data){
        mListener.onDataReceived(data)
    }
}