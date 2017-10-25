package com.amar.NoteDirector.dialogs

import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.amar.NoteDirector.activities.SimpleActivity
import com.amar.NoteDirector.extensions.config
import com.amar.NoteDirector.helpers.GIFS
import com.amar.NoteDirector.helpers.IMAGES
import com.amar.NoteDirector.helpers.VIDEOS
import kotlinx.android.synthetic.main.dialog_filter_media.view.*

class FilterMediaDialog(val activity: SimpleActivity, val callback: (result: Int) -> Unit) {
    private var view: View = LayoutInflater.from(activity).inflate(com.amar.NoteDirector.R.layout.dialog_filter_media, null)

    init {
        val filterMedia = activity.config.filterMedia
        view.apply {
            filter_media_images.isChecked = filterMedia and IMAGES != 0
            filter_media_videos.isChecked = filterMedia and VIDEOS != 0
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(com.amar.NoteDirector.R.string.ok, { _, _ -> dialogConfirmed() })
                .setNegativeButton(com.amar.NoteDirector.R.string.cancel, null)
                .create().apply {
            activity.setupDialogStuff(view, this, com.amar.NoteDirector.R.string.filter_media)
        }
    }

    private fun dialogConfirmed() {
        var result = 0
        if (view.filter_media_images.isChecked)
            result += IMAGES
        if (view.filter_media_videos.isChecked)
            result += VIDEOS

        activity.config.filterMedia = result
        callback(result)
    }
}
