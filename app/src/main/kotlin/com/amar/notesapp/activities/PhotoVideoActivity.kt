package com.amar.NoteDirector.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.simplemobiletools.commons.extensions.hasWriteStoragePermission
import com.simplemobiletools.commons.extensions.scanPath
import com.simplemobiletools.commons.extensions.toast
import com.amar.NoteDirector.extensions.*
import com.amar.NoteDirector.fragments.PhotoFragment
import com.amar.NoteDirector.fragments.VideoFragment
import com.amar.NoteDirector.fragments.ViewPagerFragment
import com.amar.NoteDirector.helpers.IS_VIEW_INTENT
import com.amar.NoteDirector.helpers.MEDIUM
import com.amar.NoteDirector.models.Medium
import kotlinx.android.synthetic.main.fragment_holder.*
import java.io.File

open class PhotoVideoActivity : SimpleActivity(), ViewPagerFragment.FragmentListener {
    private val STORAGE_PERMISSION = 1
    private var mMedium: Medium? = null
    private var mIsFullScreen = false
    private var mFragment: ViewPagerFragment? = null

    lateinit var mUri: Uri

    companion object {
        var mIsVideo = false
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.amar.NoteDirector.R.layout.fragment_holder)

        if (hasWriteStoragePermission()) {
            checkIntent(savedInstanceState)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION)
        }
    }

    private fun checkIntent(savedInstanceState: Bundle? = null) {
        mUri = intent.data ?: return

        if (mUri.scheme == "file") {
            scanPath(mUri.path) {}
            sendViewPagerIntent(mUri.path)
            finish()
            return
        } else {
            val path = applicationContext.getRealPathFromURI(mUri) ?: ""
            scanPath(mUri.path) {}
            if (path.isNotEmpty()) {
                sendViewPagerIntent(path)
                finish()
                return
            }
        }

        showSystemUI()
        val bundle = Bundle()
        val file = File(mUri.toString())
        mMedium = Medium(file.name, mUri.toString(), mIsVideo, 0, 0, file.length())
        bundle.putSerializable(MEDIUM, mMedium)

        if (savedInstanceState == null) {
            mFragment = if (mIsVideo) VideoFragment() else PhotoFragment()
            mFragment!!.listener = this
            mFragment!!.arguments = bundle
            supportFragmentManager.beginTransaction().replace(com.amar.NoteDirector.R.id.fragment_holder, mFragment).commit()
        }

        if (config.darkBackground)
            fragment_holder.background = ColorDrawable(Color.BLACK)

        val proj = arrayOf(MediaStore.Images.Media.TITLE)
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(mUri, proj, null, null, null)
            if (cursor != null && cursor.count != 0) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.TITLE)
                cursor.moveToFirst()
                title = cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            title = mMedium?.name ?: ""
        } finally {
            cursor?.close()
        }

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            val isFullscreen = visibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
            mFragment?.fullscreenToggled(isFullscreen)
        }
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.setBackgroundDrawable(resources.getDrawable(com.amar.NoteDirector.R.drawable.actionbar_gradient_background))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkIntent()
            } else {
                toast(com.amar.NoteDirector.R.string.no_storage_permissions)
                finish()
            }
        }
    }

    private fun sendViewPagerIntent(path: String) {
        Intent(this, ViewPagerActivity::class.java).apply {
            putExtra(IS_VIEW_INTENT, true)
            putExtra(MEDIUM, path)
            startActivity(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(com.amar.NoteDirector.R.menu.photo_video_menu, menu)

        menu.findItem(com.amar.NoteDirector.R.id.menu_set_as).isVisible = mMedium?.isImage() == true
        menu.findItem(com.amar.NoteDirector.R.id.menu_edit).isVisible = mMedium?.isImage() == true

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (mMedium == null)
            return true

        when (item.itemId) {
            com.amar.NoteDirector.R.id.menu_set_as -> trySetAs(File(mMedium!!.path))
            com.amar.NoteDirector.R.id.menu_open_with -> openWith(File(mMedium!!.path))
            com.amar.NoteDirector.R.id.menu_share -> shareUri(mMedium!!, mUri)

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun fragmentClicked() {
        mIsFullScreen = !mIsFullScreen
        if (mIsFullScreen) {
            hideSystemUI()
        } else {
            showSystemUI()
        }
    }

    override fun videoEnded() = false
}
