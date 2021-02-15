/*
 * MIT License
 *
 *    Copyright (c) 2021 Felix Biego
 *
 *    Permission is hereby granted, free of charge, to any person obtaining a copy
 *    of this software and associated documentation files (the "Software"), to deal
 *    in the Software without restriction, including without limitation the rights
 *    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *    copies of the Software, and to permit persons to whom the Software is
 *    furnished to do so, subject to the following conditions:
 *
 *    The above copyright notice and this permission notice shall be included in all
 *    copies or substantial portions of the Software.
 *
 *    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *    SOFTWARE.
 */

package com.fbiego.ota.app

import android.annotation.TargetApi
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.fbiego.ota.BuildConfig
import com.fbiego.ota.R
import com.fbiego.ota.ble.LEManager
import com.fbiego.ota.ble.LeManagerCallbacks
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.*
import com.fbiego.ota.MainActivity as MN

/**
 *
 */
class ForegroundService : Service(), DataListener {

    companion object {

        var bleManager: BleManager<LeManagerCallbacks>? = null

        const val SERVICE_ID = 9052
        const val SERVICE_ID2 = 9053
        const val NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID
        const val VESPA_DEVICE_ADDRESS = "00:00:00:00:00:00" // <--- YOUR MAC address here

        var deviceName = ""
        var bat = 0
        var notify = 0

        var serviceRunning = false

        var connected = false
        var prt = 0
        var parts = 0


    }

    private var startID = 0
    private lateinit var context: Context
    private var isReconnect = false


    /**
     * Called by the system when the service is first created.  Do not call this method directly.
     */
    override fun onCreate() {
        super.onCreate()

        isReconnect = false
        context = this
        notificationChannel(false, this)


        Timber.w("onCreate")
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val remoteMacAddress = pref.getString(MN.PREF_KEY_REMOTE_MAC_ADDRESS, VESPA_DEVICE_ADDRESS)
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val leDevice = bluetoothManager.adapter.getRemoteDevice(remoteMacAddress)



        bleManager = LEManager(this)
        (bleManager as LEManager).setGattCallbacks(bleManagerCallback)
        (bleManager as LEManager).connect(leDevice).enqueue()


        Timber.w("onCreate: Bluetooth adapter state: ${bluetoothManager.adapter.state}")


        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        DataReceiver.bindListener(this)

    }


    @TargetApi(Build.VERSION_CODES.O)
    private fun notificationChannel(priority: Boolean, context: Context): NotificationManager {
        val notificationMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val notificationChannel: NotificationChannel
            if (priority){
                notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL, BuildConfig.APPLICATION_ID, NotificationManager.IMPORTANCE_HIGH)
                notificationChannel.description = context.getString(R.string.channel_desc)
                notificationChannel.lightColor = ContextCompat.getColor(context, R.color.purple_700)
                notificationChannel.enableLights(true)
                notificationChannel.enableVibration(true)
            } else {
                notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL, BuildConfig.APPLICATION_ID, NotificationManager.IMPORTANCE_MIN)
                notificationChannel.description = context.getString(R.string.channel_desc)
                notificationChannel.lightColor = ContextCompat.getColor(context, R.color.purple_700)
                notificationChannel.enableLights(false)
                notificationChannel.enableVibration(false)
            }
            notificationMgr.createNotificationChannel(notificationChannel)
        }
        return notificationMgr

    }


    fun sendData(data: ByteArray): Boolean{
        return if (bleManager != null) {
            (bleManager as LEManager).writeBytes(data)
        } else {
            false
        }
    }


    private fun transmitData(data: ByteArray, progress: Int, context: Context): Boolean{
        return if (bleManager != null) {
            (bleManager as LEManager).transmitData(data, progress, context, this@ForegroundService::onProgress)
        } else {
            false
        }
    }





    @RequiresApi(Build.VERSION_CODES.P)
    override fun onDestroy() {
        Timber.w("onDestroy")
        serviceRunning = false
        connected = false
        isReconnect = false


        startID = 0

        bleManager?.close()

        unregisterReceiver(bluetoothReceiver)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationMgr.deleteNotificationChannel(NOTIFICATION_CHANNEL)
        }

        super.onDestroy()
    }

    /**
     * Create/Update the notification
     */
    fun notify(text: String, priority: Boolean, id: Int): Notification {
        // Launch the MainAcivity when user taps on the Notification
        Timber.w("Context ${context.packageName}")
        val intent = Intent(context, MN::class.java)

        val pendingIntent = PendingIntent.getActivity(context, 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT)

        val notBuild = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)

        notBuild.setSmallIcon(R.drawable.ic_bt)

        notBuild.color = ContextCompat.getColor(this, R.color.purple_500)

        notBuild.setContentIntent(pendingIntent)
        //notBuild.setContentTitle(contentText)
        notBuild.setContentText(text)
        if (priority) {
            notBuild.priority = NotificationCompat.PRIORITY_HIGH
            //notBuild.setSound(Uri.parse("android.resource://${BuildConfig.APPLICATION_ID}/"+R.raw.notification))
            notBuild.setShowWhen(true)
        } else {
            notBuild.priority = priority(prt)
            notBuild.setSound(Uri.EMPTY)
            notBuild.setShowWhen(false)

        }
        notBuild.setOnlyAlertOnce(true)
        val notification= notBuild.build()
        notificationChannel(priority, context).notify(id, notification)
        return notification
    }

    private fun notifyProgress(text: String,  progress: Int, context: Context): Notification {

        val notBuild = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
        notBuild.setSmallIcon(android.R.drawable.stat_sys_upload)
        notBuild.color = ContextCompat.getColor(context, R.color.purple_700)



        notBuild.setContentTitle(text)
        notBuild.setContentText("$progress%")
        if (progress == 0){
            notBuild.setProgress(0, 0, true)
        } else {
            notBuild.setProgress(100, progress, false)
        }
        notBuild.priority = NotificationCompat.PRIORITY_HIGH
        notBuild.setSound(Uri.EMPTY)
        notBuild.setShowWhen(true)
        notBuild.setOnlyAlertOnce(true)

        val notification= notBuild.build()
        notificationChannel(true, context).notify(SERVICE_ID2, notification)
        return notification
    }

    private fun cancelNotification(notifyId: Int, context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notifyId)
    }


    @RequiresApi(Build.VERSION_CODES.P)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Timber.w("onStartCommand {intent=${intent == null},flags=$flags,startId=$startId}")

        //val calendar = Calendar.getInstance(Locale.getDefault())
        //Timber.e("Service started with startId: ${this.startID} at ${calendar.time}")
        if (intent == null || this.startID != 0) {
            //service restarted
            Timber.w("onStartCommand - already running")
        } else {
            //started by intent or pending intent
            this.startID = startId
            val notification = notify(getString(R.string.scan), false,  SERVICE_ID)
            startForeground(SERVICE_ID, notification)

            if (intent.hasExtra("origin")){
                Timber.w("Service started on device boot")
            } else {
                connected = false
                ConnectionReceiver().notifyStatus(false)
            }


        }




        serviceRunning = true


        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }




    val bleManagerCallback: LeManagerCallbacks = object : LeManagerCallbacks() {
        /**
         * Called when the device has been connected. This does not mean that the application may start communication.
         * A service discovery will be handled automatically after this call. Service discovery
         * may ends up with calling [.onServicesDiscovered] or
         * [.onDeviceNotSupported] if required services have not been found.
         * @param device the device that got connected
         */



        @RequiresApi(Build.VERSION_CODES.P)
        override fun onDeviceConnected(device: BluetoothDevice) {
            super.onDeviceConnected(device)
            Timber.d("onDeviceConnected ${device.name}")
            notify(getString(R.string.connected)+" ${device.name}", false, SERVICE_ID)
            deviceName = device.name
            ConnectionReceiver().notifyStatus(true)

        }

        override fun onDeviceReady(device: BluetoothDevice) {
            super.onDeviceReady(device)
            Timber.d("FG - Device ready ${device.name}")
            connected = true

            deviceName = device.name


        }


        /**
         * Called when the Android device started connecting to given device.
         * The [.onDeviceConnected] will be called when the device is connected,
         * or [.onError] in case of error.
         * @param device the device that got connected
         */
        override fun onDeviceConnecting(device: BluetoothDevice) {
            super.onDeviceConnecting(device)
            connected = false
            Timber.d("Connecting to ${if (device.name.isNullOrEmpty()) "device" else device.name}")
            notify(getString(R.string.connecting)+" ${if (device.name.isNullOrEmpty()) "device" else device.name}", false, SERVICE_ID)
        }

        /**
         * Called when user initialized disconnection.
         * @param device the device that gets disconnecting
         */
        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            super.onDeviceDisconnecting(device)
            connected = false
            Timber.d("Disconnecting from ${device.name}")
            notify(getString(R.string.disconnecting)+" ${device.name}", false, SERVICE_ID)
            ConnectionReceiver().notifyStatus(false)
        }

        /**
         * Called when the device has disconnected (when the callback returned
         * [BluetoothGattCallback.onConnectionStateChange()] with state DISCONNECTED),
         * but ONLY if the [BleManager.shouldAutoConnect] method returned false for this device when it was connecting.
         * Otherwise the [.onLinkLossOccur] method will be called instead.
         * @param device the device that got disconnected
         */
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onDeviceDisconnected(device: BluetoothDevice) {
            super.onDeviceDisconnected(device)
            connected = false
            Timber.d("Disconnected from ${device.name}")
            notify(getString(R.string.disconnected)+" ${device.name}", true, SERVICE_ID)
            ConnectionReceiver().notifyStatus(false)
        }

        /**
         * This callback is invoked when the Ble Manager lost connection to a device that has been connected
         * with autoConnect option (see [BleManager.shouldAutoConnect].
         * Otherwise a [.onDeviceDisconnected] method will be called on such event.
         * @param device the device that got disconnected due to a link loss
         */
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onLinkLossOccurred(device: BluetoothDevice) {
            super.onLinkLossOccurred(device)
            connected = false
            Timber.d("Lost link to ${device.name}")
            notify(getString(R.string.loss_link)+" ${device.name}", true, SERVICE_ID)
            ConnectionReceiver().notifyStatus(false)
        }


        override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
            super.onError(device, message, errorCode)
            Timber.e("Error: $errorCode, Device:${device.name}, Message: $message")
            //notify("Error:$errorCode on ${device.name}", notify!=0, SERVICE_ID)
            connected = false

            stopSelf(startID)
        }

    }






    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action?.equals(BluetoothAdapter.ACTION_STATE_CHANGED) == true) {
                Timber.d("Bluetooth adapter changed in receiver")
                Timber.d("BT adapter state: ${intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)}")
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> {
                        // 2018/01/03 connect to remote
                        val remoteMacAddress = PreferenceManager.getDefaultSharedPreferences(context)
                            .getString(MN.PREF_KEY_REMOTE_MAC_ADDRESS, VESPA_DEVICE_ADDRESS)
                        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                        val leDevice = bluetoothManager.adapter.getRemoteDevice(remoteMacAddress)

                        bleManager = LEManager(context)
                        bleManager?.setGattCallbacks(bleManagerCallback)
                        bleManager?.connect(leDevice)?.enqueue()

                        Timber.d("Bluetooth STATE ON")
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        // 2018/01/03 close connections
                        notify(getString(R.string.disconnected)+" Smart Watch", notify!=0, SERVICE_ID)

                        bleManager?.disconnect()
                        bleManager?.close()
                        Timber.d("Bluetooth TURNING OFF")


                    }
                }
            }
        }
    }




    @RequiresApi(Build.VERSION_CODES.P)
    override fun onDataReceived(data: Data) {

        if (data.getByte(0) == (0xF1).toByte()) {
            val next = ((data.getByte(1)!!.toPInt()) * 256) + (data.getByte(2)!!.toPInt())
            sendData(context, next)
        }
        if (data.getByte(0) == (0xF2).toByte()) {
            Timber.w("Transfer complete")
            Toast.makeText(context, "Transfer Complete", Toast.LENGTH_SHORT).show()
            notifyProgress("Finishing up", 100, context)
            Handler().postDelayed({
                sendData(byteArrayOfInts(0xFE)) // send restart command
                cancelNotification(SERVICE_ID2, context)
            }, 2000)
        }

        MN().onDataReceived(data)

    }




    private fun onProgress(progress: Int, context: Context) {
        var txt = context.getString(R.string.send_data)
        if (MN.showNotif) {
            notifyProgress(txt, progress, context)
            if (progress == 100) {
                txt = context.getString(R.string.transfer_complete)
                notifyProgress(txt, progress, context)
            }
        } else {
            cancelNotification(SERVICE_ID2, context)
        }
        ProgressReceiver().getProgress(progress, txt)
    }


    @Throws(IOException::class)
    fun sendData(context: Context, pos: Int) {
        val dir = File(context.cacheDir, "data")
        val data = File(dir, "data$pos.bin").readBytes()
        val s = MN.mtu
        val total = data.size / s

        for (x in 0 until total) {
            val arr = ByteArray(s + 2)
            arr[0] = (0xFB).toByte()
            arr[1] = x.toByte()
            for (y in 0 until s) {
                arr[y + 2] = data[(x * s) + y]
            }
            sendData(arr)
        }

        if (data.size % s != 0) {
            val arr = ByteArray((data.size % s) + 2)
            arr[0] = (0xFB).toByte()
            arr[1] = total.toByte()
            for (y in 0 until data.size % s) {
                arr[y + 2] = data[(total * s) + y]
            }
            sendData(arr)
        }

        val update = byteArrayOfInts(0xFC, data.size / 256, data.size % 256, pos / 256, pos % 256)
        val cur = ((pos.toFloat() / parts) * 100).toInt()
        transmitData(update, cur, context)
    }

}