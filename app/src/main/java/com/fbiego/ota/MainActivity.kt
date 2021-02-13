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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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


class MainActivity : AppCompatActivity(), ConnectionListener, ProgressListener, DataListener {
    private lateinit var menu: Menu
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var setPref: SharedPreferences



    companion object {
        const val PREF_KEY_REMOTE_MAC_ADDRESS = "pref_remote_mac_address"
        const val PREF_CURRENT_FILE = "pref_file_name"
        lateinit var btAdapter: BluetoothAdapter
        const val BLUETOOTH = 37
        const val STORAGE = 20
        const val FILE_PICK = 56
        const val UPDATE_FILE = "update.bin"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        ProgressReceiver.bindListener(this)
        ConnectionReceiver.bindListener(this)
        DataReceiver.bindListener(this)
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        setPref =  PreferenceManager.getDefaultSharedPreferences(this)

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
        val name = if (img.exists()){
            buttonUpload.visibility = View.VISIBLE
            setPref.getString(PREF_CURRENT_FILE, "no file")
        } else {
            buttonUpload.visibility = View.GONE
            "no file"
        }
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
                FG().uploadFile(this)
            }

            R.id.cardInfo -> {
                FG().sendData(byteArrayOfInts(0xFD))
            }
            R.id.cardView -> {
                FG().sendData(byteArrayOfInts(0xFE))
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
            56
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
    

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Timber.e("RequestCode= $requestCode, ResultCode= $resultCode, Data= ${data != null}")
        if (resultCode == Activity.RESULT_OK){
            if (data != null && requestCode == FILE_PICK){

                val selectedFile = data.data
                val filePathColumn = arrayOf(MediaStore.Files.FileColumns.DATA)
                if (selectedFile != null) {
                    val cursor = contentResolver.query(selectedFile, filePathColumn, null, null, null)
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

    override fun onDataReceived(data: Data) {
        runOnUiThread {

        }
    }

    override fun onProgress(progress: Int, text: String) {
        runOnUiThread {

            progressUpload.isIndeterminate = false
            textProgress.text = text
            progressUpload.progress = progress
            percentProgress.text = "$progress%"
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