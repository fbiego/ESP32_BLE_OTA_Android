package com.fbiego.ota.app

class ProgressReceiver {
    companion object {
        private lateinit var mListener: ProgressListener
        fun bindListener(listener: ProgressListener) {
            mListener = listener
        }
    }
    fun getProgress(progress: Int, text: String){
        mListener.onProgress(progress, text)
    }
}