package com.amar.NoteDirector.activities

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.hardware.SensorManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.support.v4.view.ViewPager
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.animation.DecelerateInterpolator
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.PropertiesDialog
import com.simplemobiletools.commons.dialogs.RenameItemDialog
import com.simplemobiletools.commons.extensions.*
import com.amar.NoteDirector.activities.MediaActivity.Companion.mMedia
import com.amar.NoteDirector.adapters.MyPagerAdapter
import com.amar.NoteDirector.asynctasks.GetMediaAsynctask
import com.amar.NoteDirector.dialogs.SaveAsDialog
import com.amar.NoteDirector.dialogs.SlideshowDialog
import com.amar.NoteDirector.extensions.*
import com.amar.NoteDirector.fragments.PhotoFragment
import com.amar.NoteDirector.fragments.VideoFragment
import com.amar.NoteDirector.fragments.ViewPagerFragment
import com.amar.NoteDirector.helpers.*
import com.amar.NoteDirector.models.Medium
import kotlinx.android.synthetic.main.activity_medium.*
import java.io.File
import java.io.OutputStream
import java.util.*

class ViewPagerActivity : SimpleActivity(), ViewPager.OnPageChangeListener, ViewPagerFragment.FragmentListener {
    lateinit var mOrientationEventListener: OrientationEventListener
    private var mPath = ""
    private var mDirectory = ""

    private var mIsFullScreen = false
    private var mPos = -1
    private var mShowAll = false
    private var mIsSlideshowActive = false
    private var mRotationDegrees = 0f
    private var mLastHandledOrientation = 0
    private var mPrevHashcode = 0

    private var mSlideshowHandler = Handler()
    private var mSlideshowInterval = SLIDESHOW_DEFAULT_INTERVAL
    private var mSlideshowMoveBackwards = false
    private var mSlideshowMedia = mutableListOf<Medium>()
    private var mAreSlideShowMediaVisible = false

    companion object {
        var screenWidth = 0
        var screenHeight = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.amar.NoteDirector.R.layout.activity_medium)



        if (!hasWriteStoragePermission()) {
            finish()
            return
        }

        measureScreen()
        val uri = intent.data
        if (uri != null) {
            var cursor: Cursor? = null
            try {
                val proj = arrayOf(MediaStore.Images.Media.DATA)
                cursor = contentResolver.query(uri, proj, null, null, null)
                if (cursor?.moveToFirst() == true) {
                    mPath = cursor.getStringValue(MediaStore.Images.Media.DATA)
                }
            } finally {
                cursor?.close()
            }
        } else {
            try {
                mPath = intent.getStringExtra(MEDIUM)
                mShowAll = config.showAll
            } catch (e: Exception) {
                showErrorToast(e)
                finish()
                return
            }
        }

        if (mPath.isEmpty()) {
            toast(com.amar.NoteDirector.R.string.unknown_error_occurred)
            finish()
            return
        }

        if (intent.extras?.containsKey(IS_VIEW_INTENT) == true) {
            if (isShowHiddenFlagNeeded()) {
                if (!config.isPasswordProtectionOn) {
                    config.temporarilyShowHidden = true
                }
            }

            config.isThirdPartyIntent = true
        }

        showSystemUI()

        mDirectory = File(mPath).parent
        title = mPath.getFilenameFromPath()

        view_pager.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                view_pager.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed)
                    return

                if (mMedia.isNotEmpty()) {
                    gotMedia(mMedia)
                }
            }
        })

        reloadViewPager()
        scanPath(mPath) {}
        setupOrientationEventListener()

        if (config.darkBackground)
            view_pager.background = ColorDrawable(Color.BLACK)

        if (config.hideSystemUI)
            fragmentClicked()

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            mIsFullScreen = visibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
            view_pager.adapter?.let {
                (it as MyPagerAdapter).toggleFullscreen(mIsFullScreen)
                checkSystemUI()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (intent.extras?.containsKey(IS_VIEW_INTENT) == true) {
            config.temporarilyShowHidden = false
        }

        if (config.isThirdPartyIntent) {
            mMedia.clear()
            config.isThirdPartyIntent = false
        }
    }

    private fun setupOrientationEventListener() {
        mOrientationEventListener = object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                val currOrient = when (orientation) {
                    in 45..134 -> ORIENT_LANDSCAPE_RIGHT
                    in 225..314 -> ORIENT_LANDSCAPE_LEFT
                    else -> ORIENT_PORTRAIT
                }

                if (mLastHandledOrientation != currOrient) {
                    mLastHandledOrientation = currOrient

                    requestedOrientation = when (currOrient) {
                        ORIENT_LANDSCAPE_LEFT -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        ORIENT_LANDSCAPE_RIGHT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasWriteStoragePermission()) {
            finish()
        }
        supportActionBar?.setBackgroundDrawable(resources.getDrawable(com.amar.NoteDirector.R.drawable.actionbar_gradient_background))

        if (config.maxBrightness) {
            val attributes = window.attributes
            attributes.screenBrightness = 1f
            window.attributes = attributes
        }

        if (config.screenRotation == ROTATE_BY_DEVICE_ROTATION && mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.enable()
        } else if (config.screenRotation == ROTATE_BY_SYSTEM_SETTING) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        invalidateOptionsMenu()
    }

    override fun onPause() {
        super.onPause()
        mOrientationEventListener.disable()
        stopSlideshow()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(com.amar.NoteDirector.R.menu.menu_viewpager, menu)
        val currentMedium = getCurrentMedium() ?: return true

        menu.apply {
            findItem(com.amar.NoteDirector.R.id.menu_share_1).isVisible = !config.replaceShare
            findItem(com.amar.NoteDirector.R.id.menu_share_2).isVisible = config.replaceShare
            findItem(com.amar.NoteDirector.R.id.menu_set_as).isVisible = currentMedium.isImage()
            findItem(com.amar.NoteDirector.R.id.menu_edit).isVisible = currentMedium.isImage()
            findItem(com.amar.NoteDirector.R.id.menu_rotate).isVisible = currentMedium.isImage()
            findItem(com.amar.NoteDirector.R.id.menu_save_as).isVisible = mRotationDegrees != 0f

        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (getCurrentMedium() == null)
            return true

        when (item.itemId) {
            com.amar.NoteDirector.R.id.menu_set_as -> trySetAs(getCurrentFile())
            com.amar.NoteDirector.R.id.slideshow -> initSlideshow()
            com.amar.NoteDirector.R.id.menu_copy_to -> copyMoveTo(true)
            com.amar.NoteDirector.R.id.menu_move_to -> copyMoveTo(false)
            com.amar.NoteDirector.R.id.menu_open_with -> openWith(getCurrentFile())
            com.amar.NoteDirector.R.id.menu_share_1 -> shareMedium(getCurrentMedium()!!)
            com.amar.NoteDirector.R.id.menu_share_2 -> shareMedium(getCurrentMedium()!!)
            com.amar.NoteDirector.R.id.menu_delete -> askConfirmDelete()
            com.amar.NoteDirector.R.id.menu_rename -> renameFile()
            com.amar.NoteDirector.R.id.menu_properties -> showProperties()
            com.amar.NoteDirector.R.id.show_on_map -> showOnMap()
            com.amar.NoteDirector.R.id.menu_rotate -> rotateImage()
            com.amar.NoteDirector.R.id.menu_save_as -> saveImageAs()
            com.amar.NoteDirector.R.id.settings -> launchSettings()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun updatePagerItems(media: MutableList<Medium>) {
        val pagerAdapter = MyPagerAdapter(this, supportFragmentManager, media)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !isDestroyed) {
            view_pager.apply {
                adapter = pagerAdapter
                adapter!!.notifyDataSetChanged()
                currentItem = mPos
                addOnPageChangeListener(this@ViewPagerActivity)
            }
        }
    }

    private fun initSlideshow() {
        SlideshowDialog(this) {
            startSlideshow()
        }
    }

    private fun startSlideshow() {
        if (getMediaForSlideshow()) {
            view_pager.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    view_pager.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed)
                        return

                    hideSystemUI()
                    mSlideshowInterval = config.slideshowInterval
                    mSlideshowMoveBackwards = config.slideshowMoveBackwards
                    mIsSlideshowActive = true
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    scheduleSwipe()
                }
            })
        }
    }

    private fun animatePagerTransition(forward: Boolean) {
        val oldPosition = view_pager.currentItem
        val animator = ValueAnimator.ofInt(0, view_pager.width)
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) {
            }

            override fun onAnimationEnd(animation: Animator?) {
                view_pager.endFakeDrag()

                if (view_pager.currentItem == oldPosition) {
                    slideshowEnded(forward)
                }
            }

            override fun onAnimationCancel(animation: Animator?) {
                view_pager.endFakeDrag()
            }

            override fun onAnimationStart(animation: Animator?) {
            }
        })

        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            var oldDragPosition = 0
            override fun onAnimationUpdate(animation: ValueAnimator) {
                val dragPosition = animation.animatedValue as Int
                val dragOffset = dragPosition - oldDragPosition
                oldDragPosition = dragPosition
                view_pager.fakeDragBy(dragOffset * (if (forward) 1f else -1f))
            }
        })

        animator.duration = SLIDESHOW_SCROLL_DURATION
        view_pager.beginFakeDrag()
        animator.start()
    }

    private fun slideshowEnded(forward: Boolean) {
        if (config.loopSlideshow) {
            if (forward) {
                view_pager.setCurrentItem(0, false)
            } else {
                view_pager.setCurrentItem(view_pager.adapter!!.count - 1, false)
            }
        } else {
            stopSlideshow()
            toast(com.amar.NoteDirector.R.string.slideshow_ended)
        }
    }

    private fun stopSlideshow() {
        if (mIsSlideshowActive) {
            showSystemUI()
            mIsSlideshowActive = false
            mSlideshowHandler.removeCallbacksAndMessages(null)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun scheduleSwipe() {
        mSlideshowHandler.removeCallbacksAndMessages(null)
        if (mIsSlideshowActive) {
            if (getCurrentMedium()!!.isImage() || getCurrentMedium()!!.isGif()) {
                mSlideshowHandler.postDelayed({
                    if (mIsSlideshowActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && !isDestroyed) {
                        swipeToNextMedium()
                    }
                }, mSlideshowInterval * 1000L)
            } else {
                (getCurrentFragment() as? VideoFragment)!!.playVideo()
            }
        }
    }

    private fun swipeToNextMedium() {
        animatePagerTransition(!mSlideshowMoveBackwards)
    }

    private fun getMediaForSlideshow(): Boolean {
        mSlideshowMedia = mMedia.toMutableList()
        if (!config.slideshowIncludePhotos) {
            mSlideshowMedia = mSlideshowMedia.filter { !it.isImage() } as MutableList
        }

        if (!config.slideshowIncludeVideos) {
            mSlideshowMedia = mSlideshowMedia.filter { it.isImage() || it.isGif() } as MutableList
        }

        if (!config.slideshowIncludeGIFs) {
            mSlideshowMedia = mSlideshowMedia.filter { !it.isGif() } as MutableList
        }

        if (config.slideshowRandomOrder) {
            Collections.shuffle(mSlideshowMedia)
            mPos = 0
        } else {
            mPath = getCurrentPath()
            mPos = getPositionInList(mSlideshowMedia)
        }

        return if (mSlideshowMedia.isEmpty()) {
            toast(com.amar.NoteDirector.R.string.no_media_for_slideshow)
            false
        } else {
            updatePagerItems(mSlideshowMedia)
            mAreSlideShowMediaVisible = true
            true
        }
    }

    private fun copyMoveTo(isCopyOperation: Boolean) {
        val files = ArrayList<File>(1).apply { add(getCurrentFile()) }
        tryCopyMoveFilesTo(files, isCopyOperation) {
            config.tempFolderPath = ""
            if (!isCopyOperation) {
                reloadViewPager()
            }
        }
    }

    private fun toggleFileVisibility(hide: Boolean) {
        toggleFileVisibility(getCurrentFile(), hide) {
            val newFileName = it.absolutePath.getFilenameFromPath()
            title = newFileName

            getCurrentMedium()!!.apply {
                name = newFileName
                path = it.absolutePath
                getCurrentMedia()[mPos] = this
            }
            invalidateOptionsMenu()
        }
    }

    private fun rotateImage() {
        val currentMedium = getCurrentMedium() ?: return
        if (currentMedium.isJpg() && !isPathOnSD(currentMedium.path)) {
            rotateByExif()
        } else {
            rotateByDegrees()
        }
    }

    private fun rotateByExif() {
        val exif = ExifInterface(getCurrentPath())
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val newOrientation = getNewOrientation(orientation)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, newOrientation)
        exif.saveAttributes()
        File(getCurrentPath()).setLastModified(System.currentTimeMillis())
        (getCurrentFragment() as? PhotoFragment)?.refreshBitmap()
    }

    private fun getNewOrientation(rotation: Int): String {
        return when (rotation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> ExifInterface.ORIENTATION_ROTATE_180
            ExifInterface.ORIENTATION_ROTATE_180 -> ExifInterface.ORIENTATION_ROTATE_270
            ExifInterface.ORIENTATION_ROTATE_270 -> ExifInterface.ORIENTATION_NORMAL
            else -> ExifInterface.ORIENTATION_ROTATE_90
        }.toString()
    }

    private fun rotateByDegrees() {
        mRotationDegrees = (mRotationDegrees + 90) % 360
        getCurrentFragment()?.let {
            (it as? PhotoFragment)?.rotateImageViewBy(mRotationDegrees)
        }
        supportInvalidateOptionsMenu()
    }

    private fun saveImageAs() {
        val currPath = getCurrentPath()
        SaveAsDialog(this, currPath) {
            Thread({
                toast(com.amar.NoteDirector.R.string.saving)
                val selectedFile = File(it)
                val tmpFile = File(selectedFile.parent, "tmp_${it.getFilenameFromPath()}")
                try {
                    val bitmap = BitmapFactory.decodeFile(currPath)
                    getFileOutputStream(tmpFile) {
                        if (it == null) {
                            toast(com.amar.NoteDirector.R.string.unknown_error_occurred)
                            deleteFile(tmpFile) {}
                            return@getFileOutputStream
                        }

                        saveFile(tmpFile, bitmap, it)
                        if (needsStupidWritePermissions(selectedFile.absolutePath)) {
                            deleteFile(selectedFile) {}
                        }

                        renameFile(tmpFile, selectedFile) {
                            deleteFile(tmpFile) {}
                        }
                    }
                } catch (e: OutOfMemoryError) {
                    toast(com.amar.NoteDirector.R.string.out_of_memory_error)
                    deleteFile(tmpFile) {}
                } catch (e: Exception) {
                    toast(com.amar.NoteDirector.R.string.unknown_error_occurred)
                    deleteFile(tmpFile) {}
                }
            }).start()
        }
    }

    private fun saveFile(file: File, bitmap: Bitmap, out: OutputStream) {
        val matrix = Matrix()
        matrix.postRotate(mRotationDegrees)
        val bmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bmp.compress(file.getCompressionFormat(), 90, out)
        out.flush()
        out.close()
        toast(com.amar.NoteDirector.R.string.file_saved)
    }


    private fun isShowHiddenFlagNeeded(): Boolean {
        val file = File(mPath)
        if (file.isHidden)
            return true

        var parent = file.parentFile ?: return false
        while (true) {
            if (parent.isHidden || parent.listFiles()?.contains(File(NOMEDIA)) == true) {
                return true
            }

            if (parent.absolutePath == "/") {
                break
            }
            parent = parent.parentFile ?: return false
        }

        return false
    }

    private fun getCurrentFragment() = (view_pager.adapter as MyPagerAdapter).getCurrentFragment(view_pager.currentItem)

    private fun showProperties() {
        if (getCurrentMedium() != null)
            PropertiesDialog(this, getCurrentPath(), false)
    }

    private fun showOnMap() {
        val exif = ExifInterface(getCurrentPath())
        val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
        val lat_ref = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
        val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
        val lon_ref = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)

        if (lat == null || lat_ref == null || lon == null || lon_ref == null) {
            toast(com.amar.NoteDirector.R.string.unknown_location)
        } else {
            val geoLat = if (lat_ref == "N") {
                convertToDegree(lat)
            } else {
                0 - convertToDegree(lat)
            }

            val geoLon = if (lon_ref == "E") {
                convertToDegree(lon)
            } else {
                0 - convertToDegree(lon)
            }

            val uriBegin = "geo:$geoLat,$geoLon"
            val query = "$geoLat, $geoLon"
            val encodedQuery = Uri.encode(query)
            val uriString = "$uriBegin?q=$encodedQuery&z=16"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
            val packageManager = packageManager
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                toast(com.amar.NoteDirector.R.string.no_map_application)
            }
        }
    }

    private fun convertToDegree(stringDMS: String): Float {
        val dms = stringDMS.split(",".toRegex(), 3).toTypedArray()

        val stringD = dms[0].split("/".toRegex(), 2).toTypedArray()
        val d0 = stringD[0].toDouble()
        val d1 = stringD[1].toDouble()
        val floatD = d0 / d1

        val stringM = dms[1].split("/".toRegex(), 2).toTypedArray()
        val m0 = stringM[0].toDouble()
        val m1 = stringM[1].toDouble()
        val floatM = m0 / m1

        val stringS = dms[2].split("/".toRegex(), 2).toTypedArray()
        val s0 = stringS[0].toDouble()
        val s1 = stringS[1].toDouble()
        val floatS = s0 / s1

        return (floatD + floatM / 60 + floatS / 3600).toFloat()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == REQUEST_EDIT_IMAGE) {
            if (resultCode == Activity.RESULT_OK && resultData != null) {
                mPos = -1
                reloadViewPager()
            }
        } else if (requestCode == REQUEST_SET_AS) {
            if (resultCode == Activity.RESULT_OK) {
                toast(com.amar.NoteDirector.R.string.wallpaper_set_successfully)
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(this) {
            deleteFileBg(File(getCurrentMedia()[mPos].path)) {
                reloadViewPager()
            }
        }
    }

    private fun isDirEmpty(media: ArrayList<Medium>): Boolean {
        return if (media.isEmpty()) {
            deleteDirectoryIfEmpty()
            finish()
            true
        } else
            false
    }

    private fun renameFile() {
        RenameItemDialog(this, getCurrentPath()) {
            getCurrentMedia()[mPos].path = it
            updateActionbarTitle()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        measureScreen()
    }

    private fun measureScreen() {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        } else {
            windowManager.defaultDisplay.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
    }

    private fun reloadViewPager() {
        GetMediaAsynctask(applicationContext, mDirectory, false, false, mShowAll) {
            gotMedia(it)
        }.execute()
    }

    private fun gotMedia(media: ArrayList<Medium>) {
        if (isDirEmpty(media) || media.hashCode() == mPrevHashcode) {
            return
        }

        mPrevHashcode = media.hashCode()
        mMedia = media
        mPos = if (mPos == -1) {
            getPositionInList(media)
        } else {
            Math.min(mPos, mMedia.size - 1)
        }

        updateActionbarTitle()
        updatePagerItems(mMedia.toMutableList())
        invalidateOptionsMenu()
        checkOrientation()
    }

    private fun getPositionInList(items: MutableList<Medium>): Int {
        mPos = 0
        for ((i, medium) in items.withIndex()) {
            if (medium.path == mPath) {
                return i
            }
        }
        return mPos
    }

    private fun deleteDirectoryIfEmpty() {
        val file = File(mDirectory)
        if (config.deleteEmptyFolders && !file.isDownloadsFolder() && file.isDirectory && file.listFiles()?.isEmpty() == true) {
            deleteFile(file, true) {}
        }

        scanPath(mDirectory) {}
    }

    private fun checkOrientation() {
        if (config.screenRotation == ROTATE_BY_ASPECT_RATIO) {
            val res = getCurrentFile().getResolution()
            if (res.x > res.y) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else if (res.x < res.y) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    override fun fragmentClicked() {
        mIsFullScreen = !mIsFullScreen
        checkSystemUI()
    }

    override fun videoEnded(): Boolean {
        if (mIsSlideshowActive)
            swipeToNextMedium()
        return mIsSlideshowActive
    }

    private fun checkSystemUI() {
        if (mIsFullScreen) {
            hideSystemUI()
        } else {
            stopSlideshow()
            showSystemUI()
        }
    }

    private fun updateActionbarTitle() {
        runOnUiThread {
            if (mPos < getCurrentMedia().size) {
                title = getCurrentMedia()[mPos].path.getFilenameFromPath()
            }
        }
    }

    private fun getCurrentMedium(): Medium? {
        return if (getCurrentMedia().isEmpty() || mPos == -1)
            null
        else
            getCurrentMedia()[Math.min(mPos, getCurrentMedia().size - 1)]
    }

    private fun getCurrentMedia() = if (mAreSlideShowMediaVisible) mSlideshowMedia else mMedia

    private fun getCurrentPath() = getCurrentMedium()!!.path

    private fun getCurrentFile() = File(getCurrentPath())

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {

    }

    override fun onPageSelected(position: Int) {
        if (view_pager.offscreenPageLimit == 1) {
            view_pager.offscreenPageLimit = 2
        }
        mPos = position
        updateActionbarTitle()
        mRotationDegrees = 0f
        supportInvalidateOptionsMenu()
        scheduleSwipe()
    }

    override fun onPageScrollStateChanged(state: Int) {
        if (state == ViewPager.SCROLL_STATE_IDLE) {
            checkOrientation()
        }
    }
}