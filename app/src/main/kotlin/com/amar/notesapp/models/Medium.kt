package com.amar.NoteDirector.models

import com.simplemobiletools.commons.extensions.getMimeType
import com.simplemobiletools.commons.extensions.isGif
import com.simplemobiletools.commons.extensions.isPng
import com.simplemobiletools.commons.helpers.*
import java.io.File
import java.io.Serializable

data class Medium(var name: String, var path: String, val video: Boolean, val modified: Long, val taken: Long, val size: Long) : Serializable, Comparable<Medium> {
    companion object {
        private val serialVersionUID = -6553149366975455L
        var sorting: Int = 0
    }

    fun isPng() = path.isPng()

    fun isGif() = path.isGif()

    fun isJpg() = path.endsWith(".jpg", true) || path.endsWith(".jpeg", true)

    fun isImage() = !isGif() && !video

    fun getMimeType() = File(path).getMimeType()

    override fun compareTo(other: Medium): Int {
        var result: Int
        if (sorting and SORT_BY_NAME != 0) {
            result = AlphanumComparator().compare(name.toLowerCase(), other.name.toLowerCase())
        } else if (sorting and SORT_BY_SIZE != 0) {
            result = if (size == other.size)
                0
            else if (size > other.size)
                1
            else
                -1
        } else if (sorting and SORT_BY_DATE_MODIFIED != 0) {
            result = if (modified == other.modified)
                0
            else if (modified > other.modified)
                1
            else
                -1
        } else {
            result = if (taken == other.taken)
                0
            else if (taken > other.taken)
                1
            else
                -1
        }

        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }
        return result
    }
}
