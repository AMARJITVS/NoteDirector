package com.amar.NoteDirector.activities

import android.os.Bundle
import android.util.Log

class VideoActivity : PhotoVideoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        mIsVideo = true
        super.onCreate(savedInstanceState)
    }
}
