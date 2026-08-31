package com.krscripts.app.ui.adapter

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.krscripts.app.R
import com.krscripts.app.ui.dialog.DialogHelper
import com.krscripts.app.ui.dialog.ProgressBarDialog
import java.io.File
import java.text.Collator
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

class AdapterFileSelector private constructor(
    rootDir: File,
    private val fileSelected: Runnable,
    private val progressBarDialog: ProgressBarDialog,
    extension: String?,
    private var folderChooserMode: Boolean = false
) : BaseAdapter() {

    private var items: List<Item> = emptyList()
    private var currentDir: File = rootDir
    private var hasParent: Boolean = false

    private var rootDirPath: String = rootDir.absolutePath
    private val extension: String? = extension?.let { if (it.startsWith(".")) it else ".$it" }
    private val leaveRootDir: Boolean = true

    private val loadExecutor = Executors.newSingleThreadExecutor()
    private var currentLoadTask: Future<*>? = null

    var selectedFile: File? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    private class ViewHolder(view: View) {
        val icon: ImageView = view.findViewById(R.id.ItemIcon)
        val title: TextView = view.findViewById(R.id.ItemTitle)
        val text: TextView = view.findViewById(R.id.ItemText)
    }

    private sealed class Item {
        data class ParentDir(val parent: File) : Item()
        data class FileItem(val file: File) : Item()
    }

    init {
        loadDir(rootDir)
    }

    private fun loadDir(dir: File) {

        currentLoadTask?.cancel(true)
        progressBarDialog.showDialog("加载中...", 300L)

        currentLoadTask = loadExecutor.submit {
            val parent = dir.parentFile
            val parentPath = parent?.absolutePath.orEmpty()
            val newHasParent = parent != null &&
                    parent.exists() &&
                    parent.canRead() &&
                    (leaveRootDir || !(rootDirPath.startsWith(parentPath) && rootDirPath.length > parentPath.length))

            val fileList = if (dir.exists() && dir.canRead()) {
                dir.listFiles { file ->
                    if (folderChooserMode) {
                        file.isDirectory
                    } else {
                        file.exists() && (file.isDirectory ||
                                extension.isNullOrEmpty() ||
                                file.name.endsWith(extension))
                    }
                }?.toList() ?: emptyList()
            } else {
                emptyList()
            }

            Collator.getInstance(Locale.getDefault()).apply {
                strength = Collator.PRIMARY
            }
            val sorted = fileList.sortedWith(
                compareByDescending<File> { it.isDirectory }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )

            val newItems = mutableListOf<Item>()
            if (newHasParent) {
                newItems.add(Item.ParentDir(parent))
            }
            newItems.addAll(sorted.map { Item.FileItem(it) })

            mainHandler.post {

                if (Thread.currentThread().isInterrupted) return@post
                hasParent = newHasParent
                currentDir = dir
                items = newItems
                notifyDataSetChanged()
                progressBarDialog.hideDialog()
            }
        }
    }

    fun goParent(): Boolean {
        if (hasParent) {
            loadDir(currentDir.parentFile!!)
            return true
        }
        return false
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = 0L

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder
        if (convertView == null) {
            view = LayoutInflater.from(parent.context).inflate(R.layout.file_list_item, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = convertView.tag as ViewHolder
        }

        when (
            val item = items[position]
        ) {
            is Item.ParentDir -> {
                holder.icon.setImageResource(R.drawable.baseline_folder_24)
                holder.title.text = ".."
                holder.text.visibility = View.GONE
                view.setOnClickListener { goParent() }
                view.setOnLongClickListener(null)
            }
            is Item.FileItem -> {
                val file = item.file
                if (file.isDirectory) {
                    holder.icon.setImageResource(R.drawable.baseline_folder_24)
                    holder.text.visibility = View.GONE
                    view.setOnClickListener { onDirectoryClick(view, file) }
                    view.setOnLongClickListener(
                        if (folderChooserMode) {
                            { onFileLongClick(view, file, "选定目录？") }
                        } else null
                    )
                } else {
                    holder.icon.setImageResource(R.drawable.baseline_insert_drive_file_24)
                    holder.text.text = formatFileSize(file.length())
                    holder.text.visibility = View.VISIBLE
                    view.setOnClickListener { onFileClick(view, file) }
                    view.setOnLongClickListener(null)
                }
                holder.title.text = file.name
            }
        }
        return view
    }

    private fun onDirectoryClick(view: View, dir: File) {
        if (!dir.exists()) {
            Toast.makeText(view.context, "所选的文件已被删除，请重新选择！", Toast.LENGTH_SHORT).show()
            return
        }
        val files = dir.listFiles()
        if (files.isNullOrEmpty()) {
            Snackbar.make(view, "该目录下没有文件！", Snackbar.LENGTH_SHORT).show()
        } else {
            loadDir(dir)
        }
    }

    private fun onFileClick(view: View, file: File) {
        confirmSelection(view, file, "选定文件？")
    }

    private fun onFileLongClick(view: View, file: File, title: String): Boolean {
        confirmSelection(view, file, title)
        return true
    }

    private fun confirmSelection(view: View, file: File, title: String) {
        DialogHelper.openConfirmAlert(view.context, title, file.absolutePath, Runnable {
            if (!file.exists()) {
                Toast.makeText(view.context, "所选的文件已被删除，请重新选择！", Toast.LENGTH_SHORT).show()
                return@Runnable
            }
            selectedFile = file
            fileSelected.run()
        })
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024L -> "${bytes}B"
            bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.2fKB", bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.2fMB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.getDefault(), "%.2fGB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    companion object {
        fun folderChooser(
            rootDir: File,
            fileSelected: Runnable,
            progressBarDialog: ProgressBarDialog
        ): AdapterFileSelector {
            return AdapterFileSelector(
                rootDir = rootDir,
                fileSelected = fileSelected,
                progressBarDialog = progressBarDialog,
                extension = null,
                folderChooserMode = true
            )
        }

        fun fileChooser(
            rootDir: File,
            fileSelected: Runnable,
            progressBarDialog: ProgressBarDialog,
            extension: String?
        ): AdapterFileSelector {
            return AdapterFileSelector(
                rootDir = rootDir,
                fileSelected = fileSelected,
                progressBarDialog = progressBarDialog,
                extension = extension,
                folderChooserMode = false
            )
        }
    }
}