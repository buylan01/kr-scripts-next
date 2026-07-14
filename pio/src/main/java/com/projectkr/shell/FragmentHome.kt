package com.projectkr.shell

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import com.google.android.material.snackbar.Snackbar
import androidx.fragment.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.omarea.common.shell.KeepShellPublic
import com.projectkr.shell.ui.AdapterCpuCores
import com.projectkr.shell.utils.CpuFrequencyUtils
import com.projectkr.shell.utils.GpuUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import kotlin.collections.HashMap


class FragmentHome : androidx.fragment.app.Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private lateinit var globalSPF: SharedPreferences
    private var timer: Timer? = null
    private fun showMsg(msg: String) {
        this.view?.let { Snackbar.make(it, msg, Snackbar.LENGTH_LONG).show() }
    }

    private lateinit var spf: SharedPreferences
    private var myHandler = Handler()

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        if (isDetached) {
            return
        }
        maxFreqs.clear()
        minFreqs.clear()
        stopTimer()
        timer = Timer()
        timer!!.schedule(object : TimerTask() {
            override fun run() {
                updateInfo()
            }
        }, 0, 1000)
        updateRamInfo()
    }

    private var coreCount = -1
    private var activityManager: ActivityManager? = null

    private var minFreqs = HashMap<Int, String>()
    private var maxFreqs = HashMap<Int, String>()
    fun format1(value: Double): String {

        var bd = BigDecimal(value)
        bd = bd.setScale(1, RoundingMode.HALF_UP)
        return bd.toString()
    }

    @SuppressLint("SetTextI18n")
    private fun updateRamInfo() {
        try {
            val info = ActivityManager.MemoryInfo()
            if (activityManager == null) {
                activityManager = requireContext().getSystemService(ACTIVITY_SERVICE) as ActivityManager
            }
            activityManager!!.getMemoryInfo(info)
        } catch (ex: Exception) {
        }
    }

    private var updateTick = 0

    @SuppressLint("SetTextI18n")
    private fun updateInfo() {
        if (coreCount < 1) {
            coreCount = CpuFrequencyUtils.getCoreCount()
        }
        val cores = ArrayList<CpuCoreInfo>()
        val loads = CpuFrequencyUtils.getCpuLoad()
        for (coreIndex in 0 until coreCount) {
            val core = CpuCoreInfo()

            core.currentFreq = CpuFrequencyUtils.getCurrentFrequency("cpu$coreIndex")
            if (!maxFreqs.containsKey(coreIndex) || (core.currentFreq != "" && maxFreqs.get(coreIndex).isNullOrEmpty())) {
                maxFreqs.put(coreIndex, CpuFrequencyUtils.getCurrentMaxFrequency("cpu" + coreIndex))
            }
            core.maxFreq = maxFreqs.get(coreIndex)

            if (!minFreqs.containsKey(coreIndex) || (core.currentFreq != "" && minFreqs.get(coreIndex).isNullOrEmpty())) {
                minFreqs.put(coreIndex, CpuFrequencyUtils.getCurrentMinFrequency("cpu" + coreIndex))
            }
            core.minFreq = minFreqs.get(coreIndex)

            if (loads.containsKey(coreIndex)) {
                core.loadRatio = loads.get(coreIndex)!!
            }
            cores.add(core)
        }
        updateTick++
        if (updateTick > 5) {
            updateTick = 0
            minFreqs.clear()
            maxFreqs.clear()
        }
    }

    private fun stopTimer() {
        if (this.timer != null) {
            timer!!.cancel()
            timer = null
        }
    }

    override fun onPause() {
        stopTimer()
        super.onPause()
    }
}
