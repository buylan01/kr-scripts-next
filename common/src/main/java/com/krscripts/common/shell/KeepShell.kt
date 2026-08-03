package com.krscripts.common.shell

import android.util.Log
import com.krscripts.common.shell.ShellExecutor.getRuntime
import com.krscripts.common.util.PermissionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock


/**
 * Created by Hello on 2018/01/23.
 * Edited by buylan on 2026/08/03.
 */
class KeepShell(
    private var permissionType: PermissionType
) {
    private var p: Process? = null
    private var out: OutputStream? = null
    private var reader: BufferedReader? = null
    var isIdle = true
        private set

    //尝试退出命令行程序
    fun tryExit() {

        runCatching {
            out?.close()
            reader?.close()
        }
        runCatching {
            p?.destroy()
        }
        enterLockTime = 0L
        out = null
        reader = null
        p = null
        isIdle = true
    }

    private val mLock = ReentrantLock()
    private var enterLockTime = 0L

    fun checkRoot(): Boolean {
        val r = doCmdSync(checkRootState).lowercase(Locale.getDefault())
        val isRoot = if (r == "error" || r.contains("permission denied") || r.contains("not allowed") || r == "not found") {
            false
        } else if (r.contains("success")) {
            true
        } else {
            false
        }

        if (isRoot) {
            return true
        } else {
            if (permissionType == PermissionType.ROOT) {
                tryExit()
            }
            return  false
        }
    }

    private fun getRuntimeShell() {
        if (p != null) return
        val getSu = Thread {
            try {
                mLock.lockInterruptibly()
                enterLockTime = System.currentTimeMillis()
                p = getRuntime(permissionType)
                out = p!!.outputStream
                reader = p!!.inputStream.bufferedReader()
                Thread {
                    try {
                        val errorReader =
                            p!!.errorStream.bufferedReader()
                        while (true) {
                            Log.e("KeepShellPublic", errorReader.readLine())
                        }
                    } catch (ex: Exception) {
                        Log.e("c", "" + ex.message)
                    }
                }.start()
            } catch (ex: Exception) {
                Log.e("getRuntime", "" + ex.message)
            } finally {
                enterLockTime = 0L
                mLock.unlock()
            }
        }
        getSu.start()
        getSu.join(10000)
        if (p == null && getSu.state != Thread.State.TERMINATED) {
            enterLockTime = 0L
            getSu.interrupt()
        }
    }

    private val shellOutputCache = StringBuilder()
    private val startTagBytes = "\necho '$TAG_START'\n".toByteArray(Charset.defaultCharset())
    private val endTagBytes = "\necho '$TAG_END'\n".toByteArray(Charset.defaultCharset())

    //执行脚本
    fun doCmdSync(cmd: String): String {
        if (mLock.isLocked && enterLockTime > 0 && System.currentTimeMillis() - enterLockTime > LOCK_TIMEOUT) {
            tryExit()
            Log.e("doCmdSync-Lock", "线程等待超时${System.currentTimeMillis()} - $enterLockTime > $LOCK_TIMEOUT")
        }
        getRuntimeShell()


        try {
            mLock.lockInterruptibly()
            isIdle = false

            val result = runBlocking(Dispatchers.IO) {

                out?.let { stream ->
                    stream.write(startTagBytes)
                    stream.write(cmd.toByteArray(Charset.defaultCharset()))
                    stream.write(endTagBytes)
                    stream.flush()
                }

                var unstart = true
                while (reader != null) {
                    val line = reader!!.readLine()
                    if (line == null) {
                        break
                    } else if (line.contains(TAG_END)) {
                        shellOutputCache.append(line.substring(0, line.indexOf(TAG_END)))
                        break
                    } else if (line.contains(TAG_START)) {
                        shellOutputCache.clear()
                        shellOutputCache.append(line.substring(line.indexOf(TAG_START) + TAG_START.length))
                        unstart = false
                    } else if (!unstart) {
                        shellOutputCache.append(line)
                        shellOutputCache.append("\n")
                    }
                }

                shellOutputCache.toString().trim()
            }
            return result
        } catch (e: Exception) {
            tryExit()
            Log.e("KeepShellAsync", "" + e.message)
            return "error"
        } finally {
            enterLockTime = 0L
            mLock.unlock()

            isIdle = true
        }
    }

    companion object {
        private const val LOCK_TIMEOUT = 10000L
        private const val TAG_START = "|SH>>|"
        private const val TAG_END = "|<<SH|"
        private val checkRootState = $$"""
            if [ "$(id -u)" = "0" ] || [ "$UID" = "0" ] || [ "$(whoami)" = "root" ] || [ "$(set | grep 'USER_ID=0')" == "USER_ID=0" ]; then
                echo "success"
            else
                if [[ -d /cache ]]; then
                    echo 1 > /cache/vtools_root
                    if [[ -f /cache/vtools_root ]] && [[ $(cat /cache/vtools_root) == '1' ]]; then
                        echo "success"
                        rm -rf /cache/vtools_root
                        return
                    fi
                fi
                exit 1
            fi
            """.trimIndent()
    }
}
