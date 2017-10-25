package com.amar.NoteDirector.extensions

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue

fun Resources.getActionBarHeight(context: Context): Int {
    val tv = TypedValue()
    var height = 0
    if (context.theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
        height = TypedValue.complexToDimensionPixelSize(tv.data, displayMetrics)
    }
    return height
}

fun Resources.getStatusBarHeight(): Int {
    val id = getIdentifier("status_bar_height", "dimen", "android")
    return if (id > 0) {
        getDimensionPixelSize(id)
    } else
        0
}

fun Resources.getNavBarHeight(): Int {
    val id = getIdentifier("navigation_bar_height", "dimen", "android")
    return if (id > 0) {
        getDimensionPixelSize(id)
    } else
        0
}
