package com.krscripts.app;

import android.content.Context
import android.os.Process
import android.os.UserManager

class FileOwner(private val context: Context) {
    val userId: Int
        get() {
            val um = context.getSystemService(Context.USER_SERVICE) as UserManager
            val userHandle = Process.myUserHandle()

            var value = 0
            try {
                value = um.getSerialNumberForUser(userHandle).toInt()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return value
        }

    val fileOwner: String
        get() {
            return "u" + this.userId + "_a" + ((Process.myUid() % 100000) - Process.FIRST_APPLICATION_UID) // 100000 => UserHandle.PER_USER_RANGE
        }
}
