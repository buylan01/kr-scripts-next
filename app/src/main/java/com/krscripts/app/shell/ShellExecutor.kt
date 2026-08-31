package com.krscripts.app.shell

object ShellExecutor {
    private var extraEnvPath: String? = ""
    private var defaultEnvPath = ""

    private val envPath: String?
        get() {
            if (extraEnvPath != null && !extraEnvPath!!.isEmpty()) {
                if (defaultEnvPath.isEmpty()) {
                    try {
                        val process = Runtime.getRuntime().exec("sh")
                        val outputStream = process.outputStream
                        outputStream.write($$"echo $PATH".toByteArray())
                        outputStream.flush()
                        outputStream.close()

                        val inputStream = process.inputStream
                        val cache = ByteArray(16384)
                        val length = inputStream.read(cache)
                        inputStream.close()
                        process.destroy()

                        val path = String(cache, 0, length).trim { it <= ' ' }
                        if (path.isNotEmpty()) {
                            defaultEnvPath = path
                        } else {
                            throw RuntimeException("未能获取到 PATH 参数")
                        }
                    } catch (_: Exception) {
                        defaultEnvPath = "/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin"
                    }
                }

                val path = defaultEnvPath

                return ("PATH=$path:$extraEnvPath")
            }

            return null
        }

    private fun getProcess(run: String?): Process {
        val env = envPath
        val runtime = Runtime.getRuntime()
        val process = runtime.exec(run)
        if (env != null) {
            val outputStream = process.outputStream
            outputStream.write("export $env\n".toByteArray())
            outputStream.flush()
        }
        return process
    }

    val superUserRuntime: Process
        get() = getProcess("su")

    val runtime: Process
        get() = getProcess("sh")
}