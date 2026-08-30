package com.krscripts.core.ui.widget

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.TextView
import com.krscripts.core.R
import com.krscripts.core.TryOpenActivity
import com.krscripts.core.executor.ScriptEnvironment
import com.krscripts.core.model.TextNode
import com.krscripts.core.ui.dialog.DialogHelper
import com.krscripts.core.util.startActivityLink

class ListItemText(
    private val context: Context,
    layoutId: Int,
    config: TextNode
) : ListItemView(context, layoutId, config) {

    private val rowsView = layout.findViewById<TextView?>(R.id.kr_rows)

    init {
        if (config.selectable) {
            descView?.setTextIsSelectable(true)
            rowsView?.setTextIsSelectable(true)
            summaryView?.setTextIsSelectable(true)
        }
        if (config.rows.isNotEmpty() && rowsView != null) {
            rowsView.movementMethod = LinkMovementMethod.getInstance() // 不设置 ClickableSpan 点击没反应
            rowsView.visibility = View.VISIBLE

            val builder = SpannableStringBuilder()
            for (row in config.rows) {
                builder.apply {
                    if (row.breakRow || row.align != Layout.Alignment.ALIGN_NORMAL) {
                        appendLine()
                    }

                    val start = length
                    append(row.text)
                    val end = length

                    fun setSpan(span: Any) {
                        builder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }

                    fun updateDrawState(ds: TextPaint) {
                        ds.color = if (row.color != 1) ds.linkColor else row.color
                        ds.isUnderlineText = row.underline
                    }

                    if (row.underline) {
                        setSpan(UnderlineSpan())
                    }

                    if (row.link.isNotEmpty()) {
                        setSpan(object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                if (row.link.isNotEmpty()) {
                                    context.startActivityLink(row.link)
                                }
                            }

                            override fun updateDrawState(ds: TextPaint) {
                                updateDrawState(ds)
                            }
                        })
                    }

                    if (row.activity.isNotEmpty()) {
                        setSpan(object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                TryOpenActivity(context, row.activity).tryOpen()
                            }

                            override fun updateDrawState(ds: TextPaint) {
                                updateDrawState(ds)
                            }
                        })
                    }

                    if (row.onClickScript.isNotEmpty()) {
                        setSpan(object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                val result = ScriptEnvironment.execute(
                                    context,
                                    row.onClickScript,
                                    config
                                )
                                if (result.trim().isNotEmpty()) {
                                    DialogHelper.openInfoAlert(context, context.getString(R.string.kr_slice_script_result), result)
                                }
                            }

                            override fun updateDrawState(ds: TextPaint) {
                                updateDrawState(ds)
                            }
                        })
                    }

                    if (row.color != -1) {
                        setSpan(ForegroundColorSpan(row.color))
                    }

                    if (row.bgColor != -1) {
                        setSpan(BackgroundColorSpan(row.bgColor))
                    }

                    val textStyle = when {
                        row.bold && row.italic -> Typeface.BOLD_ITALIC
                        row.bold -> Typeface.BOLD
                        row.italic -> Typeface.ITALIC
                        else -> null
                    }

                    textStyle?.let {
                        setSpan(StyleSpan(it))
                    }

                    if (row.size != -1) {
                        setSpan(AbsoluteSizeSpan(row.size, true))
                    }

                    setSpan(AlignmentSpan.Standard(row.align))
                }
            }
            rowsView.append(builder)
            // NOTE: 修补 android.widget.Editor.touchPositionIsInSelection(Editor.java:1363) 导致的奔溃
            rowsView.setOnLongClickListener { true }
        } else {
            rowsView?.visibility = View.GONE
        }
    }
}
