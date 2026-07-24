package com.krscripts.core.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krscripts.core.R
import kotlin.system.exitProcess

object PermissionUtil {

    fun showManageFileDialog(
        context: Activity,
        manageFileRequester: ActivityResultLauncher<Intent>? = null,
        onSkip: () -> Unit = { }
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle("请授予文件管理权限")
            .setPositiveButton("授予") { _, _ ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    if (manageFileRequester != null)
                        manageFileRequester.launch(intent)
                    else {
                        context.startActivity(intent)
                    }
                } else {
                    ActivityCompat.requestPermissions(
                        context,
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ),
                        0x11
                    )
                }
            }
            .setNegativeButton(R.string.btn_exit) { _, _ ->
                exitProcess(0)
            }
            .setNeutralButton(R.string.btn_skip) { _, _ ->
                onSkip()
            }
            .setCancelable(false)
            .show()
    }

    fun checkManageFile(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            checkPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    checkPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun checkPermission(context: Context, permission: String): Boolean {
        return PermissionChecker.checkSelfPermission(
            context,
            permission
        ) == PermissionChecker.PERMISSION_GRANTED
    }
}