package com.amar.NoteDirector.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityCompat
import android.support.v4.content.FileProvider
import android.support.v7.widget.GridLayoutManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.Toast
import com.amar.NoteDirector.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.views.MyScalableRecyclerView
import com.amar.NoteDirector.adapters.MediaAdapter
import com.amar.NoteDirector.asynctasks.GetMediaAsynctask
import com.amar.NoteDirector.dialogs.ChangeSortingDialog
import com.amar.NoteDirector.dialogs.ExcludeFolderDialog
import com.amar.NoteDirector.dialogs.FilterMediaDialog
import com.amar.NoteDirector.extensions.*
import com.amar.NoteDirector.helpers.*
import com.amar.NoteDirector.models.Medium
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.InterstitialAd
import com.google.android.gms.ads.MobileAds
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_media.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*

class MediaActivity : SimpleActivity(), MediaAdapter.MediaOperationsListener {
    var img: String? = null
    private val TAG = MediaActivity::class.java.simpleName
    private val SAVE_MEDIA_CNT = 40
    private val IMAGE_REQUEST=2
    private val LAST_MEDIA_CHECK_PERIOD = 3000L
    private var mPath = ""
    private var mIsGetImageIntent = false
    private var mIsGetVideoIntent = false
    private var mIsGetAnyIntent = false
    private var mIsGettingMedia = false
    private var mShowAll = false
    private var mLoadedInitialPhotos = false
    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mLastDrawnHashCode = 0
    private var mLastMediaModified = 0
    private var mLastMediaHandler = Handler()
    private lateinit var floatclick: FloatingActionButton

    companion object {
        var mMedia = ArrayList<Medium>()
    }
    private var isFabOpen = false
    private lateinit var fab: FloatingActionButton
    private lateinit var fab1: FloatingActionButton
    private lateinit var fab2: FloatingActionButton
    private lateinit var fab3: FloatingActionButton
    private lateinit var fab4: FloatingActionButton
    private lateinit var fab_open: Animation
    private lateinit var fab_close: Animation
    private lateinit var rotate_forward: Animation
    private lateinit var rotate_backward: Animation
    private lateinit var mAdView: AdView
    private lateinit var mInterstitialAd: InterstitialAd

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.amar.NoteDirector.R.layout.activity_media)
        intent.apply {
            mIsGetImageIntent = getBooleanExtra(GET_IMAGE_INTENT, false)
            mIsGetVideoIntent = getBooleanExtra(GET_VIDEO_INTENT, false)
            mIsGetAnyIntent = getBooleanExtra(GET_ANY_INTENT, false)
        }

        MobileAds.initialize(this,"ca-app-pub-5725704895776408/6340379704")
        mInterstitialAd =InterstitialAd(this)
        mInterstitialAd.setAdUnitId("ca-app-pub-5725704895776408/7637983656");
        mInterstitialAd.loadAd(AdRequest.Builder().build())
        mAdView = findViewById<View>(R.id.adView) as AdView
        val adRequest = AdRequest.Builder().addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build()
        mAdView.loadAd(adRequest)

        media_refresh_layout.setOnRefreshListener({ getMedia() })
        mPath = intent.getStringExtra(DIRECTORY)
        mStoredAnimateGifs = config.animateGifs
        mStoredCropThumbnails = config.cropThumbnails
        mStoredScrollHorizontally = config.scrollHorizontally
        mShowAll = config.showAll
        if (mShowAll)
            supportActionBar?.setDisplayHomeAsUpEnabled(false)

        media_empty_text.setOnClickListener {
            showFilterMediaDialog()
        }
        fab = findViewById(R.id.fab)
        fab1 = findViewById(R.id.fab1)
        fab2 = findViewById(R.id.fab2)
        fab3 = findViewById(R.id.fab3)
        fab4 = findViewById(R.id.fab4)
        fab_open = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fab_open)
        fab_close = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fab_close)
        rotate_forward = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.rotate_forward)
        rotate_backward = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.rotate_backward)
        fab.setOnClickListener(clickListener)
        fab1.setOnClickListener(clickListener)
        fab2.setOnClickListener(clickListener)
        fab3.setOnClickListener(clickListener)
        fab4.setOnClickListener(clickListener)
    }
    override fun onSaveInstanceState(savedInstanceState:Bundle) {
        savedInstanceState.putString("PUT", img)
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {

        img=savedInstanceState?.getString("PUT")
        // Always call the superclass so it can save the view hierarchy state
        super.onRestoreInstanceState(savedInstanceState)
    }
    val clickListener = View.OnClickListener { view ->

        when (view.getId()) {
            R.id.fab ->
                animateFAB()
            R.id.fab1 ->
                openvideo(getHumanizedFilename(mPath))
            R.id.fab2 ->
                opencamera(getHumanizedFilename(mPath))
            R.id.fab4 ->
                VideoFromCard()
            R.id.fab3 ->
                 ImageFromCard()
        }
    }
    fun animateFAB() {
        if (isFabOpen)
        {
            fab.startAnimation(rotate_backward)
            fab1.startAnimation(fab_close)
            fab2.startAnimation(fab_close)
            fab1.setClickable(false)
            fab2.setClickable(false)
            fab3.startAnimation(fab_close)
            fab4.startAnimation(fab_close)
            fab3.setClickable(false)
            fab4.setClickable(false)
            isFabOpen = false
        }
        else
        {
            fab.startAnimation(rotate_forward)
            fab1.startAnimation(fab_open)
            fab2.startAnimation(fab_open)
            fab1.setClickable(true)
            fab2.setClickable(true)
            fab3.startAnimation(fab_open)
            fab4.startAnimation(fab_open)
            fab3.setClickable(true)
            fab4.setClickable(true)
            isFabOpen = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (mShowAll && mStoredAnimateGifs != config.animateGifs) {
            media_grid.adapter?.notifyDataSetChanged()
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            media_grid.adapter?.notifyDataSetChanged()
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            media_grid.adapter?.let {
                (it as MediaAdapter).scrollVertically = !config.scrollHorizontally
                it.notifyDataSetChanged()
            }
            setupScrollDirection()
        }

        tryloadGallery()
        invalidateOptionsMenu()
        fab.setBackgroundTintList(ColorStateList.valueOf(config.primaryColor))
        fab1.setBackgroundTintList(ColorStateList.valueOf(config.primaryColor))
        fab2.setBackgroundTintList(ColorStateList.valueOf(config.primaryColor))
        fab3.setBackgroundTintList(ColorStateList.valueOf(config.primaryColor))
        fab4.setBackgroundTintList(ColorStateList.valueOf(config.primaryColor))
        media_empty_text_label.setTextColor(config.textColor)
        media_empty_text.setTextColor(config.primaryColor)
    }

    override fun onPause() {
        super.onPause()
        mIsGettingMedia = false
        media_refresh_layout.isRefreshing = false
        mStoredAnimateGifs = config.animateGifs
        mStoredCropThumbnails = config.cropThumbnails
        mStoredScrollHorizontally = config.scrollHorizontally
        media_grid.listener = null
        mLastMediaHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (config.showAll)
            config.temporarilyShowHidden = false
        mMedia.clear()
    }

    private fun tryloadGallery() {
        if (hasWriteStoragePermission()) {
            val dirName = getHumanizedFilename(mPath)
            title = if (mShowAll) resources.getString(com.amar.NoteDirector.R.string.all_folders) else dirName
            getMedia()
            setupLayoutManager()
            checkIfColorChanged()
        } else {
            finish()
        }
    }

    private fun checkIfColorChanged() {
        if (media_grid.adapter != null && getRecyclerAdapter().primaryColor != config.primaryColor) {
            getRecyclerAdapter().primaryColor = config.primaryColor
            media_horizontal_fastscroller.updateHandleColor()
            media_vertical_fastscroller.updateHandleColor()
            fab.setBackgroundTintList(ColorStateList.valueOf(config.primaryColor))
            fab1.setBackgroundTintList(ColorStateList.valueOf(config.primaryColor))
            fab2.setBackgroundTintList(ColorStateList.valueOf(config.primaryColor))
            fab3.setBackgroundTintList(ColorStateList.valueOf(config.primaryColor))
            fab4.setBackgroundTintList(ColorStateList.valueOf(config.primaryColor))
        }
    }

    private fun setupAdapter() {
        if (isDirEmpty())
            return

        val currAdapter = media_grid.adapter
        if (currAdapter == null) {
            media_grid.adapter = MediaAdapter(this, mMedia, this, mIsGetAnyIntent) {
                itemClicked(it.path)
            }
        } else {
            (currAdapter as MediaAdapter).updateMedia(mMedia)
        }
        setupScrollDirection()
    }

    private fun setupScrollDirection() {
        media_refresh_layout.isEnabled = !config.scrollHorizontally

        media_vertical_fastscroller.isHorizontal = false
        media_vertical_fastscroller.beGoneIf(config.scrollHorizontally)

        media_horizontal_fastscroller.isHorizontal = true
        media_horizontal_fastscroller.beVisibleIf(config.scrollHorizontally)

        if (config.scrollHorizontally) {
            media_horizontal_fastscroller.setViews(media_grid, media_refresh_layout)
        } else {
            media_vertical_fastscroller.setViews(media_grid, media_refresh_layout)
        }
    }

    private fun checkLastMediaChanged() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed)
            return

        mLastMediaHandler.removeCallbacksAndMessages(null)
        mLastMediaHandler.postDelayed({
            Thread({
                val lastModified = getLastMediaModified()
                if (mLastMediaModified != lastModified) {
                    mLastMediaModified = lastModified
                    runOnUiThread {
                        getMedia()
                    }
                } else {
                    checkLastMediaChanged()
                }
            }).start()
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(com.amar.NoteDirector.R.menu.menu_media, menu)

       File(mPath).containsNoMedia()
        menu.apply {

            findItem(com.amar.NoteDirector.R.id.folder_view).isVisible = mShowAll
            findItem(com.amar.NoteDirector.R.id.open_camera).isVisible = mShowAll
            findItem(com.amar.NoteDirector.R.id.increase_column_count).isVisible = config.mediaColumnCnt < 10
            findItem(com.amar.NoteDirector.R.id.reduce_column_count).isVisible = config.mediaColumnCnt > 1
        }

        return true
    }

    fun ad()
    {
        if (mInterstitialAd.isLoaded()) {
            mInterstitialAd.show()
        } else {
        }
        launchSettings()
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            com.amar.NoteDirector.R.id.sort -> showSortingDialog()
            com.amar.NoteDirector.R.id.filter -> showFilterMediaDialog()
            com.amar.NoteDirector.R.id.toggle_filename -> toggleFilenameVisibility()
            com.amar.NoteDirector.R.id.folder_view -> switchToFolderView()
            com.amar.NoteDirector.R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
            com.amar.NoteDirector.R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
            com.amar.NoteDirector.R.id.increase_column_count -> increaseColumnCount()
            com.amar.NoteDirector.R.id.reduce_column_count -> reduceColumnCount()
            com.amar.NoteDirector.R.id.settings -> ad()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, false, !config.showAll, mPath) {
            getMedia()
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            media_refresh_layout.isRefreshing = true
            getMedia()
        }
    }

    private fun toggleFilenameVisibility() {
        config.displayFileNames = !config.displayFileNames
        if (media_grid.adapter != null)
            getRecyclerAdapter().updateDisplayFilenames(config.displayFileNames)
    }

    private fun switchToFolderView() {
        config.showAll = false
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }



    private fun deleteDirectoryIfEmpty() {
        val file = File(mPath)
        if (config.deleteEmptyFolders && !file.isDownloadsFolder() && file.isDirectory && file.listFiles()?.isEmpty() == true) {
            deleteFile(file, true) {}
        }
    }

    private fun getMedia() {
        if (mIsGettingMedia)
            return

        mIsGettingMedia = true
        val token = object : TypeToken<List<Medium>>() {}.type
        val media = Gson().fromJson<ArrayList<Medium>>(config.loadFolderMedia(mPath), token) ?: ArrayList(1)
        if (media.isNotEmpty() && !mLoadedInitialPhotos) {
            gotMedia(media, true)
        } else {
            media_refresh_layout.isRefreshing = true
        }

        mLoadedInitialPhotos = true
        GetMediaAsynctask(applicationContext, mPath, mIsGetVideoIntent, mIsGetImageIntent, mShowAll) {
            gotMedia(it)
        }.execute()
    }

    private fun isDirEmpty(): Boolean {
        return if (mMedia.size <= 0 && config.filterMedia > 0) {
            deleteDirectoryIfEmpty()
            finish()
            true
        } else
            false
    }

    private fun tryToggleTemporarilyShowHidden() {
        if (config.temporarilyShowHidden) {
            toggleTemporarilyShowHidden(false)
        } else {
            handleHiddenFolderPasswordProtection {
                toggleTemporarilyShowHidden(true)
            }
        }
    }

    private fun toggleTemporarilyShowHidden(show: Boolean) {
        config.temporarilyShowHidden = show
        getMedia()
        invalidateOptionsMenu()
    }

    private fun getRecyclerAdapter() = (media_grid.adapter as MediaAdapter)

    private fun setupLayoutManager() {
        val layoutManager = media_grid.layoutManager as GridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = GridLayoutManager.HORIZONTAL
            media_refresh_layout.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            layoutManager.orientation = GridLayoutManager.VERTICAL
            media_refresh_layout.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        media_grid.isDragSelectionEnabled = true
        media_grid.isZoomingEnabled = true
        layoutManager.spanCount = config.mediaColumnCnt
        media_grid.listener = object : MyScalableRecyclerView.MyScalableRecyclerViewListener {
            override fun zoomIn() {
                if (layoutManager.spanCount > 1) {
                    reduceColumnCount()
                    getRecyclerAdapter().actMode?.finish()
                }
            }

            override fun zoomOut() {
                if (layoutManager.spanCount < 10) {
                    increaseColumnCount()
                    getRecyclerAdapter().actMode?.finish()
                }
            }

            override fun selectItem(position: Int) {
                getRecyclerAdapter().selectItem(position)
            }

            override fun selectRange(initialSelection: Int, lastDraggedIndex: Int, minReached: Int, maxReached: Int) {
                getRecyclerAdapter().selectRange(initialSelection, lastDraggedIndex, minReached, maxReached)
            }
        }
    }

    private fun increaseColumnCount() {
        config.mediaColumnCnt = ++(media_grid.layoutManager as GridLayoutManager).spanCount
        invalidateOptionsMenu()
        media_grid.adapter?.notifyDataSetChanged()
    }

    private fun reduceColumnCount() {
        config.mediaColumnCnt = --(media_grid.layoutManager as GridLayoutManager).spanCount
        invalidateOptionsMenu()
        media_grid.adapter?.notifyDataSetChanged()
    }

    private fun isSetWallpaperIntent() = intent.getBooleanExtra(SET_WALLPAPER_INTENT, false)

    private fun opencamera(folder : String)
    {
        val uriSavedImage:Uri
        val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
        val imagesFolder = File(Environment.getExternalStorageDirectory().absolutePath+"/NoteDirector/"+folder)
        if(!imagesFolder.exists())
            imagesFolder.mkdirs()
        val imgname="NoteDirector-"+timeStamp+".jpg"
        img= Environment.getExternalStorageDirectory().absolutePath+"/NoteDirector/"+folder+"/"+imgname
        val image = File(imagesFolder, imgname)
        if (Build.VERSION.SDK_INT < 24) {
             uriSavedImage = Uri.fromFile(image)
        }
        else
        {
             uriSavedImage =FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".provider", image)
        }
        val imageIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        imageIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage)
        startActivityForResult(imageIntent, IMAGE_REQUEST)
    }
    private fun openvideo(imgn : String)
    {
        val uriSavedImage:Uri
        val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
        val imagesFolder = File(Environment.getExternalStorageDirectory().absolutePath+"/NoteDirector/"+imgn)
        if(!imagesFolder.exists())
            imagesFolder.mkdirs()
        val imgname="NoteDirector-"+timeStamp+".mp4"
        img=Environment.getExternalStorageDirectory().absolutePath+"/NoteDirector/"+imgn+"/"+imgname
        val image = File(imagesFolder, imgname)
        if (Build.VERSION.SDK_INT < 24) {
            uriSavedImage = Uri.fromFile(image)
        }
        else
        {
             uriSavedImage =FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".provider", image)
        }
        val imageIntent = Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE)
        imageIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage)
        startActivityForResult(imageIntent, IMAGE_REQUEST)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_EDIT_IMAGE) {
                if (resultData != null) {
                    mMedia.clear()
                    refreshItems()
                }
            } else if (requestCode == IMAGE_REQUEST) {
                val imagepath = File(img)
                val mPath = Uri.fromFile(imagepath)
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.setData(mPath)
                this.sendBroadcast(mediaScanIntent)
                refreshItems()
            } else if (requestCode == 10) {
                if (resultData?.getData() != null) {

                    val mImageUri = resultData.getData()
                    copyimagefile(MainActivity.FilePickUtils.getSmartFilePath(this,mImageUri),getHumanizedFilename(mPath))
                } else {
                    if (resultData?.getClipData() != null) {
                        val mClipData = resultData.getClipData()
                        val mArrayUri = ArrayList<Uri>()
                        for (i in 0..mClipData.getItemCount() - 1) {

                            val item = mClipData.getItemAt(i)
                            val uri = item.getUri()
                            mArrayUri.add(uri)

                        }
                        for (i in 0..mArrayUri.size - 1) {
                            copyimagefile(MainActivity.FilePickUtils.getSmartFilePath(this, mArrayUri[i]), getHumanizedFilename(mPath))
                        }
                    }

                }
            } else if (requestCode == 11) {
                if (resultData?.getData() != null) {

                    val mImageUri = resultData.getData()
                    copyvideofile(MainActivity.FilePickUtils.getSmartFilePath(this,mImageUri),getHumanizedFilename(mPath))

                } else {
                    if (resultData?.getClipData() != null) {
                        val mClipData = resultData.getClipData()
                        val mArrayUri = ArrayList<Uri>()
                        for (i in 0..mClipData.getItemCount() - 1) {

                            val item = mClipData.getItemAt(i)
                            val uri = item.getUri()
                            mArrayUri.add(uri)

                        }
                        for (i in 0..mArrayUri.size - 1) {
                            copyvideofile(MainActivity.FilePickUtils.getSmartFilePath(this, mArrayUri[i]), getHumanizedFilename(mPath))
                        }
                    }

                }


            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }


    private fun ImageFromCard() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent,
                "select multiple images"), 10)
    }
    private fun VideoFromCard() {
        val intent = Intent()
        intent.type = "video/*"
        intent.action = Intent.ACTION_GET_CONTENT
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent,
                "select multiple videos"), 11)
    }

    private fun copyimagefile(source:String?,dest:String?)
    {
        if(source!=null && dest!=null) {
            val srcfile = File(source)
            val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmssSSS").format(Date())
            val imagesFolder = File(Environment.getExternalStorageDirectory().absolutePath + "/NoteDirector/" + dest)
            if (!imagesFolder.exists())
                imagesFolder.mkdirs()
            val imgname = "NoteDirector-" + timeStamp + ".jpg"
            img = Environment.getExternalStorageDirectory().absolutePath + "/NoteDirector/" + dest + "/" + imgname
            val destfile = File(imagesFolder, imgname)
            val sources: FileChannel?
            val destinations: FileChannel?
            sources = FileInputStream(srcfile).getChannel()
            destinations = FileOutputStream(destfile).getChannel()
            if (destinations != null && sources != null) {
                destinations.transferFrom(sources, 0, sources.size())
            }
            if (sources != null) {
                sources.close()
            }
            if (destinations != null) {
                destinations.close()
            }
        }
        Toast.makeText(this,"Added!!!", Toast.LENGTH_SHORT).show()
        val imagepath = File(img)
        val mPath = Uri.fromFile(imagepath)
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.setData(mPath)
        this.sendBroadcast(mediaScanIntent)
        refreshItems()
    }
    private fun copyvideofile(source:String?,dest:String?)
    {
        if(source!=null && dest!=null) {
            val srcfile = File(source)
            val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmssSSS").format(Date())
            val imagesFolder = File(Environment.getExternalStorageDirectory().absolutePath + "/NoteDirector/" + dest)
            if (!imagesFolder.exists())
                imagesFolder.mkdirs()
            val imgname = "NoteDirector-" + timeStamp + ".mp4"
            img = Environment.getExternalStorageDirectory().absolutePath + "/NoteDirector/" + dest + "/" + imgname
            val destfile = File(imagesFolder, imgname)
            val sources: FileChannel?
            val destinations: FileChannel?
            sources = FileInputStream(srcfile).getChannel()
            destinations = FileOutputStream(destfile).getChannel()
            if (destinations != null && sources != null) {
                destinations.transferFrom(sources, 0, sources.size())
            }
            if (sources != null) {
                sources.close()
            }
            if (destinations != null) {
                destinations.close()
            }
        }
        Toast.makeText(this,"Added!!!", Toast.LENGTH_SHORT).show()
        val imagepath = File(img)
        val mPath = Uri.fromFile(imagepath)
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.setData(mPath)
        this.sendBroadcast(mediaScanIntent)
        refreshItems()
    }


    private fun itemClicked(path: String) {
        if (isSetWallpaperIntent()) {
            toast(com.amar.NoteDirector.R.string.setting_wallpaper)

            val wantedWidth = wallpaperDesiredMinimumWidth
            val wantedHeight = wallpaperDesiredMinimumHeight
            val ratio = wantedWidth.toFloat() / wantedHeight

            val options = RequestOptions()
                    .override((wantedWidth * ratio).toInt(), wantedHeight)
                    .fitCenter()

            Glide.with(this)
                    .asBitmap()
                    .load(File(path))
                    .apply(options)
                    .into(object : SimpleTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap?, transition: Transition<in Bitmap>?) {
                            try {
                                WallpaperManager.getInstance(applicationContext).setBitmap(resource)
                                setResult(Activity.RESULT_OK)
                            } catch (ignored: IOException) {

                            }

                            finish()
                        }
                    })
        } else if (mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent) {
            Intent().apply {
                data = Uri.parse(path)
                setResult(Activity.RESULT_OK, this)
            }
            finish()
        } else {
            val file = File(path)
            val isVideo = file.isVideoFast()
            if (isVideo) {
                openWith(file, false)
            } else {
                Intent(this, ViewPagerActivity::class.java).apply {
                    putExtra(MEDIUM, path)
                    putExtra(SHOW_ALL, mShowAll)
                    startActivity(this)
                }
            }
        }
    }

    private fun gotMedia(media: ArrayList<Medium>, isFromCache: Boolean = false) {
        mLastMediaModified = getLastMediaModified()
        mIsGettingMedia = false
        media_refresh_layout.isRefreshing = false

        media_empty_text_label.beVisibleIf(media.isEmpty() && !isFromCache)
        media_empty_text.beVisibleIf(media.isEmpty() && !isFromCache)

        checkLastMediaChanged()
        if (mLastDrawnHashCode == 0)
            mLastDrawnHashCode = media.hashCode()

        if (media.hashCode() == mMedia.hashCode() && media.hashCode() == mLastDrawnHashCode)
            return

        mLastDrawnHashCode = media.hashCode()
        mMedia = media
        runOnUiThread {
            setupAdapter()
        }
        storeFolder()
    }

    private fun storeFolder() {
        if (!config.temporarilyShowHidden) {
            val subList = mMedia.subList(0, Math.min(SAVE_MEDIA_CNT, mMedia.size))
            val json = Gson().toJson(subList)
            config.saveFolderMedia(mPath, json)
        }
    }

    override fun deleteFiles(files: ArrayList<File>) {
        val filtered = files.filter { it.isImageVideoGif() } as ArrayList
        deleteFiles(filtered) {
            if (!it) {
                toast(com.amar.NoteDirector.R.string.unknown_error_occurred)
            } else if (mMedia.isEmpty()) {
                deleteDirectoryIfEmpty()
                finish()
            }
        }
    }

    override fun refreshItems() {
        getMedia()
        Handler().postDelayed({
            getMedia()
        }, 1000)
    }

    override fun itemLongClicked(position: Int) {
        media_grid.setDragSelectActive(position)
    }
    object FilePickUtils {
        private fun getPathDeprecated(ctx: Context, uri: Uri?): String? {
            if (uri == null) {
                return null
            }
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = ctx.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null) {
                val column_index = cursor
                        .getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                cursor.moveToFirst()
                return cursor.getString(column_index)
            }
            return uri.path
        }

        fun getSmartFilePath(ctx: Context, uri: Uri): String? {

            if (Build.VERSION.SDK_INT < 19) {
                return getPathDeprecated(ctx, uri)
            }
            return FilePickUtils.getPath(ctx, uri)
        }

        @SuppressLint("NewApi")
        fun getPath(context: Context, uri: Uri): String? {
            val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            // DocumentProvider
            if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
                // ExternalStorageProvider
                if (isExternalStorageDocument(uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val type = split[0]

                    if ("primary".equals(type, ignoreCase = true)) {
                        return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                    }

                    // TODO handle non-primary volumes
                } else if (isDownloadsDocument(uri)) {
                    val id = DocumentsContract.getDocumentId(uri)
                    val contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)!!)

                    return getDataColumn(context, contentUri, null, null)
                } else if (isMediaDocument(uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val type = split[0]

                    var contentUri: Uri? = null
                    if ("image" == type) {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    } else if ("video" == type) {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else if ("audio" == type) {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }

                    val selection = "_id=?"
                    val selectionArgs = arrayOf(split[1])

                    return contentUri?.let { getDataColumn(context, it, selection, selectionArgs) }
                }// MediaProvider
                // DownloadsProvider
            } else if ("content".equals(uri.scheme, ignoreCase = true)) {
                return getDataColumn(context, uri, null, null)
            } else if ("file".equals(uri.scheme, ignoreCase = true)) {
                return uri.path
            }// File
            // MediaStore (and general)

            return null
        }

        /**
         * Get the value of the data column for this Uri. This is useful for
         * MediaStore Uris, and other file-based ContentProviders.
         * @param context The context.
         * *
         * @param uri The Uri to query.
         * *
         * @param selection (Optional) Filter used in the query.
         * *
         * @param selectionArgs (Optional) Selection arguments used in the query.
         * *
         * @return The value of the _data column, which is typically a file path.
         */
        fun getDataColumn(context: Context, uri: Uri, selection: String?,
                          selectionArgs: Array<String>?): String? {
            var cursor: Cursor? = null
            val column = "_data"
            val projection = arrayOf(column)

            try {
                cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val column_index = cursor.getColumnIndexOrThrow(column)
                    return cursor.getString(column_index)
                }
            } finally {
                if (cursor != null)
                    cursor.close()
            }
            return null
        }


        /**
         * @param uri The Uri to check.
         * *
         * @return Whether the Uri authority is ExternalStorageProvider.
         */
        fun isExternalStorageDocument(uri: Uri): Boolean {
            return "com.android.externalstorage.documents" == uri.authority
        }

        /**
         * @param uri The Uri to check.
         * *
         * @return Whether the Uri authority is DownloadsProvider.
         */
        fun isDownloadsDocument(uri: Uri): Boolean {
            return "com.android.providers.downloads.documents" == uri.authority
        }

        /**
         * @param uri The Uri to check.
         * *
         * @return Whether the Uri authority is MediaProvider.
         */
        fun isMediaDocument(uri: Uri): Boolean {
            return "com.android.providers.media.documents" == uri.authority
        }

    }
}
