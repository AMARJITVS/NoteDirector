package com.amar.NoteDirector.extensions

import android.os.Environment
import com.amar.NoteDirector.helpers.NOMEDIA
import java.io.File

fun File.containsNoMedia() = isDirectory && File(this, NOMEDIA).exists()

fun File.isDownloadsFolder() = absolutePath == Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString()
