package com.microbeaver.guardian.ui

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.microbeaver.guardian.R
import com.microbeaver.guardian.databinding.ItemAppUsageBinding

data class AppRow(
    val pkg: String,
    val name: String,
    val iconB64: String,
    val minutes: Int,
    val limit: Int,
    val blocked: Boolean
)

class AppUsageAdapter(
    private val onEdit: (AppRow) -> Unit
) : RecyclerView.Adapter<AppUsageAdapter.VH>() {

    private val items = ArrayList<AppRow>()

    fun submit(list: List<AppRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemAppUsageBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemAppUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val row = items[position]
        h.b.tvName.text = row.name
        val limitText = if (row.limit > 0) " / ${fmt(row.limit)}" else " / Unlimited"
        h.b.tvUsage.text = "Used: ${fmt(row.minutes)}$limitText"

        if (row.blocked) {
            h.b.tvLimit.visibility = View.VISIBLE
            h.b.tvLimit.text = "محظور / Blocked"
        } else {
            h.b.tvLimit.visibility = View.GONE
        }

        if (row.iconB64.isNotEmpty()) {
            try {
                val bytes = Base64.decode(row.iconB64, Base64.NO_WRAP)
                h.b.imgIcon.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            } catch (_: Exception) {
                h.b.imgIcon.setImageResource(R.drawable.ic_launcher)
            }
        } else {
            h.b.imgIcon.setImageResource(R.drawable.ic_launcher)
        }

        h.b.btnLimit.setOnClickListener { onEdit(row) }
    }

    private fun fmt(min: Int): String {
        val h = min / 60
        val m = min % 60
        return if (h > 0) "${h}h ${String.format("%02d", m)}m" else "${m}m"
    }
}
