package com.omarea.krscript.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Message
import android.text.SpannableString
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.omarea.krscript.R
import com.omarea.krscript.databinding.KrDialogLogBinding
import com.omarea.krscript.executor.ShellExecutor
import com.omarea.krscript.model.RunnableNode
import com.omarea.krscript.model.ShellHandlerBase


class DialogLogFragment : DialogFragment() {
    private var _binding: KrDialogLogBinding? = null
    private val binding get() = _binding!!

    private var running = false
    private var nodeInfo: RunnableNode? = null
    private lateinit var onExit: Runnable
    private lateinit var script: String
    private var params: HashMap<String, String>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = KrDialogLogBinding.inflate(inflater, container, false)

        val info = nodeInfo
        if (info == null) {
            dismiss()
            return binding.root
        }

        if (info.reloadPage) {
            binding.btnHide.visibility = View.GONE
        }

        val shellHandler = openExecutor(info)
        val hostActivity = activity
        if (hostActivity != null) {
            ShellExecutor().execute(hostActivity, info, script, onExit, params, shellHandler)
        } else {
            dismiss()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireActivity(), com.omarea.common.R.style.dialog_full_screen)
    }

    private fun openExecutor(nodeInfo: RunnableNode): ShellHandlerBase {
        var forceStopRunnable: Runnable? = null

        binding.btnHide.setOnClickListener {
            closeView()
        }
        binding.btnExit.setOnClickListener {
            if (running) {
                forceStopRunnable?.run()
            }
            closeView()
        }

        binding.btnCopy.setOnClickListener {
            try {
                val myClipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val myClip = ClipData.newPlainText("text", binding.shellOutput.text.toString())
                myClipboard.setPrimaryClip(myClip)
                Toast.makeText(context, getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, getString(R.string.copy_fail), Toast.LENGTH_SHORT).show()
            }
        }

        if (nodeInfo.interruptable) {
            binding.btnHide.visibility = View.VISIBLE
            binding.btnExit.visibility = View.VISIBLE
        } else {
            binding.btnHide.visibility = View.GONE
            binding.btnExit.visibility = View.GONE
        }

        if (nodeInfo.title.isNotEmpty()) {
            binding.title.text = nodeInfo.title
        } else {
            binding.title.visibility = View.GONE
        }

        if (nodeInfo.desc.isNotEmpty()) {
            binding.desc.text = nodeInfo.desc
        } else {
            binding.desc.visibility = View.GONE
        }

        binding.actionProgress.isIndeterminate = true
        return MyShellHandler(object : IActionEventHandler {
            override fun onCompleted() {
                running = false

                onExit.run()

                if (_binding != null) {
                    binding.btnHide.visibility = View.GONE
                    binding.btnExit.visibility = View.VISIBLE
                    binding.actionProgress.visibility = View.GONE
                }

                isCancelable = true
            }

            override fun onSuccess() {
                if (nodeInfo.autoOff) {
                    closeView()
                }
            }

            override fun onStart(forceStop: Runnable?) {
                running = true

                if (_binding != null) {
                    binding.btnExit.visibility =
                        if (nodeInfo.interruptable && forceStop != null) View.VISIBLE else View.GONE
                }
                forceStopRunnable = forceStop
            }

        }, binding.shellOutput, binding.actionProgress)
    }

    @FunctionalInterface
    interface IActionEventHandler {
        fun onStart(forceStop: Runnable?)
        fun onSuccess()
        fun onCompleted()
    }

    class MyShellHandler(
        private var actionEventHandler: IActionEventHandler,
        private var logView: TextView,
        private var shellProgress: ProgressBar) : ShellHandlerBase() {

        private val context: Context = logView.context

        private fun getThemeColor(attrRes: Int): Int {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(attrRes, typedValue, true)
            return typedValue.data
        }

        private val errorColor = getThemeColor(androidx.appcompat.R.attr.colorError)
        private val basicColor = getThemeColor(androidx.appcompat.R.attr.colorAccent)
        private val scriptColor = getThemeColor(androidx.appcompat.R.attr.colorAccent)
        private val endColor = getThemeColor(androidx.appcompat.R.attr.colorPrimary)

        private var hasError = false // 执行过程是否出现错误

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                EVENT_EXIT -> onExit(msg.obj)
                EVENT_START -> onStart(msg.obj)
                EVENT_REDE -> onReaderMsg(msg.obj)
                EVENT_READ_ERROR -> onError(msg.obj)
                EVENT_WRITE -> onWrite(msg.obj)
            }
        }

        override fun onReader(msg: Any) {
            updateLog(msg, basicColor)
        }

        override fun onWrite(msg: Any) {
            updateLog(msg, scriptColor)
        }

        override fun onError(msg: Any) {
            hasError = true
            updateLog(msg, errorColor)
        }

        override fun onStart(forceStop: Runnable?) {
            actionEventHandler.onStart(forceStop)
        }

        override fun onProgress(current: Int, total: Int) {
            when (current) {
                -1 -> {
                    shellProgress.visibility = View.VISIBLE
                    shellProgress.isIndeterminate = true
                }
                total -> shellProgress.visibility = View.GONE
                else -> {
                    shellProgress.visibility = View.VISIBLE
                    shellProgress.isIndeterminate = false
                    shellProgress.max = total
                    shellProgress.progress = current
                }
            }
        }

        override fun onStart(msg: Any?) {
            logView.text = ""
        }

        override fun onExit(msg: Any?) {
            updateLog(context.getString(R.string.kr_shell_completed), endColor)
            actionEventHandler.onCompleted()
            if (!hasError) {
                actionEventHandler.onSuccess()
            }
        }

        override fun updateLog(msg: SpannableString?) {
            if (msg == null) return
            logView.post {
                logView.append(msg)
                (logView.parent as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun closeView() {
        try {
            dismiss()
        } catch (_: Exception) {
        }
    }

    private var onDismissRunnable: Runnable? = null
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissRunnable?.run()
        onDismissRunnable = null
    }

    companion object {
        fun create(
            nodeInfo: RunnableNode,
            onExit: Runnable,
            onDismiss: Runnable,
            script: String,
            params: HashMap<String, String>?
        ): DialogLogFragment {
            val fragment = DialogLogFragment()
            fragment.nodeInfo = nodeInfo
            fragment.onExit = onExit
            fragment.script = script
            fragment.params = params
            fragment.onDismissRunnable = onDismiss

            return fragment
        }
    }
}