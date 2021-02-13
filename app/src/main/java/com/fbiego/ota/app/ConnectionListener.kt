package com.fbiego.ota.app

interface ConnectionListener {
    fun onConnectionChanged(state: Boolean)
}