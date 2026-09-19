package com.krscripts.app.ui.param

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.krscripts.app.R
import com.krscripts.app.contracts.FilePickerContract
import com.krscripts.app.contracts.FilePickerRequest
import com.krscripts.app.databinding.KrDialogParamsBinding
import com.krscripts.app.executor.ScriptEnvironment
import com.krscripts.app.model.ActionNode
import com.krscripts.app.model.ActionParamInfo
import com.krscripts.app.shared.FilePathResolver
import com.krscripts.app.ui.page.LoadingHelper

class ParamsDialogFragment: BottomSheetDialogFragment() {

    private var _binding: KrDialogParamsBinding? = null
    private val binding get() = _binding!!
    private var pendingFileRequest: FilePickerRequest? = null
    private val launcher = registerForActivityResult(FilePickerContract()) { result ->
        val request = pendingFileRequest
        pendingFileRequest = null
        val uri = result.uri?.let { FilePathResolver().getPath(requireContext(), it)?.toUri() }
        if (uri != null && request != null) {
            request.onSelected(uri)
        }
    }
    private var paramNodes: ArrayList<ActionParamInfo>? = null
    private var parentNode: ActionNode? = null
    private var renderer: ParamLayoutRender? = null
    private var isDialog: Boolean? = null

    companion object {

        const val REQUEST_KEY_PARAMS = "params_request"
        const val BUNDLE_KEY_STATUS = "status"
        const val BUNDLE_KEY_PARAMS = "params"

        const val STATUS_CONFIRM = 1
        const val STATUS_CANCEL  = 0

        fun newInstance(
            parentNode: ActionNode,
            paramNodes: ArrayList<ActionParamInfo>?,
            isDialog: Boolean = false
        ): ParamsDialogFragment {
            val fragment = ParamsDialogFragment()
            val args = Bundle()
            args.putSerializable("parentNode", parentNode)
            args.putSerializable("paramNodes", paramNodes)
            args.putBoolean("isDialog", isDialog)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            @Suppress("UNCHECKED_CAST")
            parentNode = BundleCompat.getSerializable(it, "parentNode", ActionNode::class.java)
            paramNodes = BundleCompat.getSerializable(it, "paramNodes", ArrayList::class.java) as? ArrayList<ActionParamInfo>
            isDialog = it.getBoolean("isDialog")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = KrDialogParamsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        if (isDialog == true) {
            binding.dragHandle.visibility = View.GONE
            binding.topBar.updatePadding(top = 32.dp(ctx))
            binding.buttonContainer.updatePadding(bottom = 8.dp(ctx))
        }

        val loadingContainer = view.findViewById<ViewGroup>(R.id.loading_container)
        val loadingHelper = LoadingHelper(loadingContainer)

        if (paramNodes != null) {
            for (paramNode in paramNodes) {

                val label =
                    if (!paramNode.label.isNullOrEmpty()) paramNode.label else paramNode.name

                loadingHelper.showDialog(ctx.getString(R.string.kr_param_load) + label)

                if (paramNode.valueShell != null) {
                    val result = ScriptEnvironment.execute(
                        ctx,
                        paramNode.valueShell,
                        parentNode
                    )
                    paramNode.valueFromShell = result
                }

                loadingHelper.showDialog(ctx.getString(R.string.kr_param_options_load) + label)

                paramNode.optionsFromShell =
                    ParamUtil.getParamOptions(ctx, paramNode, parentNode)

                loadingHelper.hideDialog()
            }
        }

        binding.run {
            parentNode ?: return@run

            title.setTextOrHide(parentNode!!.title)
            desc.setTextOrHide(parentNode!!.desc)
            warn.setTextOrHide(parentNode!!.warning)
        }

        binding.btnConfirm.setOnClickListener {

            val paramsResult = runCatching {
                renderer?.readParamsValue()
            }

            paramsResult.getOrNull()?.let { result ->
                val bundle = bundleOf()
                bundle.apply {
                    putInt(
                        BUNDLE_KEY_STATUS, STATUS_CONFIRM
                    )
                    putParcelable(
                        BUNDLE_KEY_PARAMS,
                        result
                    )
                }
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY_PARAMS,
                    bundle
                )
                dismiss()
            } ?: run {
                Toast.makeText(ctx, paramsResult.exceptionOrNull()?.message, Toast.LENGTH_SHORT)
                    .show()
            }
        }
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        renderer = ParamLayoutRender(binding.paramsContainer, requireActivity())

        paramNodes?.let { nodes ->
            renderer!!.renderList(
                nodes,
                startFilePicker = {
                    pendingFileRequest = it
                    launcher.launch(it)
                }
            )
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (isDialog == true) {
            return Dialog(requireContext(), R.style.CustomDialogThemeOverlay).apply {
                window?.setBackgroundDrawable(buildMaterialBackground())
            }
        }
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        return dialog

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun TextView.setTextOrHide(text: CharSequence) {
        if (text.isNotBlank()) {
            setText(text)
        } else {
            visibility = View.GONE
        }
    }

    private fun buildMaterialBackground(): Drawable {
        val ctx = requireContext()
        val corner = 32.dp(ctx).toFloat()

        val shape = MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setAllCorners(CornerFamily.ROUNDED, corner)
                .build()
        ).apply {
            fillColor = ColorStateList.valueOf(
                MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurfaceContainerLow, 0)
            )

            elevation = 8.dp(ctx).toFloat()
        }

        val inset = 24.dp(ctx)
        return InsetDrawable(shape, inset, inset, inset, inset)
    }

    private fun Int.dp(ctx: Context) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, toFloat(), ctx.resources.displayMetrics).toInt()
}