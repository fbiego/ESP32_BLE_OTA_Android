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
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.fbiego.ota.app.*
import com.fbiego.ota.data.BtListAdapter
import kotlinx.android.synthetic.main.activity_main.*
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import com.fbiego.ota.app.ForegroundService as FG


class MainActivity : AppCompatActivity(), ConnectionListener, ProgressListener {
    private lateinit var menu: Menu
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var setPref: SharedPreferences


    companion object {
        const val PREF_KEY_REMOTE_MAC_ADDRESS = "pref_remote_mac_address"
        const val PREF_CURRENT_FILE = "pref_file_name"
        const val PREF_MTU = "pref_mtu"
        lateinit var btAdapter: BluetoothAdapter
        const val BLUETOOTH = 37
        const val STORAGE = 20
        const val FILE_PICK = 56
        const val UPDATE_FILE = "update.bin"
        const val PART = 16384
        var mtu = 98

        var showNotif = false

        var textView: TextView? = null
        var cardUpload: CardView? = null
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

        textView = findViewById(R.id.watchName)
        cardUpload = findViewById(R.id.buttonUpload)
        mtu = setPref.getInt(PREF_MTU, mtu)

    }

    override fun onStart() {
        super.onStart()

        Timber.d("MainActivity on start")
        val remoteMacAddress = setPref.getString(
            PREF_KEY_REMOTE_MAC_ADDRESS,
            FG.VESPA_DEVICE_ADDRESS
        )
        if (btAdapter.isEnabled){

            if (remoteMacAddress != FG.VESPA_DEVICE_ADDRESS){
                startService(Intent(this, FG::class.java))

            } else {
                Toast.makeText(this, "Setup mac address first", Toast.LENGTH_SHORT).show()
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_prefs -> {

                Toast.makeText(this, R.string.stop_service, Toast.LENGTH_SHORT).show()
                stopService(Intent(this, FG::class.java))
                val mac = setPref.getString(
                    PREF_KEY_REMOTE_MAC_ADDRESS,
                    FG.VESPA_DEVICE_ADDRESS
                )
                editor = setPref.edit()
                val alert = AlertDialog.Builder(this)
                var alertDialog: AlertDialog? = null
                alert.setTitle(R.string.mac_addr)
                val devs: String
                val btNames = ArrayList<String>()
                val btAddress = ArrayList<String>()
                if (btAdapter.isEnabled) {
                    devs = getString(R.string.not_paired)
                    val devices: Set<BluetoothDevice> = btAdapter.bondedDevices
                    for (device in devices) {
                        if (device.name.contains("ESP")) {
                            btNames.add(device.name)
                            btAddress.add(device.address)
                        }
                    }

                } else {
                    devs = getString(R.string.turn_on_bt)
                }
                alert.setMessage(devs)
                val layout = LinearLayout(this)
                layout.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                val listView = ListView(this)
                val myBTlist = BtListAdapter(
                    this,
                    btNames.toTypedArray(),
                    btAddress.toTypedArray(),
                    mac!!
                )
                listView.adapter = myBTlist

                listView.setOnItemClickListener { _, _, j, _ ->
                    editor.putString(PREF_KEY_REMOTE_MAC_ADDRESS, btAddress[j])
                    editor.apply()
                    editor.commit()
                    alertDialog?.dismiss()
                }
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(20, 20, 20, 20)
                layout.addView(listView, params)
                alert.setView(layout)
                alert.setPositiveButton(R.string.bt_settings) { _, _ ->
                    val intentOpenBluetoothSettings = Intent()
                    intentOpenBluetoothSettings.action = Settings.ACTION_BLUETOOTH_SETTINGS
                    startActivity(intentOpenBluetoothSettings)
                }
                alert.setNegativeButton(R.string.cancel) { _, _ ->

                }
                alertDialog = alert.create()
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
                        Toast.makeText(this, "Setup mac address first", Toast.LENGTH_SHORT).show()
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
                    FG().sendData(this, 0)
                } else {
                    Toast.makeText(this, R.string.not_connect, Toast.LENGTH_SHORT).show()
                }
            }

            R.id.cardInfo -> {
                //FG().sendData(byteArrayOfInts(0xFD))
            }
            R.id.cardView -> {
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

    private fun requestExternal(){
        ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE
        )
    }

    @Throws(IOException::class)
    fun saveFile(src: File) {

        val directory = this.cacheDir
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val dst = File(directory, UPDATE_FILE)
        if (dst.exists()){
            dst.delete()

        }
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
                        val filePath = cursor.getString(columnIndex)

                        val f = File(filePath)
                        cursor.close()

                        saveFile(f)
                    }
                }
            }

            if (requestCode == STORAGE){
                var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
                chooseFile.type = "*/*"
                chooseFile = Intent.createChooser(chooseFile, "Choose a file")
                startActivityForResult(chooseFile, 20)
            }
        }
    }

    fun onDataReceived(data: Data) {
        Timber.e("${data.getByte(0)}")
        if (data.getByte(0) == (0xFA).toByte()) {
            val ver =
                    String.format("v%01d.%02d", data.getByte(1)!!.toPInt(), data.getByte(2)!!.toPInt())
            textView?.text = "${FG.deviceName}\t$ver"
        }
        if (data.getByte(0) == (0xF2).toByte()) {
            cardUpload?.visibility = View.VISIBLE
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