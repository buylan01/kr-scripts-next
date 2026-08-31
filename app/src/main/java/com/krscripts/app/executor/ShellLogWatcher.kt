package com.krscripts.app.executor;

import android.content.Context
import com.krscripts.app.shell.ShellEventSource
import com.krscripts.app.shell.ShellTranslation
import java.io.InputStream

object ShellLogWatcher {
    fun setWatcher(
        context: Context,
        process: Process,
        shellEventSource: ShellEventSource,
    ) {
        val shellTranslation = ShellTranslation(context)

        fun readStream(
            stream: InputStream,
            onLine: (String) -> Unit
        ): Thread = Thread {
            stream.bufferedReader().use { reader ->
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        onLine(shellTranslation.resolveRow(line))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val reader = readStream(process.inputStream, shellEventSource::postRead)
        val readerError = readStream(process.errorStream, shellEventSource::postReadError)

        val waitExit = Thread {
            var status = -1
            try {
                status = process.waitFor()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } finally {

                try {
                    reader.join()
                    readerError.join()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }

                shellEventSource.postExit(status)
            }
        }

        reader.start()
        readerError.start()
        waitExit.start()
    }
}