package com.krscripts.app.shell

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.krscripts.app.R
import com.krscripts.app.executor.ShellExecutor
import com.krscripts.app.model.RunnableNode
import com.krscripts.app.ui.dialog.DialogHelper
import com.krscripts.app.util.PermissionUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

class ShellBackground {

    class TaskNotificationController(
        private val context: Context,
        private val shellEventSource: ShellEventSource,
        private val runnableNode: RunnableNode,
        private val notificationID: Int
    ) {
        private var notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        private val notificationTitle = runnableNode.title
        private var logEntries = CopyOnWriteArrayList<String>()
        var progressCurrent = 0
        var progressTotal = 0
        private var someIgnored = false
        private var forceStop: Runnable? = null
        private var isFinished = false
        private var stopActionName = context.packageName + ".backgroundTask." + notificationID + ".stop"

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

        fun updateNotification() {
            if (logEntries.size > 6) {
                val removed = logEntries.removeFirstOrNull()
                if (removed != null) someIgnored = true
            }

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
                setContentTitle(notificationTitle)
                setContentText(logEntries.lastOrNull())
                setSmallIcon(R.drawable.baseline_build_24)

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
                        (if (someIgnored) "……\n" else "") + logEntries.joinToString("\n")
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
                notification.flags = NotificationCompat.FLAG_NO_CLEAR or NotificationCompat.FLAG_ONGOING_EVENT
            }

            notificationManager.notify(notificationID, notification) // 发送通知
        }

        suspend fun collectEvents(
            events: Flow<ShellEvent>,
            scope: CoroutineScope,
            onFinish: () -> Unit
        ) {
            events.collect { event ->
                when(event) {
                    is ShellEvent.Started -> {
                        forceStop = event.forceStop

                        val intentFilter = IntentFilter(stopActionName)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
                        } else {
                            context.registerReceiver(receiver, intentFilter)
                        }

                        updateNotification()
                    }
                    is ShellEvent.Log -> {
                        if (event.type != ShellLogType.INPUT) {
                            logEntries.add(event.text)
                            updateNotification()
                        }
                    }
                    is ShellEvent.Exited -> {
                        runCatching { context.unregisterReceiver(receiver) }

                        isFinished = true

                        if (event.payload == 0) {
                            logEntries.add(context.getString(R.string.kr_shell_completed))
                        } else {
                            logEntries.add(context.getString(R.string.kr_shell_error) + " " + event.payload?.toString())
                        }
                        updateNotification()

                        forceStop = null
                        shellEventSource.destroy()
                        scope.cancel()

                        try {
                            onFinish()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private var channelCreated = false
        private const val CHANNEL_ID = "kr_script_task_notification"
        private var notificationCounter = 0

        fun startTask(
            context: Context,
            script: String,
            params: HashMap<String, String>?,
            nodeInfo: RunnableNode,
            onFinish: () -> Unit
        ) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = PermissionUtil.checkPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                )
                if (!hasPermission) {
                    ActivityCompat.requestPermissions(
                        context as Activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        0x12
                    )
                }
            }

            val applicationContext = context.applicationContext
            notificationCounter += 1
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

            val shellEventSource = ShellEventSource()
            val controller =
                TaskNotificationController(applicationContext, shellEventSource, nodeInfo, notificationCounter)

            scope.launch {
                controller.collectEvents(
                    shellEventSource.events,
                    scope,
                    onFinish
                )
            }

            scope.launch {
                shellEventSource.progress.collect { progress ->
                    progress?.let {
                        val current = progress.first
                        val total = progress.second
                        controller.progressCurrent = current
                        controller.progressTotal = total
                        controller.updateNotification()
                    }
                }
            }

            ShellExecutor().execute(
                context,
                nodeInfo,
                script,
                params,
                shellEventSource
            )

            DialogHelper.openInfoAlert(context, context.getString(R.string.kr_bg_task_start), context.getString(
                R.string.kr_bg_task_start_desc))
        }
    }
}
