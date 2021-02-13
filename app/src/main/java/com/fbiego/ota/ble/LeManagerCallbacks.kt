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

import android.bluetooth.BluetoothDevice
import no.nordicsemi.android.ble.BleManagerCallbacks
import timber.log.Timber

/**
 * Implements the BLEManager callback methods
 */
open class LeManagerCallbacks : BleManagerCallbacks {

    /**
     * Called when the Android device started connecting to given device.
     * The [.onDeviceConnected] will be called when the device is connected,
     * or [.onError] in case of error.
     * @param device the device that got connected
     */
    override fun onDeviceConnecting(device: BluetoothDevice) {
        Timber.d("onDeviceConnecting {address=${device.address},name=${device.name}}")
    }

    /**
     * Called when the device has been connected. This does not mean that the application may start communication.
     * A service discovery will be handled automatically after this call. Service discovery
     * may ends up with calling [.onServicesDiscovered] or
     * [.onDeviceNotSupported] if required services have not been found.
     * @param device the device that got connected
     */
    override fun onDeviceConnected(device: BluetoothDevice) {

        Timber.d("onDeviceConnected {address=${device.address},name=${device.name}}")
    }

    /**
     * Called when user initialized disconnection.
     * @param device the device that gets disconnecting
     */
    override fun onDeviceDisconnecting(device: BluetoothDevice) {
        Timber.d("onDeviceDisconnecting {address=${device.address},name=${device.name}}")
    }

    /**
     * Called when the device has disconnected (when the callback returned
     * [BluetoothGattCallback.onConnectionStateChange] with state DISCONNECTED),
     * but ONLY if the [BleManager.shouldAutoConnect] method returned false for this device when it was connecting.
     * Otherwise the [.onLinklossOccur] method will be called instead.
     * @param device the device that got disconnected
     */
    override fun onDeviceDisconnected(device: BluetoothDevice) {
        Timber.d("onDeviceDisconnected {address=${device.address},name=${device.name}}")
    }

    /**
     * This callback is invoked when the Ble Manager lost connection to a device that has been connected
     * with autoConnect option (see [BleManager.shouldAutoConnect].
     * Otherwise a [.onDeviceDisconnected] method will be called on such event.
     * @param device the device that got disconnected due to a link loss
     */
    override fun onLinkLossOccurred(device: BluetoothDevice) {
        Timber.d("onLinklossOccur {address=${device.address},name=${device.name}}")
    }

    /**
     * Called when service discovery has finished and primary services has been found.
     * This method is not called if the primary, mandatory services were not found during service discovery.
     * For example in the Blood Pressure Monitor, a Blood Pressure service is a primary service and
     * Intermediate Cuff Pressure service is a optional secondary service.
     * Existence of battery service is not notified by this call.
     *
     * After successful service discovery the service will initialize all services.
     * The [.onDeviceReady] method will be called when the initialization is complete.
     *
     * @param device the device which services got disconnected
     * @param optionalServicesFound
     * if `true` the secondary services were also found on the device.
     */
    override fun onServicesDiscovered(device: BluetoothDevice, optionalServicesFound: Boolean) {
        Timber.d("onServiceDiscovered {address=${device.address},name=${device.name}}")
    }

    /**
     * Method called when all initialization requests has been completed.
     * @param device the device that get ready
     */
    override fun onDeviceReady(device: BluetoothDevice) {
        Timber.d("onDeviceReady {address=${device.address},name=${device.name}}")
    }

    /**
     * This method should return true if Battery Level notifications should be enabled on the target device.
     * If there is no Battery Service, or the Battery Level characteristic does not have NOTIFY property,
     * this method will not be called for this device.
     *
     * This method may return true only if an activity is bound to the service (to display the information
     * to the user), always (e.g. if critical battery level is reported using notifications) or never, if
     * such information is not important or the manager wants to control Battery Level notifications on its own.
     * @param device target device
     * @return true to enabled battery level notifications after connecting to the device, false otherwise
     */
    override fun shouldEnableBatteryLevelNotifications(device: BluetoothDevice): Boolean {
//        //To change body of created functions use File | Settings | File Templates.
        return false
    }

    /**
     * Called when battery value has been received from the device.
     *
     * @param value
     * the battery value in percent
     * @param device the device frm which the battery value has changed
     */
    override fun onBatteryValueReceived(device: BluetoothDevice, value: Int) {
        ////To change body of created functions use File | Settings | File Templates.
    }

    /**
     * Called when an [BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION] error occurred and the device bond state is NOT_BONDED
     * @param device the device that requires bonding
     */
    override fun onBondingRequired(device: BluetoothDevice) {
        //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * Called when the device has been successfully bonded.
     * @param device the device that got bonded
     */
    override fun onBonded(device: BluetoothDevice) {
        //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBondingFailed(device: BluetoothDevice) {
        //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * Called when a BLE error has occurred
     *
     * @param message
     * the error message
     * @param errorCode
     * the error code
     * @param device the device that caused an error
     */
    override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
        Timber.d("onError {address=${device.address},name=${device.name},msg=${message},err=$errorCode}")
    }

    /**
     * Called when service discovery has finished but the main services were not found on the device.
     * @param device the device that failed to connect due to lack of required services
     */
    override fun onDeviceNotSupported(device: BluetoothDevice) {
        Timber.d("onDeviceNotSupported {address=${device.address},name=${device.name}}")
    }

}