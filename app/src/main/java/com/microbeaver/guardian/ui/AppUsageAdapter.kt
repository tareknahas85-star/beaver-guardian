package com.microbeaver.guardian.ui

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
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
    private val onSetLimit: (AppRow) -> Unit,
    private val onToggleBlock: (AppRow, Boolean) -> Unit
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
        h.b.tvUsage.text = "${row.minutes} دقيقة اليوم"
        h.b.tvLimit.text = if (row.limit > 0) "الحد: ${row.limit} د/يوم" else "بدون حد"

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

        h.b.switchBlock.setOnCheckedChangeListener(null)
        h.b.switchBlock.isChecked = row.blocked
        h.b.switchBlock.setOnCheckedChangeListener { _, checked -> onToggleBlock(row, checked) }

        h.b.btnLimit.setOnClickListener { onSetLimit(row) }
    }
}
