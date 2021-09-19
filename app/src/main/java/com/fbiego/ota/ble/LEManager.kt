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

package com.fbiego.ota.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import com.fbiego.ota.app.DataReceiver
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.BleManagerCallbacks
import timber.log.Timber
import java.util.*

/**
 * Implements BLEManager
 */
class LEManager(context: Context) : BleManager<LeManagerCallbacks>(context) {

    var otaRxCharacteristic: BluetoothGattCharacteristic? = null
    var otaTxCharacteristic: BluetoothGattCharacteristic? = null


    companion object {
        const val MTU = 512
        val OTA_SERVICE_UUID:      UUID = UUID.fromString("fb1e4001-54ae-4a28-9f74-dfccb248601d")
        val OTA_TX_CHARACTERISTIC: UUID = UUID.fromString("fb1e4002-54ae-4a28-9f74-dfccb248601d")
        val OTA_RX_CHARACTERISTIC: UUID = UUID.fromString("fb1e4003-54ae-4a28-9f74-dfccb248601d")

    }

    /**
     * This method must return the gatt callback used by the manager.
     * This method must not create a new gatt callback each time it is being invoked, but rather return a single object.
     *
     * @return the gatt callback object
     */
    override fun getGattCallback(): BleManagerGattCallback {
        return callback
    }

    /**
     * Write {@code message} to the remote device's characteristic
     */


    fun transmitData(fastMode: Boolean, bytes: ByteArray, progress: Int, context: Context, listener: (Int, Context) -> Unit): Boolean{
        return if (isConnected && isReady && otaTxCharacteristic != null){
            requestMtu(MTU).enqueue()
            val send = writeCharacteristic(otaTxCharacteristic, bytes)
                .with { device, data ->
                    Timber.d("Data sent to ${device.address} Data = ${data.size()}")
                }
                .fail { device, status ->
                    Timber.d("Failed to send data to ${device.name}, status = $status")
                }
                .done {
                    Timber.d("Data sent")
                    listener(progress, context)
                }
            if (fastMode){
                send.split().enqueue()
            } else {
                send.enqueue()
            }

            true
        } else {
            false
        }
    }



    fun writeBytes(fastMode: Boolean, bytes: ByteArray): Boolean {
        return if (isConnected && isReady && otaTxCharacteristic != null) {
            requestMtu(MTU).enqueue()
            val send = writeCharacteristic(otaTxCharacteristic, bytes)
            if (fastMode){
                send.split().enqueue()
            } else {
                send.enqueue()
            }
            true
        } else {
            false
        }
    }

    /**
     * Returns whether to connect to the remote device just once (false) or to add the address to white list of devices
     * that will be automatically connect as soon as they become available (true). In the latter case, if
     * Bluetooth adapter is enabled, Android scans periodically for devices from the white list and if a advertising packet
     * is received from such, it tries to connect to it. When the connection is lost, the system will keep trying to reconnect
     * to it in. If true is returned, and the connection to the device is lost the [BleManagerCallbacks.onLinkLossOccur()]
     * callback is called instead of [BleManagerCallbacks.onDeviceDisconnected].
     *
     * This feature works much better on newer Android phone models and many not work on older phones.
     *
     * This method should only be used with bonded devices, as otherwise the device may change it's address.
     * It will however work also with non-bonded devices with private static address. A connection attempt to
     * a device with private resolvable address will fail.
     *
     * The first connection to a device will always be created with autoConnect flag to false
     * (see [BluetoothDevice.connectGatt()]). This is to make it quick as the
     * user most probably waits for a quick response. However, if this method returned true during first connection and the link was lost,
     * the manager will try to reconnect to it using [BluetoothGatt.connect] which forces autoConnect to true .
     *
     * @return autoConnect flag value
     */
    override fun shouldAutoConnect(): Boolean {
        return true
    }

    /**
     * Implements GATTCallback methods
     */
    private val callback: BleManagerGattCallback = object : BleManagerGattCallback() {
        /**
         * This method should return `true` when the gatt device supports the required services.
         *
         * @param gatt the gatt device with services discovered
         * @return `true` when the device has the required service
         */
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val gattService: BluetoothGattService? = gatt.getService(OTA_SERVICE_UUID)
            if (otaTxCharacteristic == null) {
                gattService?.getCharacteristic(OTA_TX_CHARACTERISTIC)?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                otaTxCharacteristic = gattService?.getCharacteristic(OTA_TX_CHARACTERISTIC)
            }
            if (otaRxCharacteristic == null) {
                otaRxCharacteristic = gattService?.getCharacteristic(OTA_RX_CHARACTERISTIC)
            }

            Timber.w("Gatt service ${gattService != null}, RX ${otaRxCharacteristic != null}, TX ${otaTxCharacteristic != null}")

            return gattService != null
                    && otaRxCharacteristic != null
                    && otaTxCharacteristic != null
        }


        /**
         * This method should set up the request queue needed to initialize the profile.
         * Enabling Service Change indications for bonded devices is handled before executing this
         * queue. The queue may have requests that are not available, e.g. read an optional
         * service when it is not supported by the connected device. Such call will trigger
         * {@link Request#fail(FailCallback)}.
         * <p>
         * This method is called from the main thread when the services has been discovered and
         * the device is supported (has required service).
         * <p>
         * Remember to call {@link Request#enqueue()} for each request.
         * <p>
         * A sample initialization should look like this:
         * <pre>
         * &#64;Override
         * protected void initialize() {
         *    requestMtu(MTU)
         *       .with((device, mtu) -> {
         *           ...
         *       })
         *       .enqueue();
         *    setNotificationCallback(characteristic)
         *       .with((device, data) -> {
         *           ...
         *       });
         *    enableNotifications(characteristic)
         *       .done(device -> {
         *           ...
         *       })
         *       .fail((device, status) -> {
         *           ...
         *       })
         *       .enqueue();
         * }
         * </pre>
         */
        override fun initialize() {
            Timber.d("Initialising...")

            requestMtu(MTU).enqueue()

            setNotificationCallback(otaRxCharacteristic)
                .with { device, data ->
                    Timber.d("Data received from ${device.address} Data = ${data.size()}")

                    DataReceiver().getData(data)


                }
            enableNotifications(otaRxCharacteristic)
                .done {
                    Timber.d("Successfully enabled OTARxCharacteristic notifications")
                }
                .fail { _, _ ->
                    Timber.w("Failed to enable OTARxCharacteristic notifications")
                }
                .enqueue()
//            enableIndications(otaRxCharacteristic)
//                    .done {
//                        Timber.d("Successfully wrote message")
//                    }
//                    .fail { device, status ->
//                        Timber.w("Failed to write message to ${device.address} - status: $status")
//                    }
//                    .enqueue()


        }


        override fun onDeviceDisconnected() {
            otaRxCharacteristic = null
            otaTxCharacteristic = null
        }
    }

}