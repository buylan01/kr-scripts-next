package com.omarea.krscript.executor

import android.content.Context
import android.os.Build
import android.os.Environment
import com.omarea.common.shared.FileWrite.getPrivateFileDir
import com.omarea.common.shared.FileWrite.getPrivateFilePath
import com.omarea.common.shared.FileWrite.writePrivateFile
import com.omarea.common.shared.FileWrite.writePrivateShellFile
import com.omarea.common.shell.KeepShell
import com.omarea.common.shell.KeepShellPublic.checkRoot
import com.omarea.common.shell.KeepShellPublic.getDefaultInstance
import com.omarea.common.shell.ShellTranslation
import com.omarea.krscript.FileOwner
import com.omarea.krscript.model.NodeInfoBase
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.Charset
import androidx.core.content.edit
import com.omarea.krscript.util.MD5

object ScriptEnvironment {
    private const val ASSETS_FILE = "file:///android_asset/"
    var isInitialed: Boolean = false
        private set
    private var environmentPath = ""

    // 此目录将添加到PATH尾部，作为应用程序提供的拓展程序库目录，如有需要则需要在初始化executor.sh之前为该变量赋值
    private var TOOLKIT_DIR: String? = ""
    private var rooted = false
    private var privateShell: KeepShell? = null
    private var shellTranslation: ShellTranslation? = null
    private val PLACEHOLDER_REGEX = Regex("""\$\(\{([^}]+)\}\)""")

    private fun init(context: Context): Boolean {
        val configSpf = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE)

        return init(
            context,
            configSpf.getString("executor", "kr-script/executor.sh")!!,
            configSpf.getString("toolkitDir", "kr-script/toolkit")
        )
    }

    /**
     * 初始化执行器
     *
     * @param context  Context
     * @param executor 执行器在Assets中的位置
     * @return 是否初始化成功
     */
    fun init(context: Context, executor: String, toolkitDir: String?): Boolean {
        if (isInitialed) {
            return true
        }

        shellTranslation = ShellTranslation(context.applicationContext)
        rooted = checkRoot()

        try {
            if (!toolkitDir.isNullOrEmpty()) {
                TOOLKIT_DIR = ExtractAssets(context).extractResources(toolkitDir)
            }

            val fileName = executor.removePrefix(ASSETS_FILE)

            val bytes = context.assets.open(fileName).use { it.readBytes() }
            var envShell = String(bytes, Charset.defaultCharset()).replace("\r", "")

            val environment = getEnvironment(context).toMutableMap()
            val outputPathAbs = getPrivateFilePath(context, fileName)
            environment["EXECUTOR_PATH"] = outputPathAbs

            envShell = PLACEHOLDER_REGEX.replace(envShell) { match ->
                environment[match.groupValues[1]] ?: ""
            }


            isInitialed =
                writePrivateFile(envShell.toByteArray(Charset.defaultCharset()), fileName, context)
            if (isInitialed) {
                environmentPath = outputPathAbs
            }

            context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE).edit {
                putString("executor", executor)
                putString("toolkitDir", toolkitDir)
            }

            privateShell = if (rooted) getDefaultInstance() else KeepShell(false)

            return isInitialed
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * 写入缓存（脚本代码存入脚本文件）
     */
    private fun createShellCache(context: Context, script: String): String {
        val md5 = MD5.md5(script)
        val outputPath = "kr-script/cache/$md5.sh"
        if (File(outputPath).exists()) {
            return outputPath
        }

        val bytes = ("#!/system/bin/sh\n\n$script")
            .replace("\r\n", "\n")
            .replace("\r\t", "\t")
            .replace("\r", "\n")
            .toByteArray()
        if (writePrivateFile(bytes, outputPath, context)) {
            return getPrivateFilePath(context, outputPath)
        }
        return ""
    }

    /**
     * 执行脚本
     */
    private fun extractScript(context: Context, fileName: String): String? {
        var fileName = fileName
        if (fileName.startsWith(ASSETS_FILE)) {
            fileName = fileName.substring(ASSETS_FILE.length)
        }
        return writePrivateShellFile(fileName, fileName, context)
    }

    @JvmStatic
    fun executeResultRoot(context: Context, script: String?, nodeInfoBase: NodeInfoBase?): String {
        if (!isInitialed) {
            init(context)
        }

        if (script.isNullOrEmpty()) {
            return ""
        }

        val script2 = script.trim { it <= ' ' }
        val path: String? = if (script2.startsWith(ASSETS_FILE)) {
            extractScript(context, script2)
        } else {
            createShellCache(context, script)
        }

        if (!isInitialed) {
            init(context)
        }

        val script = buildString {
            appendLine()
            if (nodeInfoBase != null && nodeInfoBase.currentPageConfigPath.isNotEmpty()) {
                val parentDir = nodeInfoBase.pageConfigDir
                val configPath = nodeInfoBase.currentPageConfigPath
                append("export PAGE_CONFIG_DIR='").append(parentDir).append("'\n")
                append("export PAGE_CONFIG_FILE='").append(configPath).append("'\n")

                if (configPath.startsWith("file:///android_asset/")) {
                    val extractor = ExtractAssets(context)
                    append("export PAGE_WORK_DIR='").append(extractor.getExtractPath(parentDir)).append("'\n")
                    append("export PAGE_WORK_FILE='").append(extractor.getExtractPath(configPath)).append("'\n")
                } else {
                    append("export PAGE_WORK_DIR='").append(parentDir).append("'\n")
                    append("export PAGE_WORK_FILE='").append(configPath).append("'\n")
                }
            } else {
                append("export PAGE_CONFIG_DIR=''\n")
                append("export PAGE_CONFIG_FILE=''\n")
                append("export PAGE_WORK_DIR=''\n")
                append("export PAGE_WORK_FILE=''\n")
            }
            appendLine()
            appendLine()
            append("$environmentPath \"$path\"")
        }

        val cmdResult = privateShell!!.doCmdSync(script)
        return shellTranslation?.resolveRow(cmdResult) ?: cmdResult
    }

    private fun getStartPath(context: Context): String {
        val dir = getPrivateFileDir(context)
        if (dir.endsWith("/")) {
            return dir.substring(0, dir.length - 1)
        }
        return dir
    }

    /*
    public static int getUserId() {
        int value = 0;
        try {
            Class<?> c = Class.forName("android.os.UserHandle");
            Method get = c.getMethod("getUserId", int.class);
            value = (int)(get.invoke(c, android.os.Process.myUid()));
        } catch (Exception ignored) {
        }
        return value;
    }*/
    /**
     * 获取框架的环境变量
     */
    private fun getEnvironment(context: Context): HashMap<String, String> {
        val params = HashMap<String, String>()

        params["TOOLKIT"] = TOOLKIT_DIR ?: "null"
        params["MAGISK_PATH"] = "null"
        params["START_DIR"] = getStartPath(context)
        params["TEMP_DIR"] = context.cacheDir.absolutePath

        val fileOwner = FileOwner(context)
        val androidUid = fileOwner.getUserId()
        params["ANDROID_UID"] = androidUid.toString()

        try {
            // @ https://blog.csdn.net/Gaugamela/article/details/78689580
            params["APP_USER_ID"] = fileOwner.getFileOwner()
        } catch (_: Exception) {

        }

        params["ANDROID_SDK"] = "" + Build.VERSION.SDK_INT
        // params.put("ROOT_PERMISSION", rooted ? "granted" : "denied");
        params["ROOT_PERMISSION"] = if (rooted) "true" else "false"
        params["SDCARD_PATH"] = Environment.getExternalStorageDirectory().absolutePath
        val busyboxPath = getPrivateFilePath(context, "busybox")
        if (File(busyboxPath).exists()) {
            params["BUSYBOX"] = busyboxPath
        } else {
            params["BUSYBOX"] = "busybox"
        }

        try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageInfo(context.packageName, 0)
            params["PACKAGE_NAME"] = context.packageName
            params["PACKAGE_VERSION_NAME"] = packageInfo.versionName ?: "null"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params["PACKAGE_VERSION_CODE"] = "" + packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                params["PACKAGE_VERSION_CODE"] = "" + packageInfo.versionCode
            }
        } catch (_: Exception) {

        }

        return params
    }

    private fun getVariables(params: HashMap<String, String>?): ArrayList<String?> {
        val envp = ArrayList<String?>()

        if (params != null) {
            for (key in params.keys) {
                var value = params[key]
                if (value == null) {
                    value = ""
                }
                envp.add(key + "='" + value.replace("'".toRegex(), "'\\\\''") + "'")
            }
        }

        return envp
    }

    private fun getExecuteScript(context: Context, script: String?, tag: String?): String {
        if (!isInitialed) {
            init(context)
        }

        if (script.isNullOrEmpty()) {
            return ""
        }

        val script2 = script.trim { it <= ' ' }
        var cachePath: String?
        if (script2.startsWith(ASSETS_FILE)) {
            cachePath = extractScript(context, script2)
            if (cachePath == null) {
                cachePath = script
                // String error = context.getString(R.string.script_losted) + setState;
                // Toast.makeText(context, error, Toast.LENGTH_LONG).show();
            }
        } else {
            cachePath = createShellCache(context, script)
        }


        return "$environmentPath \"$cachePath\" \"$tag\""
    }

    val runtime: Process?
        get() {
            return try {
                if (rooted) {
                    Runtime.getRuntime().exec("su")
                } else {
                    Runtime.getRuntime().exec("sh")
                }
            } catch (_: Exception) {
                null
            }
        }

    /**
     * 使用执行器运行脚本
     *
     * @param context          Context
     * @param dataOutputStream Runtime进程的输出流
     * @param cmds             要执行的脚本
     * @param params           参数类别
     */
    @JvmStatic
    fun executeShell(
        context: Context,
        dataOutputStream: DataOutputStream,
        cmds: String?,
        params: HashMap<String, String>?,
        nodeInfo: NodeInfoBase?,
        tag: String?
    ) {
        var params = params
        if (params == null) {
            params = HashMap()
        }

        // 页面配置文件路径
        if (nodeInfo != null) {
            val parentPageConfigDir = nodeInfo.pageConfigDir
            val currentPageConfigPath = nodeInfo.currentPageConfigPath
            params["PAGE_CONFIG_DIR"] = parentPageConfigDir
            params["PAGE_CONFIG_FILE"] = currentPageConfigPath
            if (currentPageConfigPath.startsWith("file:///android_asset/")) {
                val extractor = ExtractAssets(context)
                params["PAGE_WORK_DIR"] = extractor.getExtractPath(parentPageConfigDir)
                params["PAGE_WORK_FILE"] = extractor.getExtractPath(currentPageConfigPath)
            } else {
                params["PAGE_WORK_DIR"] = parentPageConfigDir
                params["PAGE_WORK_FILE"] = currentPageConfigPath
            }
        } else {
            params["PAGE_CONFIG_DIR"] = ""
            params["PAGE_CONFIG_FILE"] = ""
            params["PAGE_WORK_DIR"] = ""
            params["PAGE_WORK_FILE"] = ""
        }

        val envp = getVariables(params)
        val envpCmds = StringBuilder()
        if (!envp.isEmpty()) {
            for (param in envp) {
                envpCmds.append("export ").append(param).append("\n")
            }
        }
        try {
            dataOutputStream.write(envpCmds.toString().toByteArray(charset("UTF-8")))

            dataOutputStream.write(
                getExecuteScript(
                    context,
                    cmds,
                    tag
                ).toByteArray(charset("UTF-8"))
            )

            dataOutputStream.writeBytes("\n\n")
            dataOutputStream.writeBytes("sleep 0.2;\n")
            dataOutputStream.writeBytes("exit\n")
            dataOutputStream.writeBytes("exit\n")
            dataOutputStream.flush()
        } catch (_: Exception) {

        }
    }
}
