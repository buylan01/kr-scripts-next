package com.krscripts.core.executor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.edit
import com.krscripts.core.FileOwner
import com.krscripts.core.model.NodeInfoBase
import com.krscripts.core.shared.FileWrite.getPrivateFileDir
import com.krscripts.core.shared.FileWrite.getPrivateFilePath
import com.krscripts.core.shared.FileWrite.writePrivateFile
import com.krscripts.core.shared.FileWrite.writePrivateShellFile
import com.krscripts.core.shell.KeepShell
import com.krscripts.core.shell.KeepShellPublic.checkRoot
import com.krscripts.core.shell.KeepShellPublic.getDefaultInstance
import com.krscripts.core.shell.ShellTranslation
import com.krscripts.core.util.MD5
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.Charset

object ScriptEnvironment {
    private const val ASSETS_FILE = "file:///android_asset/"
    var isInitialed: Boolean = false
        private set
    private var environmentPath = ""

    // 此目录将添加到PATH尾部，作为应用程序提供的拓展程序库目录，如有需要则需要在初始化executor.sh之前为该变量赋值
    private var TOOLKIT_DIR: String? = ""
    private var rooted = false
    private var privateShell: KeepShell? = null
    @SuppressLint("StaticFieldLeak")
    private var shellTranslation: ShellTranslation? = null
    @SuppressLint("StaticFieldLeak")
    private var assetsExtractor: AssetsExtractor? = null
    private val PLACEHOLDER_REGEX = Regex("""\{([^}]+)\}""")

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

        val appContext = context.applicationContext
        assetsExtractor = AssetsExtractor(appContext)
        shellTranslation = ShellTranslation(appContext)

        rooted = checkRoot()

        try {
            if (!toolkitDir.isNullOrEmpty()) {
                TOOLKIT_DIR = assetsExtractor?.extractResources(toolkitDir)
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

        val script = buildString {
            if (!nodeInfoBase?.currentPageConfigPath.isNullOrEmpty()) {
                val configDir = nodeInfoBase.pageConfigDir
                val configFile = nodeInfoBase.currentPageConfigPath
                appendExport("PAGE_CONFIG_DIR", configDir)
                appendExport("PAGE_CONFIG_FILE", configFile)

                var workDir: String? = null
                var workFile: String? = null
                if (configFile.startsWith("file:///android_asset/")) {
                    workDir = assetsExtractor?.getExtractPath(configDir)
                    workFile = assetsExtractor?.getExtractPath(configFile)
                }

                appendExport("PAGE_WORK_DIR", workDir ?: configDir)
                appendExport("PAGE_WORK_FILE", workFile ?: configFile)
            }
            appendLine()
            append("${if (rooted) "" else "sh " }$environmentPath \"$path\"")
        }

        val cmdResult = privateShell!!.doCmdSync(script)
        return shellTranslation?.resolveRow(cmdResult) ?: cmdResult
    }

    private fun StringBuilder.appendExport(name: String, value: String) {
        append("export ").append(name).append("=\'").append(value).append("\'\n")
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
        params["START_DIR"] = getStartPath(context)
        params["TEMP_DIR"] = context.cacheDir.absolutePath

        val fileOwner = FileOwner(context)
        val androidUid = fileOwner.userId
        params["ANDROID_UID"] = androidUid.toString()

        try {
            // @ https://blog.csdn.net/Gaugamela/article/details/78689580
            params["APP_USER_ID"] = fileOwner.fileOwner
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

    private fun buildVariables(params: Map<String, String?>?): List<String> =
        params?.map { (key, value) ->
            "export $key='${(value ?: "").replace("'", "'\\''")}'"
        } ?: emptyList()

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


        return "${if (rooted) "" else "sh "}$environmentPath \"$cachePath\" \"$tag\""
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
        val envParams = params ?: HashMap()

        nodeInfo?.let {
            val configDir = it.pageConfigDir
            val configFile = it.currentPageConfigPath
            envParams["PAGE_CONFIG_DIR"] = configDir
            envParams["PAGE_CONFIG_FILE"] = configFile

            var workDir: String? = null
            var workFile: String? = null
            if (configFile.startsWith("file:///android_asset/")) {
                val extractor = AssetsExtractor(context)
                workDir = extractor.getExtractPath(configDir)
                workFile = extractor.getExtractPath(configFile)
            }

            envParams["PAGE_WORK_DIR"] = workDir ?: configDir
            envParams["PAGE_WORK_FILE"] = workFile ?: configFile
        }

        val exportCommands = buildVariables(envParams).joinToString(separator = "\n")
        val script = getExecuteScript(context, cmds, tag)

        val content = buildString {
            if (exportCommands.isNotEmpty()) {
                append(exportCommands).append('\n')
            }
            append(script)
            append("\nexit\n")
        }

        try {
            dataOutputStream.write(content.toByteArray(Charsets.UTF_8))
            dataOutputStream.flush()
        } catch (e: Exception) {
            Log.e("ShellEnvironment", "Failed to write shell commands", e)
        }
    }
}
