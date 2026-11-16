package com.automotive.swiftlauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class AppAdapter(
    private var apps: List<AppInfo>,
    private val onItemClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private var filteredApps: List<AppInfo> = apps

    fun updateData(newApps: List<AppInfo>) {
        this.apps = newApps
        this.filteredApps = newApps
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val trimmed = query.trim().lowercase(Locale.getDefault())
        filteredApps = if (trimmed.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.lowercase(Locale.getDefault()).contains(trimmed) ||
                it.packageName.lowercase(Locale.getDefault()).contains(trimmed)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_grid, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(filteredApps[position])
    }

    override fun getItemCount(): Int = filteredApps.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgIcon: ImageView = itemView.findViewById(R.id.imgAppIcon)
        private val textName: TextView = itemView.findViewById(R.id.textAppName)

        fun bind(app: AppInfo) {
            textName.text = app.label
            if (app.icon != null) {
                imgIcon.setImageDrawable(app.icon)
            } else {
                imgIcon.setImageResource(R.drawable.ic_launcher_car)
            }
            itemView.setOnClickListener {
                onItemClick(app)
            }
        }
    }
}
