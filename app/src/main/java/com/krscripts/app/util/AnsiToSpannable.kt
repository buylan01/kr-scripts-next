package com.krscripts.app.util

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan

object AnsiToSpannable {

    private const val ESC = '\u001B'
    private const val CSI_START = '['

    private val STANDARD_COLORS = intArrayOf(
        Color.BLACK, Color.RED, Color.GREEN, Color.YELLOW,
        Color.BLUE, Color.MAGENTA, Color.CYAN, Color.WHITE
    )

    private val BRIGHT_COLORS = intArrayOf(
        Color.DKGRAY,
        Color.rgb(255, 85, 85), Color.rgb(85, 255, 85), Color.rgb(255, 255, 85),
        Color.rgb(85, 85, 255), Color.rgb(255, 85, 255), Color.rgb(85, 255, 255),
        Color.WHITE
    )

    fun parse(ansiText: String, defaultForegroundColor: Int? = null): SpannableString {
        val builder = SpannableStringBuilder()
        val state = AnsiState(defaultForegroundColor)
        var segmentStart = 0
        var i = 0

        while (i < ansiText.length) {
            if (ansiText[i] == ESC && i + 1 < ansiText.length && ansiText[i + 1] == CSI_START) {
                val seqEnd = findSequenceEnd(ansiText, i + 2)
                if (seqEnd == -1) {
                    builder.append(ansiText.substring(i))
                    break
                }

                applySpans(builder, state, segmentStart, builder.length)

                val params = ansiText.substring(i + 2, seqEnd)
                if (ansiText[seqEnd] == 'm') {
                    handleSgr(params, state)
                }

                i = seqEnd + 1
                segmentStart = builder.length
            } else {
                builder.append(ansiText[i])
                i++
            }
        }

        applySpans(builder, state, segmentStart, builder.length)
        return SpannableString(builder)
    }

    private fun findSequenceEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length) {
            val c = text[i]
            when (c) {
                in '0'..'9', ';', ':', '?', '!', '$', '"', '#', ' ' -> {
                    i++
                }

                in '@'..'~' -> {
                    return i
                }

                else -> {
                    return -1
                }
            }
        }
        return -1
    }

    private fun applySpans(spannable: Spannable, state: AnsiState, start: Int, end: Int) {
        if (start >= end) return

        state.foreground?.let {
            spannable.setSpan(ForegroundColorSpan(it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        state.background?.let {
            spannable.setSpan(BackgroundColorSpan(it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (state.bold) {
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (state.italic) {
            spannable.setSpan(
                StyleSpan(Typeface.ITALIC),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (state.underline) {
            spannable.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (state.strikethrough) {
            spannable.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun handleSgr(params: String, state: AnsiState) {
        if (params.isEmpty()) {
            state.reset()
            return
        }

        val parts = params.split(';')
        var i = 0
        while (i < parts.size) {
            val code = parts[i].trim().toIntOrNull() ?: 0

            when (code) {
                0 -> state.reset()
                1 -> state.bold = true
                3 -> state.italic = true
                4 -> state.underline = true
                7 -> state.reverse = true
                9 -> state.strikethrough = true
                22 -> state.bold = false
                23 -> state.italic = false
                24 -> state.underline = false
                27 -> state.reverse = false
                29 -> state.strikethrough = false
                39 -> state.foreground = state.defaultForeground
                49 -> state.background = null
                in 30..37 -> {
                    val color = STANDARD_COLORS[code - 30]
                    if (state.reverse) state.background = color else state.foreground = color
                }

                in 40..47 -> {
                    val color = STANDARD_COLORS[code - 40]
                    if (state.reverse) state.foreground = color else state.background = color
                }

                in 90..97 -> {
                    val color = BRIGHT_COLORS[code - 90]
                    if (state.reverse) state.background = color else state.foreground = color
                }

                in 100..107 -> {
                    val color = BRIGHT_COLORS[code - 100]
                    if (state.reverse) state.foreground = color else state.background = color
                }

                38 -> {
                    if (i + 1 < parts.size) {
                        when (parts[i + 1].trim()) {
                            "5" -> {
                                if (i + 2 < parts.size) {
                                    val index = parts[i + 2].trim().toIntOrNull() ?: 0
                                    if (state.reverse) state.background = get256Color(index)
                                    else state.foreground = get256Color(index)
                                    i += 2
                                }
                            }

                            "2" -> {
                                if (i + 4 < parts.size) {
                                    val r = parts[i + 2].trim().toIntOrNull() ?: 0
                                    val g = parts[i + 3].trim().toIntOrNull() ?: 0
                                    val b = parts[i + 4].trim().toIntOrNull() ?: 0
                                    val color = Color.rgb(r, g, b)
                                    if (state.reverse) state.background = color
                                    else state.foreground = color
                                    i += 4
                                }
                            }
                        }
                    }
                }

                48 -> {
                    if (i + 1 < parts.size) {
                        when (parts[i + 1].trim()) {
                            "5" -> {
                                if (i + 2 < parts.size) {
                                    val index = parts[i + 2].trim().toIntOrNull() ?: 0
                                    if (state.reverse) state.foreground = get256Color(index)
                                    else state.background = get256Color(index)
                                    i += 2
                                }
                            }

                            "2" -> {
                                if (i + 4 < parts.size) {
                                    val r = parts[i + 2].trim().toIntOrNull() ?: 0
                                    val g = parts[i + 3].trim().toIntOrNull() ?: 0
                                    val b = parts[i + 4].trim().toIntOrNull() ?: 0
                                    val color = Color.rgb(r, g, b)
                                    if (state.reverse) state.foreground = color
                                    else state.background = color
                                    i += 4
                                }
                            }
                        }
                    }
                }
            }
            i++
        }
    }

    private fun get256Color(index: Int): Int = when (index) {
        in 0..15 -> STANDARD_COLORS[index]
        in 16..231 -> {
            val r = (index - 16) / 36
            val g = ((index - 16) % 36) / 6
            val b = (index - 16) % 6
            Color.rgb(
                if (r == 0) 0 else 55 + r * 40,
                if (g == 0) 0 else 55 + g * 40,
                if (b == 0) 0 else 55 + b * 40
            )
        }

        in 232..255 -> {
            val level = 8 + (index - 232) * 10
            Color.rgb(level, level, level)
        }

        else -> Color.TRANSPARENT
    }

    private class AnsiState(
        val defaultForeground: Int?
    ) {
        var foreground: Int? = defaultForeground
        var background: Int? = null
        var bold = false
        var italic = false
        var underline = false
        var strikethrough = false
        var reverse = false

        fun reset() {
            foreground = defaultForeground
            background = null
            bold = false
            italic = false
            underline = false
            strikethrough = false
            reverse = false
        }
    }
}