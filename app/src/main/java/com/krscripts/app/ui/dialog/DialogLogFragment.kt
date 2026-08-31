package com.krscripts.app.ui.dialog

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.fragment.app.DialogFragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.krscripts.app.R
import com.krscripts.app.databinding.KrDialogLogBinding
import com.krscripts.app.executor.ShellExecutor
import com.krscripts.app.model.ActionAfterExecution
import com.krscripts.app.model.RunnableNode
import com.krscripts.app.shell.ShellEvent
import com.krscripts.app.shell.ShellEventSource
import com.krscripts.app.shell.ShellLogType
import com.krscripts.app.util.AnsiToSpannable
import com.krscripts.app.util.isExitError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class DialogLogFragment : DialogFragment() {
    private var _binding: KrDialogLogBinding? = null
    private val binding get() = _binding!!
    private var nodeInfo: RunnableNode? = null
    private var onFinish: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private lateinit var script: String
    private var params: HashMap<String, String>? = null

    fun getMaterialColor(attrIdRes: Int): Int {
        return MaterialColors.getColor(context,attrIdRes, "Color not found")
    }
    private val colorOutputError by lazy { getMaterialColor(androidx.appcompat.R.attr.colorError) }
    private val colorOutput by lazy { getMaterialColor(androidx.appcompat.R.attr.colorAccent) }
    private val colorInput by lazy { getMaterialColor(androidx.appcompat.R.attr.colorPrimary) }

    private val shellEventSource = ShellEventSource()
    private var shellHasError: Boolean = false
    private var shellOnStop: Runnable? = null
    private var shellRunning = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = KrDialogLogBinding.inflate(inflater, container, false)

        val info = nodeInfo
        if (info == null) {
            dismiss()
            return binding.root
        }

        initView(info)
        createCollector(shellEventSource.events, lifecycleScope, info.interruptable)

        if (activity != null) {
            ShellExecutor().execute(activity, info, script, params, shellEventSource)
        } else {
            dismiss()
        }

        return binding.root
    }

    private fun createCollector(
        events: Flow<ShellEvent>,
        scope: CoroutineScope,
        interruptable: Boolean
    ) {
        val outputView = binding.shellOutput
        val scrollView = binding.logContainter
        val shellProgress = binding.actionProgress
        scope.launch {
            events.collect { event ->
                when(event) {
                    is ShellEvent.Started -> {
                        shellOnStop = event.forceStop
                        shellRunning = true
                        binding.btnExit.isEnabled = interruptable && event.forceStop != null
                    }
                    is ShellEvent.Log -> {
                        if (event.type == ShellLogType.OUTPUT_ERROR) { shellHasError = true }
                        if (event.type != ShellLogType.INPUT) {
                            val spanned = event.toLogSpanned()
                            outputView.append(spanned)
                            scrollView.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                    is ShellEvent.Exited -> {

                        val isError = isExitError(event.payload)

                        val messageRes = if (isError) R.string.kr_shell_error else R.string.kr_shell_completed
                        val color = if (isError) colorOutputError else colorInput

                        context?.getString(messageRes)?.let {
                            val str = buildSpannedString {
                                color(color) { append(it) }
                            }
                            outputView.append(str)
                        }

                        binding.btnExit.isEnabled = true
                        binding.btnHide.isEnabled = true

                        binding.actionProgress.let { view ->
                            val initialHeight = view.trackThickness

                            ValueAnimator.ofInt(initialHeight, 0).apply {
                                duration = 220
                                interpolator = FastOutSlowInInterpolator()
                                addUpdateListener { valueAnimator ->
                                    val animatedValue = valueAnimator.animatedValue as Int
                                    view.trackThickness = animatedValue
                                }
                                start()
                            }
                        }

                        isCancelable = true

                        if (!shellHasError && nodeInfo?.afterExecution == ActionAfterExecution.HIDE) {
                            dismiss()
                        }
                        onFinish?.invoke()
                    }
                }
            }
        }
        scope.launch {
            shellEventSource.progress.collect { progress ->
                progress?.let {
                    val current = progress.first
                    val total = progress.second
                    when (current) {
                        -1 -> {
                            shellProgress.visibility = View.VISIBLE
                            shellProgress.isIndeterminate = true
                        }

                        else -> {
                            shellProgress.visibility = View.VISIBLE
                            shellProgress.isIndeterminate = false
                            shellProgress.max = total
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                shellProgress.setProgress(current, true)
                            } else {
                                shellProgress.progress = current
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismiss?.invoke()
        onDismiss = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        shellEventSource.destroy()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireActivity(), R.style.dialog_full_screen)
    }

    private fun initView(nodeInfo: RunnableNode) {

        binding.btnHide.setOnClickListener {
            dismiss()
        }
        binding.btnExit.setOnClickListener {
            if (shellRunning) {
                shellOnStop?.run()
            }
            dismiss()
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

        val interruptable = nodeInfo.interruptable
        binding.btnHide.isEnabled = interruptable && !nodeInfo.reloadPage
        binding.btnExit.isEnabled = interruptable


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
    }

    fun ShellEvent.Log.toLogSpanned(): Spanned {
        val defaultColor = when (type) {
            ShellLogType.OUTPUT -> colorOutput
            ShellLogType.OUTPUT_ERROR -> colorOutputError
            ShellLogType.INPUT -> colorInput
        }
        return AnsiToSpannable.parse(text + "\n", defaultColor)
    }

    companion object {
        fun create(
            nodeInfo: RunnableNode,
            script: String,
            params: HashMap<String, String>?,
            onFinish: () -> Unit,
            onDismiss: () -> Unit
        ): DialogLogFragment {
            val fragment = DialogLogFragment()
            fragment.nodeInfo = nodeInfo
            fragment.onFinish = onFinish
            fragment.onDismiss = onDismiss
            fragment.script = script
            fragment.params = params

            return fragment
        }
    }
}