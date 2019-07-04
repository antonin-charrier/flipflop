package com.ourakoz.flipflop

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_command.view.*

class CommandsAdapter(val commands: List<Command>, val context: Context) : RecyclerView.Adapter<ViewHolder>() {
    override fun getItemCount(): Int {
        return commands.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.commandName?.text = commands.elementAt(position).name
        when(commands.elementAt(position).type) {
            CommandType.PHONE.toString() -> holder.commandType?.setImageResource(R.drawable.phone)
            CommandType.SMS.toString() -> holder.commandType?.setImageResource(R.drawable.sms)
            CommandType.URL.toString() -> holder.commandType?.setImageResource(R.drawable.url)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_command, parent, false))
    }
}

class ViewHolder (view: View) : RecyclerView.ViewHolder(view) {
    val commandName = view.command_name
    val commandType = view.command_type
}
