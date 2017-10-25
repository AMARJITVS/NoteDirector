package com.amar.NoteDirector.activities

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.simplemobiletools.commons.extensions.toast
import com.theartofdev.edmodo.cropper.CropImageView
import kotlinx.android.synthetic.main.view_crop_image.*

class SetWallpaperActivity : SimpleActivity(), CropImageView.OnCropImageCompleteListener {
    private val PICK_IMAGE = 1
    private var isLandscapeRatio = true

    lateinit var uri: Uri
    lateinit var wallpaperManager: WallpaperManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.amar.NoteDirector.R.layout.view_crop_image)

        if (intent.data == null) {
            val pickIntent = Intent(applicationContext, MainActivity::class.java)
            pickIntent.action = Intent.ACTION_PICK
            pickIntent.type = "image/*"
            startActivityForResult(pickIntent, PICK_IMAGE)
            return
        }

        handleImage(intent)
    }

    private fun handleImage(intent: Intent) {
        uri = intent.data
        if (uri.scheme != "file" && uri.scheme != "content") {
            toast(com.amar.NoteDirector.R.string.unknown_file_location)
            finish()
            return
        }

        wallpaperManager = WallpaperManager.getInstance(applicationContext)
        crop_image_view.apply {
            setOnCropImageCompleteListener(this@SetWallpaperActivity)
            setImageUriAsync(uri)
        }

        setupAspectRatio()
    }

    private fun setupAspectRatio() {
        val wallpaperWidth = if (isLandscapeRatio) wallpaperManager.desiredMinimumWidth else wallpaperManager.desiredMinimumWidth / 2
        crop_image_view.setAspectRatio(wallpaperWidth, wallpaperManager.desiredMinimumHeight)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(com.amar.NoteDirector.R.menu.menu_set_wallpaper, menu)

        menu.findItem(com.amar.NoteDirector.R.id.portrait_aspect_ratio).isVisible = isLandscapeRatio
        menu.findItem(com.amar.NoteDirector.R.id.landscape_aspect_ratio).isVisible = !isLandscapeRatio
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            com.amar.NoteDirector.R.id.save -> crop_image_view.getCroppedImageAsync()
            com.amar.NoteDirector.R.id.rotate -> crop_image_view.rotateImage(90)
            com.amar.NoteDirector.R.id.portrait_aspect_ratio -> changeAspectRatio(false)
            com.amar.NoteDirector.R.id.landscape_aspect_ratio -> changeAspectRatio(true)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun changeAspectRatio(isLandscape: Boolean) {
        isLandscapeRatio = isLandscape
        setupAspectRatio()
        invalidateOptionsMenu()
    }

    override fun onCropImageComplete(view: CropImageView?, result: CropImageView.CropResult) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed)
            return

        if (result.error == null) {
            toast(com.amar.NoteDirector.R.string.setting_wallpaper)
            Thread({
                val bitmap = result.bitmap
                val wantedHeight = wallpaperManager.desiredMinimumHeight
                val ratio = wantedHeight / bitmap.height.toFloat()
                val wantedWidth = (bitmap.width * ratio).toInt()
                try {
                    wallpaperManager.setBitmap(Bitmap.createScaledBitmap(bitmap, wantedWidth, wantedHeight, true))
                    setResult(Activity.RESULT_OK)
                } catch (e: OutOfMemoryError) {
                    toast(com.amar.NoteDirector.R.string.out_of_memory_error)
                    setResult(Activity.RESULT_CANCELED)
                }
                finish()
            }).start()
        } else {
            toast("${getString(com.amar.NoteDirector.R.string.image_editing_failed)}: ${result.error.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == PICK_IMAGE) {
            if (resultCode == Activity.RESULT_OK && resultData != null) {
                handleImage(resultData)
            } else {
                finish()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }
}
