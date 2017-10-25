package com.amar.NoteDirector.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.amar.NoteDirector.asynctasks.GetDirectoriesAsynctask
import com.amar.NoteDirector.extensions.config

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GetDirectoriesAsynctask(context, false, false) {
            context.config.directories = Gson().toJson(it)
        }.execute()
    }
}
