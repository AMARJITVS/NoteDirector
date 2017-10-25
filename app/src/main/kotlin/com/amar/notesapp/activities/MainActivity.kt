package com.amar.NoteDirector.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v7.widget.GridLayoutManager
import android.widget.FrameLayout
import com.google.gson.Gson
import com.simplemobiletools.commons.dialogs.CreateNewFolderDialog
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.models.Release
import com.simplemobiletools.commons.views.MyScalableRecyclerView
import com.amar.NoteDirector.adapters.DirectoryAdapter
import com.amar.NoteDirector.asynctasks.GetDirectoriesAsynctask
import com.amar.NoteDirector.dialogs.ChangeSortingDialog
import com.amar.NoteDirector.dialogs.FilterMediaDialog
import com.amar.NoteDirector.extensions.*
import com.amar.NoteDirector.helpers.*
import com.amar.NoteDirector.models.Directory
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.util.*
import android.system.Os.mkdir
import android.os.Environment.getExternalStorageDirectory
import android.support.design.widget.FloatingActionButton
import android.util.Log
import com.amar.NoteDirector.R
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Bitmap
import android.provider.DocumentsContract
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatDialogFragment
import android.system.Os.mkdir
import android.view.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import com.amar.notesapp.activities.PrefManager
import com.amar.notesapp.activities.WelcomeActivity
import com.google.android.gms.ads.*
import kotlinx.android.synthetic.main.dialog_new_folder.view.*
import org.apache.commons.io.FileUtils
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import kotlin.collections.ArrayList


class MainActivity : SimpleActivity(), DirectoryAdapter.DirOperationsListener {
    var img: String? = null
    var gname: String? = null
    var alertbox: android.support.v7.app.AlertDialog? = null
    private val STORAGE_PERMISSION = 1
    private val IMAGE_REQUEST=1
    private val PICK_MEDIA = 2
    private val PICK_WALLPAPER = 3
    private val LAST_MEDIA_CHECK_PERIOD = 3000L

    lateinit var mDirs: ArrayList<Directory>

    private var mIsPickImageIntent = false
    private var mIsPickVideoIntent = false
    private var mIsGetImageContentIntent = false
    private var mIsGetVideoContentIntent = false
    private var mIsGetAnyContentIntent = false
    private var mIsSetWallpaperIntent = false
    private var mIsThirdPartyIntent = false
    private var mIsGettingDirs = false
    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mLoadedInitialPhotos = false
    private var mLastMediaModified = 0
    private var mLastMediaHandler = Handler()
    private lateinit var floatclick: FloatingActionButton
    private lateinit var camera: ImageButton
    private lateinit var video: ImageButton
    private lateinit var addcam: ImageButton
    private lateinit var addvid: ImageButton
    private lateinit var mAdView: AdView
    private lateinit var mInterstitialAd: InterstitialAd

    private var mCurrAsyncTask: GetDirectoriesAsynctask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.amar.NoteDirector.R.layout.activity_main)

        MobileAds.initialize(this,"ca-app-pub-5725704895776408/5945504323")
        mInterstitialAd =InterstitialAd(this)
        mInterstitialAd.setAdUnitId("ca-app-pub-5725704895776408/7196142288");
        mInterstitialAd.loadAd(AdRequest.Builder().build())
        mAdView = findViewById<View>(R.id.adView) as AdView
        val adRequest = AdRequest.Builder().addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build()
        mAdView.loadAd(adRequest)

        val folder = File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/NoteDirector")
        if (!folder.exists()) {
             folder.mkdir()
        }
        else

        mIsPickImageIntent = isPickImageIntent(intent)
        mIsPickVideoIntent = isPickVideoIntent(intent)
        mIsGetImageContentIntent = isGetImageContentIntent(intent)
        mIsGetVideoContentIntent = isGetVideoContentIntent(intent)
        mIsGetAnyContentIntent = isGetAnyContentIntent(intent)
        mIsSetWallpaperIntent = isSetWallpaperIntent(intent)
        mIsThirdPartyIntent = mIsPickImageIntent || mIsPickVideoIntent || mIsGetImageContentIntent || mIsGetVideoContentIntent ||
                mIsGetAnyContentIntent || mIsSetWallpaperIntent

        removeTempFolder()
        directories_refresh_layout.setOnRefreshListener({ getDirectories() })
        mDirs = ArrayList()
        mStoredAnimateGifs = config.animateGifs
        mStoredCropThumbnails = config.cropThumbnails
        mStoredScrollHorizontally = config.scrollHorizontally
        storeStoragePaths()

        directories_empty_text.setOnClickListener {
            showFilterMediaDialog()
        }
        floatclick = findViewById(R.id.floatbut)

        // set on-click listener
        floatclick.setOnClickListener {
            createNewFolder()
        }
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
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (mIsThirdPartyIntent) {
            menuInflater.inflate(com.amar.NoteDirector.R.menu.menu_main_intent, menu)
        } else {
            menuInflater.inflate(com.amar.NoteDirector.R.menu.menu_main, menu)
            menu.findItem(com.amar.NoteDirector.R.id.increase_column_count).isVisible = config.dirColumnCnt < 10
            menu.findItem(com.amar.NoteDirector.R.id.reduce_column_count).isVisible = config.dirColumnCnt > 1
        }

        return true
    }

    fun ins()
    {
        val prefManager = PrefManager(applicationContext)
        // make first time launch TRUE
        prefManager.setFirstTimeLaunch(true)
        startActivity(Intent(this@MainActivity, WelcomeActivity::class.java))
        finish()
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
            com.amar.NoteDirector.R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
            com.amar.NoteDirector.R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
            com.amar.NoteDirector.R.id.increase_column_count -> increaseColumnCount()
            com.amar.NoteDirector.R.id.reduce_column_count -> reduceColumnCount()
            com.amar.NoteDirector.R.id.instructions -> ins()
            com.amar.NoteDirector.R.id.settings -> ad()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        config.isThirdPartyIntent = false
        if (mStoredAnimateGifs != config.animateGifs) {
            directories_grid.adapter?.notifyDataSetChanged()
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            directories_grid.adapter?.notifyDataSetChanged()
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            directories_grid.adapter?.let {
                (it as DirectoryAdapter).scrollVertically = !config.scrollHorizontally
                it.notifyDataSetChanged()
            }
            setupScrollDirection()
        }

        tryloadGallery()
        invalidateOptionsMenu()
        floatbut.setBackgroundTintList(ColorStateList.valueOf(config.primaryColor))
        directories_empty_text_label.setTextColor(config.textColor)
        directories_empty_text.setTextColor(config.primaryColor)
    }

    override fun onPause() {
        super.onPause()
        mCurrAsyncTask?.shouldStop = true
        storeDirectories()
        directories_refresh_layout.isRefreshing = false
        mIsGettingDirs = false
        mStoredAnimateGifs = config.animateGifs
        mStoredCropThumbnails = config.cropThumbnails
        mStoredScrollHorizontally = config.scrollHorizontally
        directories_grid.listener = null
        mLastMediaHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        config.temporarilyShowHidden = false
        removeTempFolder()
    }

    private fun removeTempFolder() {
        val newFolder = File(config.tempFolderPath)
        if (newFolder.exists() && newFolder.isDirectory) {
            if (newFolder.list()?.isEmpty() == true) {
                deleteFileBg(newFolder, true) { }
            }
        }
        config.tempFolderPath = ""
    }

    private fun tryloadGallery() {
        if (hasWriteStoragePermission()) {

                getDirectories()
            setupLayoutManager()
            checkIfColorChanged()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getDirectories()
            } else {
                toast(com.amar.NoteDirector.R.string.no_storage_permissions)
                finish()
            }
        }
    }

    private fun getDirectories() {
        if (mIsGettingDirs)
            return

        mIsGettingDirs = true
        val dirs = getCachedDirectories()
        if (dirs.isNotEmpty() && !mLoadedInitialPhotos) {
            gotDirectories(dirs, true)
        }

        if (!mLoadedInitialPhotos) {
            directories_refresh_layout.isRefreshing = true
        }

        mLoadedInitialPhotos = true
        mCurrAsyncTask = GetDirectoriesAsynctask(applicationContext, mIsPickVideoIntent || mIsGetVideoContentIntent, mIsPickImageIntent || mIsGetImageContentIntent) {
            gotDirectories(addTempFolderIfNeeded(it), false)
        }
        mCurrAsyncTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, true, false) {
            getDirectories()
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            directories_refresh_layout.isRefreshing = true
            getDirectories()
        }
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
        getDirectories()
        invalidateOptionsMenu()
    }

    private fun checkIfColorChanged() {
        if (directories_grid.adapter != null && getRecyclerAdapter().primaryColor != config.primaryColor) {
            getRecyclerAdapter().primaryColor = config.primaryColor
            directories_vertical_fastscroller.updateHandleColor()
            directories_horizontal_fastscroller.updateHandleColor()
            floatbut.setBackgroundTintList(ColorStateList.valueOf(config.primaryColor))
        }
    }

    override fun tryDeleteFolders(folders: ArrayList<File>) {
        for (file in folders) {
            deleteFolders(folders) {
                runOnUiThread {
                    refreshItems()
                }
            }
        }
    }

    private fun getRecyclerAdapter() = (directories_grid.adapter as DirectoryAdapter)

    private fun setupLayoutManager() {
        val layoutManager = directories_grid.layoutManager as GridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = GridLayoutManager.HORIZONTAL
            directories_refresh_layout.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            layoutManager.orientation = GridLayoutManager.VERTICAL
            directories_refresh_layout.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        directories_grid.isDragSelectionEnabled = true
        directories_grid.isZoomingEnabled = true
        layoutManager.spanCount = config.dirColumnCnt
        directories_grid.listener = object : MyScalableRecyclerView.MyScalableRecyclerViewListener {
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

    private fun createNewFolder() {
        val view = this.layoutInflater.inflate(R.layout.dialog_new_folder, null)
        val path=Environment.getExternalStorageDirectory().absolutePath+"/NoteDirector"
        view.folder_path.text = this.humanizePath(path).trimEnd('/') + "/"

        android.support.v7.app.AlertDialog.Builder(this)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
            window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            context.setupDialogStuff(view, this, R.string.create_new_folder)
            getButton(android.support.v7.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(View.OnClickListener {
                val name = view.folder_name.value
                when {
                    name.isEmpty() -> Toast.makeText(applicationContext,"Enter cabin name !!!",Toast.LENGTH_SHORT).show()
                    name.isAValidFilename() -> {
                        val file = File(path, name)
                        if (file.exists()) {
                            Toast.makeText(applicationContext,"cabin name already exists !!!",Toast.LENGTH_SHORT).show()
                            return@OnClickListener
                        }
                        gname=name
                        createFolder(name,file, this)
                    }
                    else -> Toast.makeText(applicationContext,"Invalid name !!!",Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
    private fun createFolder(name:String,file: File, alertDialog: android.support.v7.app.AlertDialog) {
        if (this.needsStupidWritePermissions(file.absolutePath)) {
            this.handleSAFDialog(file) {
                val documentFile = this.getFileDocument(file.absolutePath)
                documentFile?.createDirectory(file.name)
            }
        } else if (file.mkdirs()) {
        }
        alertDialog.dismiss()
        media_selector(name)
    }

    private fun increaseColumnCount() {
        config.dirColumnCnt = ++(directories_grid.layoutManager as GridLayoutManager).spanCount
        invalidateOptionsMenu()
        directories_grid.adapter?.notifyDataSetChanged()
    }

    private fun reduceColumnCount() {
        config.dirColumnCnt = --(directories_grid.layoutManager as GridLayoutManager).spanCount
        invalidateOptionsMenu()
        directories_grid.adapter?.notifyDataSetChanged()
    }

    private fun isPickImageIntent(intent: Intent) = isPickIntent(intent) && (hasImageContentData(intent) || isImageType(intent))

    private fun isPickVideoIntent(intent: Intent) = isPickIntent(intent) && (hasVideoContentData(intent) || isVideoType(intent))

    private fun isPickIntent(intent: Intent) = intent.action == Intent.ACTION_PICK

    private fun isGetContentIntent(intent: Intent) = intent.action == Intent.ACTION_GET_CONTENT && intent.type != null

    private fun isGetImageContentIntent(intent: Intent) = isGetContentIntent(intent) &&
            (intent.type.startsWith("image/") || intent.type == MediaStore.Images.Media.CONTENT_TYPE)

    private fun isGetVideoContentIntent(intent: Intent) = isGetContentIntent(intent) &&
            (intent.type.startsWith("video/") || intent.type == MediaStore.Video.Media.CONTENT_TYPE)

    private fun isGetAnyContentIntent(intent: Intent) = isGetContentIntent(intent) && intent.type == "*/*"

    private fun isSetWallpaperIntent(intent: Intent?) = intent?.action == Intent.ACTION_SET_WALLPAPER

    private fun hasImageContentData(intent: Intent) = (intent.data == MediaStore.Images.Media.EXTERNAL_CONTENT_URI ||
            intent.data == MediaStore.Images.Media.INTERNAL_CONTENT_URI)

    private fun hasVideoContentData(intent: Intent) = (intent.data == MediaStore.Video.Media.EXTERNAL_CONTENT_URI ||
            intent.data == MediaStore.Video.Media.INTERNAL_CONTENT_URI)

    private fun isImageType(intent: Intent) = (intent.type?.startsWith("image/") == true || intent.type == MediaStore.Images.Media.CONTENT_TYPE)

    private fun isVideoType(intent: Intent) = (intent.type?.startsWith("video/") == true || intent.type == MediaStore.Video.Media.CONTENT_TYPE)

    private fun opencamera(imgn : String)
    {
        val uriSavedImage:Uri
        val imageIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
        val imagesFolder = File(Environment.getExternalStorageDirectory().absolutePath+"/NoteDirector/"+imgn)
        if(!imagesFolder.exists())
            imagesFolder.mkdirs()
        val imgname="NoteDirector-"+timeStamp+".jpg"
        img=Environment.getExternalStorageDirectory().absolutePath+"/NoteDirector/"+imgn+"/"+imgname
        val image = File(imagesFolder, imgname)
        if (Build.VERSION.SDK_INT < 24) {
            uriSavedImage=Uri.fromFile(image)
        }
        else
        {
            uriSavedImage =FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".provider", image)
        }
         imageIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage)
        startActivityForResult(imageIntent, IMAGE_REQUEST)
    }
    private fun openvideo(imgn : String)
    {
        val uriSavedImage:Uri
        val imageIntent = Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE)
        val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
        val imagesFolder = File(Environment.getExternalStorageDirectory().absolutePath+"/NoteDirector/"+imgn)
        if(!imagesFolder.exists())
            imagesFolder.mkdirs()
        val imgname="NoteDirector-"+timeStamp+".mp4"
        img=Environment.getExternalStorageDirectory().absolutePath+"/NoteDirector/"+imgn+"/"+imgname
        val image = File(imagesFolder, imgname)
        if (Build.VERSION.SDK_INT < 24) {
            uriSavedImage=Uri.fromFile(image)
        }
        else
        {
            uriSavedImage =FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".provider", image)
        }
        imageIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage)
        startActivityForResult(imageIntent, IMAGE_REQUEST)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICK_MEDIA && resultData?.data != null) {
                Intent().apply {
                    val path = resultData.data.path
                    val uri = Uri.fromFile(File(path))
                    if (mIsGetImageContentIntent || mIsGetVideoContentIntent || mIsGetAnyContentIntent) {
                        if (intent.extras?.containsKey(MediaStore.EXTRA_OUTPUT) == true) {
                            var inputStream: InputStream? = null
                            var outputStream: OutputStream? = null
                            try {
                                val output = intent.extras.get(MediaStore.EXTRA_OUTPUT) as Uri
                                inputStream = FileInputStream(File(path))
                                outputStream = contentResolver.openOutputStream(output)
                                inputStream.copyTo(outputStream)
                            } catch (ignored: FileNotFoundException) {
                            } finally {
                                inputStream?.close()
                                outputStream?.close()
                            }
                        } else {
                            val type = File(path).getMimeType("image/jpeg")
                            setDataAndTypeAndNormalize(uri, type)
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        }
                    } else if (mIsPickImageIntent || mIsPickVideoIntent) {
                        data = uri
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }

                    setResult(Activity.RESULT_OK, this)
                }
                finish()
            } else if (requestCode == PICK_WALLPAPER) {
                setResult(Activity.RESULT_OK)
                finish()
            }
            else if(requestCode==IMAGE_REQUEST)
            {
                Toast.makeText(applicationContext,"Cabin created",Toast.LENGTH_SHORT).show()
                val imagepath = File(img)
                val mPath = Uri.fromFile(imagepath)
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.setData(mPath)
                this.sendBroadcast(mediaScanIntent)
                Handler(Looper.getMainLooper()).postDelayed(object:Runnable {
                    override fun run() {
                        getDirectories()
                    }
                }, 2000)
            }
            else if(requestCode==10)
            {
                 if(resultData?.getData()!=null){

                    val mImageUri=resultData.getData()
                     copyimagefile(FilePickUtils.getSmartFilePath(this,mImageUri),gname)

                }else{
                    if(resultData?.getClipData()!=null){
                        val mClipData=resultData.getClipData()
                       val mArrayUri=ArrayList<Uri>()
                        for(i in 0..mClipData.getItemCount()-1){

                            val item = mClipData.getItemAt(i)
                            val uri = item.getUri()
                            mArrayUri.add(uri)

                        }
                        for(i in 0..mArrayUri.size-1)
                        {
                            copyimagefile(FilePickUtils.getSmartFilePath(this,mArrayUri[i]),gname)
                        }
                    }

                }
                Toast.makeText(applicationContext,"Cabin created",Toast.LENGTH_SHORT).show()
                Toast.makeText(this,"Added!!!",Toast.LENGTH_SHORT).show()

            }
            else if(requestCode==11)
            {
                if(resultData?.getData()!=null){

                    val mImageUri=resultData.getData()
                    copyvideofile(FilePickUtils.getSmartFilePath(this,mImageUri),gname)

                }else{
                    if(resultData?.getClipData()!=null){
                        val mClipData=resultData.getClipData()
                        val mArrayUri=ArrayList<Uri>()
                        for(i in 0..mClipData.getItemCount()-1){

                            val item = mClipData.getItemAt(i)
                            val uri = item.getUri()
                            mArrayUri.add(uri)

                        }
                        for(i in 0..mArrayUri.size-1)
                        {
                            copyvideofile(FilePickUtils.getSmartFilePath(this,mArrayUri[i]),gname)
                        }
                    }

                }

                Toast.makeText(applicationContext,"Cabin created",Toast.LENGTH_SHORT).show()
                Toast.makeText(this,"Added!!!",Toast.LENGTH_SHORT).show()

            }
        }
        else if (resultCode == RESULT_CANCELED) {
            if(requestCode==IMAGE_REQUEST) {
                val p = img!!.lastIndexOf("/")
                val folder = img?.substring(0, p)
                val dir = File(folder)
                if (dir.exists()) {
                    dir.delete()
                }
            }
                else if( requestCode==10||requestCode==11) {
                    val folder = Environment.getExternalStorageDirectory().absolutePath+"/NoteDirector/"+gname
                    val dir = File(folder)
                    if (dir.exists()) {
                        dir.delete()
                    }
                }

        }
        super.onActivityResult(requestCode, resultCode, resultData)
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

        val imagepath = File(img)
        val mPath = Uri.fromFile(imagepath)
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.setData(mPath)
        this.sendBroadcast(mediaScanIntent)
        Handler(Looper.getMainLooper()).postDelayed(object:Runnable {
            override fun run() {
                getDirectories()
            }
        }, 2000)
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
            if (destinations != null && source != null) {
                destinations.transferFrom(sources, 0, sources.size())
            }
            if (sources != null) {
                sources.close()
            }
            if (destinations != null) {
                destinations.close()
            }
        }

        val imagepath = File(img)
        val mPath = Uri.fromFile(imagepath)
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.setData(mPath)
        this.sendBroadcast(mediaScanIntent)
        Handler(Looper.getMainLooper()).postDelayed(object:Runnable {
            override fun run() {
                getDirectories()
            }
        }, 2000)
    }
    private fun itemClicked(path: String) {
        Intent(this, MediaActivity::class.java).apply {
            putExtra(DIRECTORY, path)

            if (mIsSetWallpaperIntent) {
                putExtra(SET_WALLPAPER_INTENT, true)
                startActivityForResult(this, PICK_WALLPAPER)
            } else {
                putExtra(GET_IMAGE_INTENT, mIsPickImageIntent || mIsGetImageContentIntent)
                putExtra(GET_VIDEO_INTENT, mIsPickVideoIntent || mIsGetVideoContentIntent)
                putExtra(GET_ANY_INTENT, mIsGetAnyContentIntent)
                startActivityForResult(this, PICK_MEDIA)
            }
        }
    }

    private fun gotDirectories(dirs: ArrayList<Directory>, isFromCache: Boolean) {
        mLastMediaModified = getLastMediaModified()
        directories_refresh_layout.isRefreshing = false
        mIsGettingDirs = false

        directories_empty_text_label.beVisibleIf(dirs.isEmpty() && !isFromCache)
        directories_empty_text.beVisibleIf(dirs.isEmpty() && !isFromCache)

        checkLastMediaChanged()
        if (dirs.hashCode() == mDirs.hashCode())
            return

        mDirs = dirs

        runOnUiThread {
            setupAdapter()
        }

        storeDirectories()
    }

    private fun storeDirectories() {
        if (!config.temporarilyShowHidden && config.tempFolderPath.isEmpty()) {
            val directories = Gson().toJson(mDirs)
            config.directories = directories
        }
    }

    private fun setupAdapter() {
        val currAdapter = directories_grid.adapter
        if (currAdapter == null) {
            directories_grid.adapter = DirectoryAdapter(this, mDirs, this, isPickIntent(intent) || isGetAnyContentIntent(intent)) {
                itemClicked(it.path)
            }
        } else {
            (currAdapter as DirectoryAdapter).updateDirs(mDirs)
        }
        setupScrollDirection()
    }

    private fun setupScrollDirection() {
        directories_refresh_layout.isEnabled = !config.scrollHorizontally

        directories_vertical_fastscroller.isHorizontal = false
        directories_vertical_fastscroller.beGoneIf(config.scrollHorizontally)

        directories_horizontal_fastscroller.isHorizontal = true
        directories_horizontal_fastscroller.beVisibleIf(config.scrollHorizontally)

        if (config.scrollHorizontally) {
            directories_horizontal_fastscroller.setViews(directories_grid, directories_refresh_layout)
        } else {
            directories_vertical_fastscroller.setViews(directories_grid, directories_refresh_layout)
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
                        getDirectories()
                    }
                } else {
                    checkLastMediaChanged()
                }
            }).start()
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    override fun refreshItems() {
        getDirectories()
    }

    override fun itemLongClicked(position: Int) {
        directories_grid.setDragSelectActive(position)
    }


    private fun media_selector(imgn :String) {
        val view = this.layoutInflater.inflate(R.layout.cam_vid_selector, null)
        android.support.v7.app.AlertDialog.Builder(this)
                .create().apply {
            window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            context.setupDialogStuff(view, this, R.string.media_selector)
            camera(this)
            this.setCanceledOnTouchOutside(false)
            this.setOnCancelListener(DialogInterface.OnCancelListener {
                val folder = Environment.getExternalStorageDirectory().absolutePath+"/NoteDirector/"+gname
                val dir = File(folder)
                if (dir.exists()) {
                    dir.delete()
                }
            })
            camera = findViewById<ImageButton>(R.id.camera) as ImageButton
            camera.setOnClickListener {
                alertbox?.dismiss()
                opencamera(imgn)
            }
            video = findViewById<ImageButton>(R.id.video) as ImageButton
            video.setOnClickListener {
                alertbox?.dismiss()
                openvideo(imgn)
            }
            addcam = findViewById<ImageButton>(R.id.addcam) as ImageButton
            addcam.setOnClickListener {
                alertbox?.dismiss()
                ImageFromCard()
            }
            addvid = findViewById<ImageButton>(R.id.addvid) as ImageButton
            addvid.setOnClickListener {
                alertbox?.dismiss()
                VideoFromCard()
            }
        }
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
    private fun camera( alertDialog: android.support.v7.app.AlertDialog) {
       alertbox=alertDialog
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
