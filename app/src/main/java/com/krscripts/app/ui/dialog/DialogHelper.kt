package com.krscripts.app.ui.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krscripts.app.R

class DialogHelper {
    data class DialogButton(
        val text: String,
        val dismiss: Boolean = true,
        val onClick: Runnable? = null
    )

    companion object {
        fun animDialog(
            context: Context,
            builder: AlertDialog.Builder
        ): Dialog {
            val dialog = builder
                .setOnDismissListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        (context as? Activity)?.window?.decorView?.setRenderEffect(null)
                    }
                }
                .create()

            dialog.show()

            if (context is Activity) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.window.decorView.setRenderEffect(
                        RenderEffect.createBlurEffect(48f, 48f, Shader.TileMode.CLAMP)
                    )
                }
            }

            return dialog
        }

        fun openInfoAlert(
            context: Context,
            title: String,
            message: String,
            onDismiss: Runnable? = null
        ): Dialog {

            val dialog = showDialog(
                context,
                null,
                title = title,
                message = message,
                themeId = 0,
                cancelable = true,
                dismissButton = DialogButton(text = "知道了", onClick = onDismiss),
                confirmButton = DialogButton(text = "")
            )

            return dialog
        }

        fun openConfirmAlert(
            context: Context,
            title: String = "",
            message: String = "",
            onConfirm: Runnable? = null
        ): Dialog {

            val dialog = showDialog(
                context,
                null,
                title = title,
                message = message,
                themeId = 0,
                cancelable = true,
                dismissButton = DialogButton(text = "取消"),
                confirmButton = DialogButton(text = "确定", onClick = onConfirm)
            )

            return dialog
        }

        fun showDialog(
            context: Context,
            view: View?,
            cancelable: Boolean = true,
            title: String,
            message: String,
            themeId: Int = R.style.CustomDialogThemeOverlay,
            confirmButton: DialogButton? = null,
            dismissButton: DialogButton? = null,
            onConfirm: Runnable? = null
        ): Dialog {
            val dialog = MaterialAlertDialogBuilder(context, themeId)
                .setTitle(title)
                .setView(view)
                .setCancelable(cancelable)
                .setNegativeButton(dismissButton?.text ?: "取消") { dialog, _ ->
                    dismissButton?.onClick?.run()
                    if (dismissButton?.dismiss != false) dialog.dismiss()
                }
                .setPositiveButton(confirmButton?.text ?: "确定") { dialog, _ ->
                    confirmButton?.onClick?.run()
                    onConfirm?.run()
                    if (confirmButton?.dismiss != false) dialog.dismiss()
                }
                .setOnDismissListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        (context as? Activity)?.window?.decorView?.setRenderEffect(null)
                    }
                }
                .create()

            dialog.setMessage(message.takeIf { it.isNotEmpty() })
            dialog.setCanceledOnTouchOutside(cancelable)

            dialog.show()

            dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)

            if (context is Activity) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.window.decorView.setRenderEffect(
                        RenderEffect.createBlurEffect(48f, 48f, Shader.TileMode.CLAMP)
                    )
                }
            }

            return dialog
        }

        fun showFullScreenDialog(
            context: Context,
            view: View?,
            cancelable: Boolean = true,
            title: String,
            message: String,
            themeId: Int = R.style.dialog_full_screen,
            confirmButton: DialogButton? = null,
            dismissButton: DialogButton? = null,
            onConfirm: Runnable? = null
        ): Dialog {
            val dialog = AlertDialog.Builder(context, themeId)
                .setTitle(title)
                .setView(view)
                .setCancelable(cancelable)
                .setNegativeButton(dismissButton?.text ?: "取消") { dialog, _ ->
                    dismissButton?.onClick?.run()
                    if (dismissButton?.dismiss != false) dialog.dismiss()
                }
                .setPositiveButton(confirmButton?.text ?: "确定") { dialog, _ ->
                    confirmButton?.onClick?.run()
                    onConfirm?.run()
                    if (confirmButton?.dismiss != false) dialog.dismiss()
                }
                .create()

            dialog.setCanceledOnTouchOutside(cancelable)

            dialog.show()

            return dialog
        }
    }
}
