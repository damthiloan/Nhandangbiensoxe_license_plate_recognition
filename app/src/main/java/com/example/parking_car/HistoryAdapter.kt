package com.example.parking_car

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.parking_car.R

class HistoryAdapter(private var list: List<HistoryItem>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvId: TextView = view.findViewById(R.id.tv_item_id)
        val tvPlate: TextView = view.findViewById(R.id.tv_item_plate)
        val tvEntry: TextView = view.findViewById(R.id.tv_item_entry)
        val tvExit: TextView = view.findViewById(R.id.tv_item_exit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Bạn cần tạo thêm file item_history.xml trong res/layout (xem bước 3)
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        // 1. Gán dữ liệu
        holder.tvId.text = (position + 1).toString()
        holder.tvPlate.text = item.licensePlate

        // Định dạng thời gian: Lấy Giờ:Phút Ngày/Tháng (Ví dụ: 10:46 10/01)
        holder.tvEntry.text = formatDateTime(item.entryTime)
        holder.tvExit.text = if (item.exitTime == "null") "--" else formatDateTime(item.exitTime)

        // 2. Xử lý Màu Nền và In Đậm cho xe đang ở trong bãi
        if (item.exitTime == "在場") {
            // Màu nền khi xe ĐANG trong bãi (Xanh nhạt hoặc vàng tùy bạn chọn)
            holder.itemView.setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))

            // In đậm toàn bộ chữ để dễ nhìn
            val bold = android.graphics.Typeface.BOLD
            holder.tvId.setTypeface(null, bold)
            holder.tvPlate.setTypeface(null, bold)
            holder.tvEntry.setTypeface(null, bold)
            holder.tvExit.setTypeface(null, bold)

            // Đổi màu chữ biển số sang xanh đậm cho nổi bật
            holder.tvPlate.setTextColor(android.graphics.Color.parseColor("#25427A"))
        } else {
            // Màu nền khi xe ĐÃ RA (Màu trắng hoặc trong suốt)
            holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

            // Chữ thường cho xe đã ra
            val normal = android.graphics.Typeface.NORMAL
            holder.tvId.setTypeface(null, normal)
            holder.tvPlate.setTypeface(null, normal)
            holder.tvEntry.setTypeface(null, normal)
            holder.tvExit.setTypeface(null, normal)

            // Màu chữ mặc định
            holder.tvPlate.setTextColor(android.graphics.Color.BLACK)
        }
    }

    // Hàm hỗ trợ định dạng thời gian cho gọn (Optional)
    private fun formatDateTime(rawDate: String): String {
        return try {
            // Tách chuỗi 2024-01-10T10:46:42.037Z
            val parts = rawDate.split("T")
            val date = parts[0].substring(5) // Lấy 01-10
            val time = parts[1].substring(0, 5) // Lấy 10:46
            "$time $date"
        } catch (e: Exception) {
            rawDate
        }
    }

    override fun getItemCount() = list.size

    fun getList(): List<HistoryItem> {
        return list
    }

    fun updateData(newList: List<HistoryItem>) {
        list = newList
        notifyDataSetChanged()
    }
}