package com.mapbox.w3w.sample.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.w3w.sample.R
import com.mapbox.w3w.sample.ui.common.ResultsAdapter.DataViewHolder

class ResultsAdapter(private val entryList: ArrayList<SimpleAdapterResult>,
                        private val adapterOnClickListener: AdapterOnClickListener)
    : RecyclerView.Adapter<DataViewHolder>() {

    class DataViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(item: SimpleAdapterResult, adapterOnClickListener: AdapterOnClickListener) {
            itemView.apply {
                val textView: TextView = itemView.findViewById(R.id.text_name)
                textView.text = item.getDisplayTitle()

                val icon: ImageView = itemView.findViewById(R.id.image_Icon)
                when (item.type) {
                    ResultType.W3W_SUGGESTION -> icon.setImageResource(R.drawable.ic_w3w)
                    else -> {
                        icon.setImageResource(R.drawable.ic_location)
                    }
                }

                itemView.setOnClickListener {
                    adapterOnClickListener.onItemClicked(item)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataViewHolder =
        DataViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false))

    override fun getItemCount(): Int = entryList.size

    override fun onBindViewHolder(holder: DataViewHolder, position: Int) {
        holder.bind(entryList[position], adapterOnClickListener)
    }

    fun clear() {
        this.entryList.apply {
            clear()
        }
    }

    fun fillEntries(entries: List<SimpleAdapterResult>) {
        this.entryList.apply {
            clear()
            addAll(entries)
        }
    }

    fun addEntry(entry: SimpleAdapterResult) {
        this.entryList.apply {
            add(entry)
        }
    }
}