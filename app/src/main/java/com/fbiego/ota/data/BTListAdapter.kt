package com.fbiego.ota.data

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.fbiego.ota.R

class BtListAdapter(private val activity: Activity, private val name: Array<String>, private val address: Array<String>, private val mac: String)
    : ArrayAdapter<String>(activity, R.layout.bt_item, name) {

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = activity.layoutInflater
        val rowView = inflater.inflate(R.layout.bt_item, null, true)
        val nameR = rowView.findViewById<TextView>(R.id.btName)
        val addressR = rowView.findViewById<TextView>(R.id.btAddress)
        val iconR = rowView.findViewById<ImageView>(R.id.btIcon)

        nameR.text = name[position]
        addressR.text = address[position]
        if (mac == address[position]) {
            iconR.imageTintList = ColorStateList.valueOf(Color.BLUE)
        } else {
            iconR.imageTintList = ColorStateList.valueOf(Color.GRAY)
        }


        return rowView
    }
}
