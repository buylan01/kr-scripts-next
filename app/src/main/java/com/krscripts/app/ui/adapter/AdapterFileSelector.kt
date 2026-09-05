package com.krscripts.app.ui.adapter

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.krscripts.app.R
import com.krscripts.app.ui.dialog.DialogHelper
import com.krscripts.app.ui.dialog.ProgressBarDialog
import java.io.File
import java.text.Collator
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

class AdapterFileSelector(
    rootDir: File,
    private val fileSelected: Runnable,
    private val progressBarDialog: ProgressBarDialog,
    extension: String?,
    private var folderChooserMode: Boolean = false
) : RecyclerView.Adapter<AdapterFileSelector.ViewHolder>() {

    private var items: List<Item> = emptyList()
    private var currentDir: File = rootDir
    var hasParent: Boolean = false
        private set

    private var rootDirPath: String = rootDir.absolutePath
    private val extension: String? = extension?.let { if (it.startsWith(".")) it else ".$it" }
    private val leaveRootDir: Boolean = true

    private val loadExecutor = Executors.newSingleThreadExecutor()
    private var currentLoadTask: Future<*>? = null

    var selectedFile: File? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
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

            val newItems = buildList {
                if (newHasParent) {
                    add(Item.ParentDir(parent))
                }
                addAll(sorted.map { Item.FileItem(it) })
            }

            mainHandler.post {

                if (Thread.currentThread().isInterrupted) return@post
                val oldSize = items.size
                hasParent = newHasParent
                currentDir = dir
                items = newItems

                if (oldSize > 0) {
                    notifyItemRangeRemoved(0, oldSize)
                }
                if (items.isNotEmpty()) {
                    notifyItemRangeInserted(0, items.size)
                }

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

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = 0L

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.file_list_item, parent, false)
        val holder = ViewHolder(view)
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val view = holder.itemView
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
                            { onFileLongClick(view, file) }
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

    private fun onFileLongClick(view: View, file: File): Boolean {
        confirmSelection(view, file, "选定目录？")
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
}