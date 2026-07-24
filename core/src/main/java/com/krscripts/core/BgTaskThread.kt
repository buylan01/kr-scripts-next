package com.krscripts.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import androidx.core.app.NotificationCompat
import com.krscripts.common.ui.DialogHelper
import com.krscripts.core.executor.ShellExecutor
import com.krscripts.core.model.RunnableNode
import com.krscripts.core.model.ShellHandlerBase
import java.util.concurrent.CopyOnWriteArrayList

class BgTaskThread(private var process: Process) : Thread() {
    override fun run() {
        try {
            process.waitFor()
        } catch (_: java.lang.Exception) {

        }
    }

    class ServiceShellHandler(private val context: Context, private val runnableNode: RunnableNode, private val notificationID: Int) : ShellHandlerBase() {
        private var notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        private val notificationTitle = runnableNode.title
        private var logEntries = CopyOnWriteArrayList<String>()
        private var notificationMShortMsg = ""
        private var progressCurrent = 0
        private var progressTotal = 0
        private var someIgnored = false
        private var forceStop: Runnable? = null
        private var isFinished = false
        private var stopActionName = context.packageName + ".TaskStop." + "N" + notificationID

        private val stopIntent by lazy {
            val intent = Intent(stopActionName).apply {
                putExtra("id", notificationID)
                setPackage(context.packageName)
            }
            PendingIntent.getBroadcast(
                context,
                notificationID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null && intent.hasExtra("id")) {
                    if (intent.getIntExtra("id", 0) == notificationID) {
                        forceStop?.run()
                    }
                }
            }
        }

        private fun updateNotification() {
            if (logEntries.size > 6) {
                val removed = logEntries.removeFirstOrNull()
                if (removed != null) someIgnored = true
            }

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
                setContentTitle("$notificationTitle($notificationID)")
                setContentText(notificationMShortMsg + " >> " + logEntries.lastOrNull())
                setSmallIcon(R.drawable.baseline_build_24)
                setAutoCancel(true)
                setWhen(System.currentTimeMillis())

                if (progressTotal != progressCurrent) {
                    setProgress(progressTotal, progressCurrent, progressTotal < 0)
                } else {
                    setProgress(0, 0, false)
                }

                if (runnableNode.interruptable && forceStop != null && !isFinished) {
                    addAction(
                        R.drawable.baseline_stop_circle_24,
                        context.getString(R.string.kr_stop),
                        stopIntent
                    )
                }

                if (logEntries.isNotEmpty()) {
                    setStyle(NotificationCompat.BigTextStyle().bigText(
                        (if (someIgnored) "……\n" else "") + logEntries.joinToString("")
                    ))
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!channelCreated) {
                        val channel = NotificationChannel(
                            CHANNEL_ID,
                            context.getString(R.string.kr_script_task_notification),
                            NotificationManager.IMPORTANCE_DEFAULT
                        )
                        channel.enableLights(false)
                        channel.enableVibration(false)
                        channel.setSound(null, null)
                        notificationManager.createNotificationChannel(channel)
                    }
                    channelCreated = true
                    setChannelId(CHANNEL_ID)
                } else {
                    setSound(null)
                    setVibrate(null)
                }
            }

            val notification = notificationBuilder.build()

            if (!isFinished) {
                notification.flags = Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
            }

            notificationManager.notify(notificationID, notification) // 发送通知
        }

        override fun updateLog(msg: SpannableString?) {
        }

        override fun onReader(msg: Any?) {
            logEntries.add("" + msg?.toString())
            updateNotification()
        }

        override fun onError(msg: Any?) {
            notificationMShortMsg = context.getString(R.string.kr_script_task_has_error)
            logEntries.add("" + msg?.toString())
            updateNotification()
        }

        override fun onWrite(msg: Any?) {
        }

        override fun onExit(msg: Any?) {
            try {
                //context.unregisterReceiver(receiver)
            } catch (_: Exception) {

            }
            isFinished = true
            notificationMShortMsg = context.getString(R.string.kr_script_task_finished)

            if (msg == 0) {
                logEntries.add("\n" + context.getString(R.string.kr_shell_completed))
            } else {
                logEntries.add("\n" + context.getString(R.string.kr_shell_finish_error) + " " + msg?.toString())
            }
            updateNotification()
        }

        override fun onStart(forceStop: Runnable?) {
            this.forceStop = forceStop

            val intentFilter = IntentFilter(stopActionName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, intentFilter)
            }

            updateNotification()
        }

        override fun onStart(msg: Any?) {
            notificationMShortMsg = context.getString(R.string.kr_script_task_running)
        }

        override fun onProgress(current: Int, total: Int) {
            progressCurrent = current
            progressTotal = total
            updateNotification()
        }
    }

    companion object {
        private var channelCreated = false
        private const val CHANNEL_ID = "kr_script_task_notification"
        private var notificationCounter = 34050

        fun startTask(context: Context, script: String, params: HashMap<String, String>?, nodeInfo: RunnableNode, onExit: Runnable, onDismiss: Runnable) {
            val applicationContext = context.applicationContext
            notificationCounter += 1

            val handler = ServiceShellHandler(applicationContext, nodeInfo, notificationCounter)
            ShellExecutor().execute(
                    context,
                    nodeInfo,
                    script,
                    {
                        /*
                        try {
                            process.destroy()
                        } catch (ex: java.lang.Exception) {
                        }
                        */
                        try {
                            onExit.run()
                            onDismiss.run()
                        } catch (_: Exception) {
                        }
                    },
                    params,
                    handler)

            val bundle = Bundle()
            params?.run {
                bundle.putSerializable("params", params)
            }
            DialogHelper.helpInfo(context, context.getString(R.string.kr_bg_task_start), context.getString(
                R.string.kr_bg_task_start_desc))
            // Toast.makeText(applicationContext, applicationContext.getString(R.string.kr_bg_task_start), Toast.LENGTH_SHORT).show()
        }
    }
}
