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

package com.fbiego.ota

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.ParcelUuid
import android.provider.MediaStore
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.net.toFile
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fbiego.ota.app.*
import com.fbiego.ota.ble.LEManager
import com.fbiego.ota.data.BtDevice
import com.fbiego.ota.data.BtListAdapter
import kotlinx.android.synthetic.main.activity_main.*
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.io.*
import java.util.*
import kotlin.collections.ArrayList
import com.fbiego.ota.app.ForegroundService as FG


class MainActivity : AppCompatActivity(), ConnectionListener, ProgressListener {
    private lateinit var menu: Menu
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var setPref: SharedPreferences

    private var deviceList = ArrayList<BtDevice>()
    private var deviceAdapter = BtListAdapter(
            deviceList,
            deviceAddress,
            this@MainActivity::selectedDevice
    )
    private var mScanning: Boolean = false
    private lateinit var alertDialog: AlertDialog

    private val bluetoothAdapter: BluetoothAdapter by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    lateinit var textView: TextView
    lateinit var cardUpload: CardView
    lateinit var progress: ProgressBar
    lateinit var button: Button



    companion object {
        const val PREF_KEY_REMOTE_MAC_ADDRESS = "pref_remote_mac_address"
        const val PREF_CURRENT_FILE = "pref_file_name"
        const val PREF_MTU = "pref_mtu"
        lateinit var btAdapter: BluetoothAdapter
        const val BLUETOOTH = 37
        const val STORAGE = 20
        const val FILE_PICK = 56
        const val UPDATE_FILE = "update.bin"
        private const val FINE_LOCATION_PERMISSION_REQUEST= 1001
        const val BACKGROUND_LOCATION = 67
        const val PART = 16384
        var mtu = 500

        var showNotif = false
        var deviceAddress = ""

        lateinit var deviceRecycler: RecyclerView

        var start = 0L
        var startOta = 0L
        var timeTr = 0L
        var timeOta = 0L


        var total = 0
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        ProgressReceiver.bindListener(this)
        ConnectionReceiver.bindListener(this)
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        setPref = PreferenceManager.getDefaultSharedPreferences(this)
        deviceAddress = setPref.getString(PREF_KEY_REMOTE_MAC_ADDRESS, FG.VESPA_DEVICE_ADDRESS).toString()

        textView = findViewById(R.id.watchName)
        cardUpload = findViewById(R.id.buttonUpload)
        mtu = setPref.getInt(PREF_MTU, mtu)

    }

    override fun onStart() {
        super.onStart()

        Timber.d("MainActivity on start")
        deviceAddress = setPref.getString(
                PREF_KEY_REMOTE_MAC_ADDRESS,
                FG.VESPA_DEVICE_ADDRESS
        ).toString()
        if (btAdapter.isEnabled){

            if (deviceAddress!= FG.VESPA_DEVICE_ADDRESS){
                startService(Intent(this, FG::class.java))

            } else {
                Toast.makeText(this, "Select a device first", Toast.LENGTH_SHORT).show()
            }

        }

        Timber.w("onStart")
    }


    override fun onResume() {
        super.onResume()

        val directory = this.cacheDir
        val img = File(directory, UPDATE_FILE)
        val info = File(directory, "info.txt")
        val name = if (img.exists()){
            buttonUpload.visibility = View.VISIBLE
            if (info.exists() && info.canRead()){
                info.readText()
            } else {
                "no file"
            }
        } else {
            buttonUpload.visibility = View.INVISIBLE
            "no file"
        }
        textProgress.text = name
        showNotif = false
    }

    override fun onPause() {
        super.onPause()
        showNotif = true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        this.menu = menu
        return super.onCreateOptionsMenu(menu)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_prefs -> {

                Toast.makeText(this, R.string.stop_service, Toast.LENGTH_SHORT).show()
                stopService(Intent(this, FG::class.java))

                val inflater = layoutInflater
                val layout = inflater.inflate(R.layout.search_dialog, null)
                deviceRecycler = layout.findViewById(R.id.devicesView)
                button = layout.findViewById(R.id.search)
                progress = layout.findViewById(R.id.progressBar)
                textView = layout.findViewById(R.id.textView)
                progress.isIndeterminate = true
                button.setOnClickListener {
                    deviceList.clear()
                    updateDevices()
                    scanLeDevice(true)
                }
                deviceRecycler.layoutManager = LinearLayoutManager(this)
                val div = DividerItemDecoration(
                        deviceRecycler.context,
                        LinearLayoutManager.VERTICAL
                )
                deviceRecycler.addItemDecoration(div)
                deviceRecycler.isNestedScrollingEnabled = false
                deviceRecycler.apply {
                    layoutManager = LinearLayoutManager(this@MainActivity)
                    adapter = deviceAdapter
                }
                deviceRecycler.itemAnimator?.changeDuration = 0

                if (!checkLocation()){
                    requestLocation()
                } else {
                    if (!checkFINE()){
                        requestBackground()
                    } else {
                        if (bluetoothAdapter.isEnabled) {
                            scanLeDevice(true) //make sure scan function won't be called several times
                        }
                    }
                }

                val dialog = AlertDialog.Builder(this)

                //dialog.setTitle("Scan")
                //dialog.setMessage("Searching")
                dialog.setView(layout)

                dialog.setOnDismissListener {
                    Timber.e("Dialog dismissed")
                }

                alertDialog = dialog.create()
                alertDialog.show()
                true
            }
            R.id.menu_item_kill -> {
                ConnectionReceiver().notifyStatus(false)
                Toast.makeText(this, R.string.stop_service, Toast.LENGTH_SHORT).show()
                stopService(Intent(this, FG::class.java))
                true
            }
            R.id.menu_item_start -> {

                val remoteMacAddress = setPref.getString(
                        PREF_KEY_REMOTE_MAC_ADDRESS,
                        FG.VESPA_DEVICE_ADDRESS
                )
                if (btAdapter.isEnabled) {
                    if (remoteMacAddress != FG.VESPA_DEVICE_ADDRESS) {

                        Toast.makeText(this, R.string.start_service, Toast.LENGTH_SHORT).show()
                        startService(Intent(this, FG::class.java))
                    } else {
                        Toast.makeText(this, "Select a device first", Toast.LENGTH_SHORT).show()
                    }

                } else {
                    //enable bt
                    val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBluetoothIntent, BLUETOOTH)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            FINE_LOCATION_PERMISSION_REQUEST -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    scanLeDevice(true)
                } else {
                    //tvTestNote.text= getString(R.string.allow_location_detection)
                }
                return
            }
            STORAGE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
                    chooseFile.type = "*/*"
                    chooseFile = Intent.createChooser(chooseFile, "Choose a file")
                    startActivityForResult(chooseFile, 20)
                } else {
                    //tvTestNote.text= getString(R.string.allow_location_detection)
                }
                return
            }
        }
    }

    private fun checkLocation(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return true
    }

    private fun requestLocation(){
        ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                FINE_LOCATION_PERMISSION_REQUEST
        )
    }

    private fun scanLeDevice(enable: Boolean) {
        when (enable) {
            true -> {
                // Stops scanning after a pre-defined scan period.
                Handler().postDelayed({
                    mScanning = false
                    progress.isIndeterminate = false
                    button.visibility = View.VISIBLE
                    bluetoothAdapter.bluetoothLeScanner?.stopScan(mLeScanCallback)
                }, 10000)
                mScanning = true
                progress.isIndeterminate = true
                button.visibility = View.GONE
                textView.text = "Searching for devices"
                val filter =
                        ScanFilter.Builder().setServiceUuid(ParcelUuid(LEManager.OTA_SERVICE_UUID))
                                .build()
                val filters = mutableListOf<ScanFilter>(filter)
                val settings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setReportDelay(0)
                        .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                    bluetoothAdapter.bluetoothLeScanner?.startScan(
                        mLeScanCallback
                    )
                } else {
                    bluetoothAdapter.bluetoothLeScanner?.startScan(
                        filters,
                        settings,
                        mLeScanCallback
                    )
                }
            }
            else -> {
                mScanning = false
                progress.isIndeterminate = false
                button.visibility = View.VISIBLE
                bluetoothAdapter.bluetoothLeScanner?.stopScan(mLeScanCallback)
            }
        }

    }

    fun updateDevices(){
        textView.text = if (deviceList.isNotEmpty()){
            "Found ${deviceList.size} device${if (deviceList.size > 1) "s" else ""}"
        } else {
            "No device found"
        }
        deviceAdapter.update(deviceList, deviceAddress)
    }

    private fun selectedDevice(device: BtDevice){
        scanLeDevice(false)
        deviceAddress = device.address
        setPref.edit().putString(PREF_KEY_REMOTE_MAC_ADDRESS, deviceAddress).apply()
        Timber.e("Selected: ${device.name}, address: ${device.address}, device: address")
        alertDialog.dismiss()
        if (!isBonded(device)){
            Timber.e("bonding device")
            createBond(device)
        } else {
            Timber.e("Already bonded")
        }
        Handler().postDelayed({
            startService(Intent(this, FG::class.java))
        }, 5000
        )

    }

    private fun createBond(btDev: BtDevice){
        bluetoothAdapter.getRemoteDevice(btDev.address).createBond()
    }

    private fun isBonded(btDev: BtDevice): Boolean{
        for (dev in bluetoothAdapter.bondedDevices){
            if (dev.address == btDev.address){
                return true
            }
        }
        return false
    }

    private var mLeScanCallback = object : ScanCallback(){
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            Timber.e("Callback: $callbackType, Result ${result?.device?.name}")
            val me: BtDevice? = deviceList.singleOrNull {
                it.address == result?.device?.address
            }
            if (me == null && result?.device?.name != null){
                deviceList.add(BtDevice(result.device.name, result.device.address, false))
            }
            updateDevices()
        }

        override fun onBatchScanResults(results: MutableList<ScanResult?>?) {
            super.onBatchScanResults(results)
            Timber.e("Scan results: $results")
            for (result in results!!){
                val me: BtDevice? = deviceList.singleOrNull {
                    it.address == result?.device?.address
                }
                if (me == null && result?.device?.name != null){
                    deviceList.add(BtDevice(result.device?.name!!, result.device.address, false))
                }
            }
            updateDevices()
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Timber.e("Scan Fail: error $errorCode")
        }

    }

    fun onClick(view: View){

        when (view.id){
            R.id.choose -> {
                if (checkExternal()) {
                    var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
                    chooseFile.type = "*/*"
                    chooseFile = Intent.createChooser(chooseFile, "Choose a file")
                    startActivityForResult(chooseFile, FILE_PICK)
                } else {
                    requestExternal()
                }
            }

            R.id.buttonUpload -> {
                startOta = System.currentTimeMillis()
                clearData()
                val parts = generate()
                FG.parts = parts
                if (FG().sendData(byteArrayOfInts(0xFD))) {
                    Toast.makeText(this, "Uploading file", Toast.LENGTH_SHORT).show()
                    buttonUpload.visibility = View.INVISIBLE
                    FG().sendData(
                            byteArrayOfInts(
                                    0xFF,
                                    parts / 256,
                                    parts % 256,
                                    mtu / 256,
                                    mtu % 256
                            )
                    )
                    //FG().sendData(this, 0)
                    start = System.currentTimeMillis()
                    //
                } else {
                    Toast.makeText(this, R.string.not_connect, Toast.LENGTH_SHORT).show()
                }
            }

            R.id.cardInfo -> {
                //FG().sendData(byteArrayOfInts(0xFD))
            }
            R.id.cardView -> {  // with progress
                //FG().sendData(byteArrayOfInts(0xFE))


            }
        }

    }

    private fun checkExternal(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return true
    }

    private fun checkFINE(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestBackground(){
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            BACKGROUND_LOCATION
        )
    }

    private fun requestExternal(){
        ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE
        )
    }

    @Throws(IOException::class)
    fun saveFile(src: File?, uri: Uri?) {

        val directory = this.cacheDir
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val dst = File(directory, UPDATE_FILE)
        if (dst.exists()){
            dst.delete()

        }
        if (src != null) {
            val info = File(directory, "info.txt")
            info.writeText(src.name)

            FileInputStream(src).use { `in` ->
                FileOutputStream(dst).use { out ->
                    // Transfer bytes from in to out
                    val buf = ByteArray(1024)
                    var len: Int
                    while (`in`.read(buf).also { len = it } > 0) {
                        out.write(buf, 0, len)
                    }
                }
            }
            val fos = FileOutputStream(dst, true)
            fos.flush()
            fos.close()
        }
        if (uri != null){
            val info = File(directory, "info.txt")
            info.writeText("firmware.bin")

            contentResolver.openInputStream(uri).use { `in` ->
                FileOutputStream(dst).use { out ->
                    // Transfer bytes from in to out
                    val buf = ByteArray(1024)
                    var len: Int
                    while (`in`!!.read(buf).also { len = it } > 0) {
                        out.write(buf, 0, len)
                    }
                }
            }
            val fos = FileOutputStream(dst, true)
            fos.flush()
            fos.close()
        }
    }

    @Throws(IOException::class)
    fun clearData() {
        val directory = this.cacheDir
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val upload = File(directory, "data")
        if (upload.exists()) {
            upload.deleteRecursively()
        }
    }

    @Throws(IOException::class)
    fun saveData(byteArray: ByteArray, pos: Int) {
        val directory = this.cacheDir
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val upload = File(directory, "data")
        if (!upload.exists()) {
            upload.mkdirs()
        }
        val data = File(upload, "data$pos.bin")
        val fos = FileOutputStream(data, true)
        fos.write(byteArray)
        fos.flush()
        fos.close()

    }

    @Throws(IOException::class)
    fun generate(): Int {
        val bytes = File(this.cacheDir, "update.bin").readBytes()
        val s = bytes.size / PART

        for (x in 0 until s) {
            val data = ByteArray(PART)
            for (y in 0 until PART) {
                data[y] = bytes[(x * PART) + y]
            }
            saveData(data, x)

        }
        if (bytes.size % PART != 0) {
            val data = ByteArray(bytes.size % PART)
            for (y in 0 until bytes.size % PART) {
                data[y] = bytes[(s * PART) + y]
            }
            saveData(data, s)
        }
        return if (bytes.size % PART == 0) {
            (bytes.size / PART)
        } else {
            (bytes.size / PART) + 1
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Timber.e("RequestCode= $requestCode, ResultCode= $resultCode, Data= ${data != null}")
        if (resultCode == Activity.RESULT_OK) {
            if (data != null && requestCode == FILE_PICK) {

                val selectedFile = data.data
                val filePathColumn = arrayOf(MediaStore.Files.FileColumns.DATA)
                if (selectedFile != null) {
                    val cursor =
                        contentResolver.query(selectedFile, filePathColumn, null, null, null)
                    if (cursor != null) {
                        cursor.moveToFirst()
                        val columnIndex = cursor.getColumnIndex(filePathColumn[0])

                        //Timber.e(filePathColumn.contentDeepToString())
                        //Timber.e("index = $columnIndex")
                        val filePath = cursor.getString(columnIndex)

                        //val f = File(filePath)
                        cursor.close()

                        //saveFile(f)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
                            saveFile(File(filePath), null)
                        } else {
                            saveFile(null, selectedFile)
                        }
                    }
                }
            }
        }
    }

    fun onDataReceived(data: Data) {
        Timber.e("${data.getByte(0)}")
        if (data.getByte(0) == (0xFA).toByte()) {
            val ver =
                    String.format("v%01d.%02d", data.getByte(1)!!.toPInt(), data.getByte(2)!!.toPInt())
            textView.text = "${FG.deviceName}\t$ver"
        }
    }

    override fun onProgress(progress: Int, text: String) {
        runOnUiThread {

            progressUpload.isIndeterminate = false
            textProgress.text = text
            progressUpload.progress = progress
            percentProgress.text = "$progress%"
            if (progress == 100) {
                buttonUpload.visibility = View.VISIBLE
                progressUpload.isIndeterminate= true
                timeTr = System.currentTimeMillis() - start
                textProgress.text = "Transfer complete in ${timeString(timeTr)}\nInstalling..."
            }
            if (progress == 101){
                timeOta = System.currentTimeMillis() - startOta
                cardUpload.visibility = View.VISIBLE
                percentProgress.text = ""
                textProgress.text = text + "\nFile transfer time: ${timeString(timeTr)}\nTotal OTA time: ${timeString(timeOta)}"
            }
        }
    }

    override fun onConnectionChanged(state: Boolean) {
        runOnUiThread {
            FG.connected = state
            setIcon(FG.connected)
            if (FG.connected) {
                watchName.text = FG.deviceName
                textProgress.text = ""
                progressUpload.progress = 0
                percentProgress.text = ""
            }
        }
    }

    private fun setIcon(state: Boolean){
        if (state){
            connect.setImageResource(R.drawable.ic_bt)
            connect.imageTintList = ColorStateList.valueOf(Color.BLUE)
        } else {
            connect.setImageResource(R.drawable.ic_disc)
            connect.imageTintList = ColorStateList.valueOf(Color.DKGRAY)
        }
    }
}