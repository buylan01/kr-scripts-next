package com.krscripts.app

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.krscripts.app.databinding.ActivityFileSelectorBinding
import com.krscripts.app.ui.adapter.AdapterFileSelector
import com.krscripts.app.ui.dialog.ProgressBarDialog
import com.krscripts.app.util.PermissionUtil.checkAccessFiles
import com.krscripts.app.util.PermissionUtil.requestAccessFilesDialog
import java.io.File

class ActivityFileSelector : AppCompatActivity() {
    companion object {
        const val MODE_FILE = 0
        const val MODE_FOLDER = 1
    }

    private var adapterFileSelector: AdapterFileSelector? = null
    var extension = ""
    var mode = MODE_FILE

    private lateinit var binding: ActivityFileSelectorBinding

    private val manageFileRequester = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        loadData()
    }

    private lateinit var backPressedCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityFileSelectorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.fileSelector) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { _ ->
            finish()
        }

        intent.extras?.run {
            if (containsKey("extension")) {
                extension = intent.extras!!.getString("extension").toString()
                if (!extension.startsWith(".")) {
                    extension = ".$extension"
                }
                if (extension.isNotEmpty()) {
                    title = "$title($extension)"
                }
            }
            if (containsKey("mode")) {
                mode = getInt("mode")
                if (mode == MODE_FOLDER) {
                    title = getString(R.string.title_activity_folder_selector)
                }
            }
        }

        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapterFileSelector?.hasParent == true) {
                    adapterFileSelector?.goParent()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        if (checkAccessFiles(this)) {
            val sdcard = File(Environment.getExternalStorageDirectory().absolutePath)
            if (sdcard.exists() && sdcard.isDirectory) {
                val list = sdcard.listFiles()
                if (list == null) {
                    Toast.makeText(applicationContext, "获取文件列表失败！", Toast.LENGTH_LONG).show()
                    return
                }
                val onSelected =  Runnable {
                    val file: File? = adapterFileSelector!!.selectedFile
                    if (file != null) {
                        this.setResult(
                            RESULT_OK,
                            Intent().apply {
                                setData(file.toUri())
                                putExtra("file", file.absolutePath)
                            }
                        )
                        this.finish()
                    }
                }
                adapterFileSelector = if (mode == MODE_FOLDER) {
                    AdapterFileSelector.folderChooser(sdcard, onSelected, ProgressBarDialog(this))
                } else {
                    AdapterFileSelector.fileChooser(sdcard, onSelected, ProgressBarDialog(this), extension)
                }

                binding.fileSelectorList.adapter = adapterFileSelector
            }
        } else {
            requestAccessFilesDialog(this, manageFileRequester) {
                this.finish()
            }
        }
    }
}
