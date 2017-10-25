package com.amar.NoteDirector.adapters

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.AsyncTask
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.support.v7.view.ActionMode
import android.support.v7.widget.RecyclerView
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseArray
import android.view.*
import android.widget.Toast
import com.bignerdranch.android.multiselector.ModalMultiSelectorCallback
import com.bignerdranch.android.multiselector.MultiSelector
import com.bignerdranch.android.multiselector.SwappingHolder
import com.bumptech.glide.Glide
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.PropertiesDialog
import com.simplemobiletools.commons.dialogs.RenameItemDialog
import com.amar.NoteDirector.activities.SimpleActivity
import com.amar.NoteDirector.extensions.*
import com.amar.NoteDirector.models.Medium
import kotlinx.android.synthetic.main.photo_video_item.view.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import com.afollestad.materialdialogs.MaterialDialog
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.PageSize
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.PdfWriter
import com.amar.NoteDirector.R
import com.simplemobiletools.commons.extensions.*
import kotlinx.android.synthetic.main.dialog_new_folder.view.*
import kotlinx.android.synthetic.main.dialog_pdf_conversion.view.*
import java.io.ByteArrayOutputStream

public class MediaAdapter(val activity: SimpleActivity, var media: MutableList<Medium>, val listener: MediaOperationsListener?, val isPickIntent: Boolean,
                   val itemClick: (Medium) -> Unit) : RecyclerView.Adapter<MediaAdapter.ViewHolder>() {

    val multiSelector = MultiSelector()
    val config = activity.config

    var actMode: ActionMode? = null
    var itemViews = SparseArray<View>()
    val selectedPositions = HashSet<Int>()
    val selpos=ArrayList<Int>()
    var primaryColor = config.primaryColor
    var displayFilenames = config.displayFileNames
    var scrollVertically = !config.scrollHorizontally
    val imgUri = ArrayList<String>()
    private var mMorphCounter1 = 1
    lateinit var path:String
    lateinit var filename:String
    lateinit var image:Image

    fun toggleItemSelection(select: Boolean, pos: Int) {
        if (select) {
            itemViews[pos]?.medium_check?.background?.setColorFilter(primaryColor, PorterDuff.Mode.SRC_IN)
            selectedPositions.add(pos)
            if(!selpos.contains(pos))
            selpos.add(pos)
        } else {
            selectedPositions.remove(pos)
            if(selpos.contains(pos))
            selpos.remove(pos)
        }

        itemViews[pos]?.medium_check?.beVisibleIf(select)

        if (selectedPositions.isEmpty()) {
            actMode?.finish()
            return
        }

        updateTitle(selectedPositions.size)
    }

    fun updateTitle(cnt: Int) {
        actMode?.title = "$cnt / ${media.size}"
        actMode?.invalidate()
    }

    val adapterListener = object : MyAdapterListener {
        override fun toggleItemSelectionAdapter(select: Boolean, position: Int) {
            toggleItemSelection(select, position)
        }

        override fun getSelectedPositions(): HashSet<Int> = selectedPositions
    }

    val multiSelectorMode = object : ModalMultiSelectorCallback(multiSelector) {
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                com.amar.NoteDirector.R.id.cab_properties -> showProperties()
                com.amar.NoteDirector.R.id.cab_rename -> renameFile()
                com.amar.NoteDirector.R.id.cab_share -> shareMedia()
                com.amar.NoteDirector.R.id.cab_copy_to -> copyMoveTo(true)
                com.amar.NoteDirector.R.id.cab_move_to -> copyMoveTo(false)
                com.amar.NoteDirector.R.id.cab_select_all -> selectAll()
                com.amar.NoteDirector.R.id.cab_delete -> askConfirmDelete()
                else -> return false
            }
            return true
        }

        override fun onCreateActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
            super.onCreateActionMode(actionMode, menu)
            actMode = actionMode
            activity.menuInflater.inflate(com.amar.NoteDirector.R.menu.cab_media, menu)
            return true
        }

        override fun onPrepareActionMode(actionMode: ActionMode?, menu: Menu): Boolean {
            menu.findItem(com.amar.NoteDirector.R.id.cab_rename).isVisible = selectedPositions.size <= 1

            return true
        }

        override fun onDestroyActionMode(actionMode: ActionMode?) {
            super.onDestroyActionMode(actionMode)
            selectedPositions.forEach {
                itemViews[it]?.medium_check?.beGone()
            }
            selectedPositions.clear()
            actMode = null
        }

    }

    private fun showProperties() {
        if (selectedPositions.size <= 1) {
            PropertiesDialog(activity, media[selectedPositions.first()].path, config.shouldShowHidden)
        } else {
            val paths = ArrayList<String>()
            selectedPositions.forEach { paths.add(media[it].path) }
            PropertiesDialog(activity, paths, config.shouldShowHidden)
        }
    }

    private fun renameFile() {
        RenameItemDialog(activity, getCurrentFile().absolutePath) {
            activity.runOnUiThread {
                listener?.refreshItems()
                actMode?.finish()
            }
        }
    }


    private fun shareMedia() {
        if (selectedPositions.size == 1 && selectedPositions.first() != -1) {
            activity.shareMedium(getsharepdf1()[0])
        } else if (selectedPositions.size > 1) {
            pdfdialog()
        }
    }
    private fun pdfdialog()
    {
        val view = activity.layoutInflater.inflate(R.layout.dialog_pdf_conversion, null)
        android.support.v7.app.AlertDialog.Builder(activity)
                .setPositiveButton(R.string.yes, null)
                .setNegativeButton(R.string.no, null)
                .create().apply {
            window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            context.setupDialogStuff(view, this, R.string.app_name)
            getButton(android.support.v7.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(View.OnClickListener {
                getsharepdf()
                this.dismiss()
            })
            getButton(android.support.v7.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener(View.OnClickListener {
                activity.shareMedia(getsharepdf2())
                this.dismiss()
            })
        }
    }
    private fun getsharepdf1(): List<Medium> {
        val selectedMedia = ArrayList<Medium>(selectedPositions.size)
        selectedPositions.forEach { selectedMedia.add(media[it]) }
        return selectedMedia
    }
    private fun getsharepdf2(): List<Medium> {
        val selectedMedia = ArrayList<Medium>(selectedPositions.size)
        selectedPositions.forEach { selectedMedia.add(media[it]) }
        return selectedMedia
    }
    private fun getsharepdf(): List<Medium> {
        val selectedMedia = ArrayList<Medium>(selpos.size)
        val tempvid = ArrayList<Medium>()
        selpos.forEach { selectedMedia.add(media[it]) }
        for (st in selectedMedia) {
            if(media.map { st.video }[0]) {
               tempvid.add(st)
            }
            }
        for(a in tempvid)
        {
            selectedMedia.remove(a)
        }
        for (st in selectedMedia) {
                imgUri.add( media.map { st.path }[0])
        }
        createPdf()
        return selectedMedia
    }
    fun createPdf() {
        if (imgUri.size == 0) {
            Toast.makeText(activity, "No Images selected", Toast.LENGTH_LONG).show()
        } else {

            val view = activity.layoutInflater.inflate(R.layout.dialog_new_folder, null)
            val path = Environment.getExternalStorageDirectory().absolutePath + "/NoteDirector/PDF-Files/"
            view.folder_path.text = "Enter PDF name"
            android.support.v7.app.AlertDialog.Builder(activity)
                    .setPositiveButton(R.string.ok, null)
                    .setNegativeButton(R.string.cancel, null)
                    .create().apply {
                window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                context.setupDialogStuff(view, this, R.string.create_new_pdf)
                getButton(android.support.v7.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(View.OnClickListener {
                    val name = view.folder_name.value
                    when {
                        name.isEmpty() -> Toast.makeText(activity, "Name cannot be blank", Toast.LENGTH_LONG).show()
                        name.isAValidFilename() -> {
                            val file = File(path+"NoteDirector-"+name+".pdf")
                            if (file.exists()) {
                                Toast.makeText(activity,"File name already exists !!!",Toast.LENGTH_LONG).show()
                                return@OnClickListener
                            }
                            filename = "NoteDirector-"+view.folder_name.value
                            creatingPDF().execute()
                            if (mMorphCounter1 == 0) {
                                mMorphCounter1++
                            }
                            this.dismiss()
                        }
                        else -> Toast.makeText(activity.applicationContext, "Invalid name !!!", Toast.LENGTH_LONG).show()
                    }
                })
            }
        }
    }

 inner class creatingPDF: AsyncTask<String, String, String>() {
        // Progress dialog
        internal var builder = MaterialDialog.Builder(activity)
                .title("Please Wait")
                .content("Creating PDF. This may take a while.")
                .cancelable(false)
                .progress(true, 0)
        internal var dialog = builder.build()
        override fun onPreExecute() {
            super.onPreExecute()
            dialog.show()
        }
        override fun doInBackground(vararg params:String):String {
            val folder = File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/NoteDirector/PDF-Files/")
            if (!folder.exists())
            {
                folder.mkdir()
            }
            path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/NoteDirector/PDF-Files/"
            path = path + filename + ".pdf"
            val document = Document(PageSize.A4, 38.0f, 38.0f, 50.0f, 38.0f)
            val documentRect = document.getPageSize()
            try
            {
                PdfWriter.getInstance(document, FileOutputStream(path))
                document.open()
                for (i in 0..imgUri.size - 1)
                {
                    val bmp = BitmapFactory.decodeFile(imgUri.get(i))
//                    val stream = ByteArrayOutputStream()
//                    bmp.compress(Bitmap.CompressFormat.PNG, 70, stream)
                    image = Image.getInstance(imgUri.get(i))
                    if (bmp.getWidth() > documentRect.getWidth() || bmp.getHeight() > documentRect.getHeight())
                    {
                        //bitmap is larger than page,so set bitmap's size similar to the whole page
                        image.scaleAbsolute(documentRect.getWidth(), documentRect.getHeight())
                    }
                    else
                    {
                        //bitmap is smaller than page, so add bitmap simply.[note: if you want to fill page by stretching image, you may set size similar to page as above]
                        image.scaleAbsolute(bmp.getWidth()*1.0f, bmp.getHeight()*1.0f)
                    }
                    bmp.recycle()
                    image.setAbsolutePosition((documentRect.getWidth() - image.getScaledWidth()) / 2, (documentRect.getHeight() - image.getScaledHeight()) / 2)
                    image.setBorder(Image.BOX)
                    image.setBorderWidth(15.0f)
                    document.add(image)
                    document.newPage()
                    System.gc()
                }

            }
            catch (e:Exception) {
                e.printStackTrace()
            }
            document.close()
            imgUri.clear()
            return ""
        }
        override fun onPostExecute(s:String) {
            super.onPostExecute(s)
            dialog.dismiss()
            activity.sharepdf(path)
        }
    }

    private fun copyMoveTo(isCopyOperation: Boolean) {
        val files = ArrayList<File>()
        selectedPositions.forEach { files.add(File(media[it].path)) }

        activity.tryCopyMoveFilesTo(files, isCopyOperation) {
            config.tempFolderPath = ""
            if (!isCopyOperation) {
                listener?.refreshItems()
            }
            actMode?.finish()
        }
    }

    fun selectAll() {
        val cnt = media.size
        for (i in 0..cnt - 1) {
            selectedPositions.add(i)
            multiSelector.setSelected(i, 0, true)
            notifyItemChanged(i)
        }
        updateTitle(cnt)
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(activity) {
            deleteFiles()
            actMode?.finish()
        }
    }

    private fun getCurrentFile() = File(media[selectedPositions.first()].path)

    private fun deleteFiles() {
        if (selectedPositions.isEmpty())
            return

        val files = ArrayList<File>(selectedPositions.size)
        val removeMedia = ArrayList<Medium>(selectedPositions.size)

        if (media.size <= selectedPositions.first()) {
            actMode?.finish()
            return
        }

        activity.handleSAFDialog(File(media[selectedPositions.first()].path)) {
            selectedPositions.sortedDescending().forEach {
                val medium = media[it]
                files.add(File(medium.path))
                removeMedia.add(medium)
                notifyItemRemoved(it)
                itemViews.put(it, null)
            }

            media.removeAll(removeMedia)
            selectedPositions.clear()
            listener?.deleteFiles(files)

            val newItems = SparseArray<View>()
            var curIndex = 0
            for (i in 0..itemViews.size() - 1) {
                if (itemViews[i] != null) {
                    newItems.put(curIndex, itemViews[i])
                    curIndex++
                }
            }

            itemViews = newItems
        }
    }



    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent?.context).inflate(com.amar.NoteDirector.R.layout.photo_video_item, parent, false)
        return ViewHolder(view, adapterListener, activity, multiSelectorMode, multiSelector, listener, isPickIntent, itemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        itemViews.put(position, holder.bindView(media[position], displayFilenames, scrollVertically))
        toggleItemSelection(selectedPositions.contains(position), position)
        holder.itemView.tag = holder
    }

    override fun onViewRecycled(holder: ViewHolder?) {
        super.onViewRecycled(holder)
        holder?.stopLoad()
    }

    override fun getItemCount() = media.size

    fun updateMedia(newMedia: ArrayList<Medium>) {
        media = newMedia
        notifyDataSetChanged()
    }

    fun updateDisplayFilenames(display: Boolean) {
        displayFilenames = display
        notifyDataSetChanged()
    }

    fun selectItem(pos: Int) {
        toggleItemSelection(true, pos)
    }

    fun selectRange(from: Int, to: Int, min: Int, max: Int) {
        if (from == to) {
            (min..max).filter { it != from }
                    .forEach { toggleItemSelection(false, it) }
            return
        }

        if (to < from) {
            for (i in to..from)
                toggleItemSelection(true, i)

            if (min > -1 && min < to) {
                (min until to).filter { it != from }
                        .forEach { toggleItemSelection(false, it) }
            }
            if (max > -1) {
                for (i in from + 1..max)
                    toggleItemSelection(false, i)
            }
        } else {
            for (i in from..to)
                toggleItemSelection(true, i)

            if (max > -1 && max > to) {
                (to + 1..max).filter { it != from }
                        .forEach { toggleItemSelection(false, it) }
            }

            if (min > -1) {
                for (i in min until from)
                    toggleItemSelection(false, i)
            }
        }
    }

    class ViewHolder(val view: View, val adapterListener: MyAdapterListener, val activity: SimpleActivity, val multiSelectorCallback: ModalMultiSelectorCallback,
                     val multiSelector: MultiSelector, val listener: MediaOperationsListener?, val isPickIntent: Boolean, val itemClick: (Medium) -> (Unit)) :
            SwappingHolder(view, MultiSelector()) {
        fun bindView(medium: Medium, displayFilenames: Boolean, scrollVertically: Boolean): View {
            itemView.apply {
                play_outline.visibility = if (medium.video) View.VISIBLE else View.GONE
                photo_name.beVisibleIf(displayFilenames)
                photo_name.text = medium.name
                activity.loadImage(medium.path, medium_thumbnail, scrollVertically)

                setOnClickListener { viewClicked(medium) }
                setOnLongClickListener { if (isPickIntent) viewClicked(medium) else viewLongClicked(); true }
            }
            return itemView
        }

        private fun viewClicked(medium: Medium) {
            if (multiSelector.isSelectable) {
                val isSelected = adapterListener.getSelectedPositions().contains(layoutPosition)
                adapterListener.toggleItemSelectionAdapter(!isSelected, layoutPosition)
            } else {
                itemClick(medium)
            }
        }

        private fun viewLongClicked() {
            if (listener != null) {
                if (!multiSelector.isSelectable) {
                    activity.startSupportActionMode(multiSelectorCallback)
                    adapterListener.toggleItemSelectionAdapter(true, layoutPosition)
                }

                listener.itemLongClicked(layoutPosition)
            }
        }

        fun stopLoad() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)
                return

            Glide.with(activity).clear(view.medium_thumbnail)
        }
    }

    interface MyAdapterListener {
        fun toggleItemSelectionAdapter(select: Boolean, position: Int)

        fun getSelectedPositions(): HashSet<Int>
    }

    interface MediaOperationsListener {
        fun refreshItems()

        fun deleteFiles(files: ArrayList<File>)

        fun itemLongClicked(position: Int)
    }
}
