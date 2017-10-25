package com.amar.NoteDirector.dialogs

import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.amar.NoteDirector.activities.SimpleActivity
import com.amar.NoteDirector.adapters.MediaAdapter
import com.amar.NoteDirector.asynctasks.GetMediaAsynctask
import com.amar.NoteDirector.extensions.config
import com.amar.NoteDirector.models.Medium
import kotlinx.android.synthetic.main.dialog_medium_picker.view.*

class PickMediumDialog(val activity: SimpleActivity, val path: String, val callback: (path: String) -> Unit) {
    var dialog: AlertDialog
    var mediaGrid: RecyclerView
    var shownMedia: ArrayList<Medium> = ArrayList()

    init {
        val view = LayoutInflater.from(activity).inflate(com.amar.NoteDirector.R.layout.dialog_medium_picker, null)
        mediaGrid = view.media_grid

        dialog = AlertDialog.Builder(activity)
                .setPositiveButton(com.amar.NoteDirector.R.string.ok, null)
                .setNegativeButton(com.amar.NoteDirector.R.string.cancel, null)
                .create().apply {
            activity.setupDialogStuff(view, this, com.amar.NoteDirector.R.string.select_photo)

            val token = object : TypeToken<List<Medium>>() {}.type
            val media = Gson().fromJson<ArrayList<Medium>>(activity.config.loadFolderMedia(path), token) ?: ArrayList<Medium>(1)

            if (media.isNotEmpty()) {
                gotMedia(media)
            }

            GetMediaAsynctask(activity, path, false, true, false) {
                gotMedia(it)
            }.execute()
        }
    }

    private fun gotMedia(media: ArrayList<Medium>) {
        if (media.hashCode() == shownMedia.hashCode())
            return

        shownMedia = media
        val adapter = MediaAdapter(activity, media, null, true) {
            callback(it.path)
            dialog.dismiss()
        }
        mediaGrid.adapter = adapter
    }
}
