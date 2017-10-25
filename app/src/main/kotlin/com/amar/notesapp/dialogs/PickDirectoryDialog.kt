package com.amar.NoteDirector.dialogs

import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.view.LayoutInflater
import android.view.View
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.extensions.beGoneIf
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.toast
import com.amar.NoteDirector.activities.SimpleActivity
import com.amar.NoteDirector.adapters.DirectoryAdapter
import com.amar.NoteDirector.asynctasks.GetDirectoriesAsynctask
import com.amar.NoteDirector.extensions.config
import com.amar.NoteDirector.extensions.getCachedDirectories
import com.amar.NoteDirector.models.Directory
import kotlinx.android.synthetic.main.dialog_directory_picker.view.*

class PickDirectoryDialog(val activity: SimpleActivity, val sourcePath: String, val callback: (path: String) -> Unit) {
    var dialog: AlertDialog
    var shownDirectories: ArrayList<Directory> = ArrayList()
    var view: View = LayoutInflater.from(activity).inflate(com.amar.NoteDirector.R.layout.dialog_directory_picker, null)

    init {
        (view.directories_grid.layoutManager as GridLayoutManager).apply {
            orientation = if (activity.config.scrollHorizontally) GridLayoutManager.HORIZONTAL else GridLayoutManager.VERTICAL
            spanCount = activity.config.dirColumnCnt
        }

        dialog = AlertDialog.Builder(activity)
                .setPositiveButton(com.amar.NoteDirector.R.string.ok, null)
                .setNegativeButton(com.amar.NoteDirector.R.string.cancel, null)
                .setNeutralButton(com.amar.NoteDirector.R.string.other_folder, { _, _ -> showOtherFolder() })
                .create().apply {
            activity.setupDialogStuff(view, this, com.amar.NoteDirector.R.string.select_destination)

            val dirs = activity.getCachedDirectories()
            if (dirs.isNotEmpty()) {
                gotDirectories(activity.addTempFolderIfNeeded(dirs))
            }

            GetDirectoriesAsynctask(activity, false, false) {
                gotDirectories(activity.addTempFolderIfNeeded(it))
            }.execute()
        }
    }

    fun showOtherFolder() {
        val showHidden = activity.config.shouldShowHidden
        FilePickerDialog(activity, sourcePath, false, showHidden, true) {
            callback(it)
        }
    }

    private fun gotDirectories(directories: ArrayList<Directory>) {
        if (directories.hashCode() == shownDirectories.hashCode())
            return

        shownDirectories = directories
        val adapter = DirectoryAdapter(activity, directories, null, true) {
            if (it.path.trimEnd('/') == sourcePath) {
                activity.toast(com.amar.NoteDirector.R.string.source_and_destination_same)
                return@DirectoryAdapter
            } else {
                callback(it.path)
                dialog.dismiss()
            }
        }

        val scrollHorizontally = activity.config.scrollHorizontally
        view.apply {
            directories_grid.adapter = adapter

            directories_vertical_fastscroller.isHorizontal = false
            directories_vertical_fastscroller.beGoneIf(scrollHorizontally)

            directories_horizontal_fastscroller.isHorizontal = true
            directories_horizontal_fastscroller.beVisibleIf(scrollHorizontally)

            if (scrollHorizontally) {
                directories_horizontal_fastscroller.setViews(directories_grid)
            } else {
                directories_vertical_fastscroller.setViews(directories_grid)
            }
        }
    }
}
