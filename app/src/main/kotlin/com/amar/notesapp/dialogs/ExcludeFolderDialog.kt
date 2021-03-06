package com.amar.NoteDirector.dialogs

import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getBasePath
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.amar.NoteDirector.activities.SimpleActivity
import com.amar.NoteDirector.extensions.config
import kotlinx.android.synthetic.main.dialog_exclude_folder.view.*

class ExcludeFolderDialog(val activity: SimpleActivity, val selectedPaths: List<String>, val callback: () -> Unit) {
    val alternativePaths = getAlternativePathsList()
    var radioGroup: RadioGroup? = null

    init {
        val view = LayoutInflater.from(activity).inflate(com.amar.NoteDirector.R.layout.dialog_exclude_folder, null).apply {
            exclude_folder_parent.beVisibleIf(alternativePaths.size > 1)

            radioGroup = exclude_folder_radio_group
            exclude_folder_radio_group.beVisibleIf(alternativePaths.size > 1)
        }

        alternativePaths.forEachIndexed { index, value ->
            val radioButton = (activity.layoutInflater.inflate(com.amar.NoteDirector.R.layout.radio_button, null) as RadioButton).apply {
                text = alternativePaths[index]
                isChecked = index == 0
                id = index
            }
            radioGroup!!.addView(radioButton, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(com.amar.NoteDirector.R.string.ok, { dialog, which -> dialogConfirmed() })
                .setNegativeButton(com.amar.NoteDirector.R.string.cancel, null)
                .create().apply {
            activity.setupDialogStuff(view, this)
        }
    }

    private fun dialogConfirmed() {
        val path = if (alternativePaths.isEmpty()) selectedPaths[0] else alternativePaths[radioGroup!!.checkedRadioButtonId]
        activity.config.addExcludedFolder(path)
        callback()
    }

    private fun getAlternativePathsList(): List<String> {
        val pathsList = ArrayList<String>()
        if (selectedPaths.size > 1)
            return pathsList

        val path = selectedPaths[0]
        var basePath = path.getBasePath(activity)
        val relativePath = path.substring(basePath.length)
        val parts = relativePath.split("/").filter(String::isNotEmpty)
        if (parts.isEmpty())
            return pathsList

        pathsList.add(basePath)
        if (basePath == "/")
            basePath = ""

        for (part in parts) {
            basePath += "/$part"
            pathsList.add(basePath)
        }

        return pathsList.reversed()
    }
}
