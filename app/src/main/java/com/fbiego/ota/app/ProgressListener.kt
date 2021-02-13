package com.fbiego.ota.app

interface ProgressListener {
    fun onProgress(progress: Int, text: String)
}