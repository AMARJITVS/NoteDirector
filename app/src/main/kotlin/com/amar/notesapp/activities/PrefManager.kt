package com.amar.notesapp.activities

import android.content.Context
import android.content.SharedPreferences

/**
 * Created by DELL on 15-10-2017.
 */
class PrefManager(context: Context) {
    internal var pref: SharedPreferences
    internal var editor:SharedPreferences.Editor
    internal var _context:Context
    // shared pref mode
    internal var PRIVATE_MODE = 0
    fun setFirstTimeLaunch(isFirstTime:Boolean) {
        editor.putBoolean(IS_FIRST_TIME_LAUNCH, isFirstTime)
        editor.commit()
    }
    fun isFirstTimeLaunch():Boolean {
        return pref.getBoolean(IS_FIRST_TIME_LAUNCH, true)
    }
    init{
        this._context = context
        pref = _context.getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        editor = pref.edit()
    }
    companion object {
        // Shared preferences file name
        private val PREF_NAME = "androidhive-welcome"
        private val IS_FIRST_TIME_LAUNCH = "IsFirstTimeLaunch"
    }
}