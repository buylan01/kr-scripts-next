package com.krscripts.common.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krscripts.common.R
import com.krscripts.common.shell.KeepShellPublic
import com.krscripts.common.ui.DialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener
import kotlin.coroutines.resume

object PermissionUtil {

    const val REQUEST_CODE_SHIZUKU: Int = 1360

    fun requestAccessFilesDialog(
        context: Activity,
        manageFileRequester: ActivityResultLauncher<Intent>? = null,
        onSkip: () -> Unit = { }
    ) {
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle("权限缺失")
            .setMessage("请授予文件管理权限")
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
                context.finishAffinity()
            }
            .setNeutralButton(R.string.btn_skip) { _, _ ->
                onSkip()
            }
            .setCancelable(false)
        DialogHelper.animDialog(context, builder)
    }

    fun checkAccessFiles(context: Context): Boolean {
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

    suspend fun ensureShizukuPermission(): Boolean {
        if (Shizuku.isPreV11()) {
            return false
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            return true
        }

        return suspendCancellableCoroutine { continuation ->
            val listener = object : OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)

            continuation.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
        }
    }

    suspend fun getPermissionType(): PermissionType {

        var permissionType = PermissionType.NONE

        withContext(Dispatchers.IO) {
            if (KeepShellPublic.checkRoot()) permissionType = PermissionType.ROOT
        }
        if (permissionType == PermissionType.ROOT) {
            return permissionType
        }

        if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                permissionType = PermissionType.ADB_ROOT
                return permissionType
            }
        }

        return PermissionType.NONE
    }
}