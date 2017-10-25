package com.amar.NoteDirector.asynctasks

import android.content.Context
import android.os.AsyncTask
import com.amar.NoteDirector.extensions.getFilesFrom
import com.amar.NoteDirector.models.Medium
import java.util.*

class GetMediaAsynctask(val context: Context, val mPath: String, val isPickVideo: Boolean = false, val isPickImage: Boolean = false,
                        val showAll: Boolean, val callback: (media: ArrayList<Medium>) -> Unit) :
        AsyncTask<Void, Void, ArrayList<Medium>>() {

    override fun doInBackground(vararg params: Void): ArrayList<Medium> {
        val path = if (showAll) "" else mPath
        return context.getFilesFrom(path, isPickImage, isPickVideo)
    }

    override fun onPostExecute(media: ArrayList<Medium>) {
        super.onPostExecute(media)
        callback(media)
    }
}
