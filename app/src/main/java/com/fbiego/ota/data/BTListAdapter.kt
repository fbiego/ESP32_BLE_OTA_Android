package com.fbiego.ota.data
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.fbiego.ota.R

class BtListAdapter(private val devices: ArrayList<BtDevice>, private val mac: String, private val listener: (BtDevice) -> Unit)
    : RecyclerView.Adapter<BtListAdapter.DeviceHolder>() {

    private val data = mutableListOf<BtDevice>()
    private var address = ""

    init {
        address = mac
        data.addAll(devices)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.bt_item, parent, false)
        return DeviceHolder(view)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: DeviceHolder, position: Int) {
        holder.bind(data[position], address, listener)
    }

    fun update(new: ArrayList<BtDevice>, mac: String){
        val diffCallback = DeviceDiffCallback(this.data, new)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.address = mac
        this.data.clear()
        this.data.addAll(new)
        diffResult.dispatchUpdatesTo(this)
    }



    class DeviceHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        private val mName: TextView = itemView.findViewById(R.id.btName)
        private val mAddress: TextView = itemView.findViewById(R.id.btAddress)
        private val mIcon: ImageView = itemView.findViewById(R.id.btIcon)

        fun bind(device: BtDevice, mac: String, listener: (BtDevice) -> Unit){
            mName.text = device.name
            mAddress.text = device.address
            if (mac == device.address){
                mIcon.imageTintList = ColorStateList.valueOf(Color.BLUE)
            } else {
                mIcon.imageTintList = ColorStateList.valueOf(Color.GRAY)
            }
            itemView.setOnClickListener {
                listener(device)
            }
        }

    }
}

