package com.fbiego.ota.app

class ConnectionReceiver {
    companion object {
        private lateinit var mListener:ConnectionListener
        fun bindListener(listener:ConnectionListener) {
            mListener = listener
        }
    }

    fun notifyStatus(state: Boolean){
        mListener.onConnectionChanged(state)
    }
}