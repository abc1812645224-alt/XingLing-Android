/*
 * 星灵 (XingLing)
 * Copyright (C) 2026 XingLing Project
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.xingling.app.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    minTextSize: TextUnit = 9.sp,
    maxLines: Int = 2,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    // If original font size is not specified or invalid, fallback to 14.sp
    val initialFontSize = if (style.fontSize.isUnspecified) 14.sp else style.fontSize
    var textSize by remember(text) { mutableStateOf(initialFontSize) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    val resolvedStyle = style.copy(
        fontSize = textSize,
        fontWeight = fontWeight ?: style.fontWeight,
        textAlign = textAlign ?: style.textAlign
    )

    Text(
        text = text,
        color = color,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        maxLines = maxLines,
        softWrap = true,
        overflow = overflow,
        style = resolvedStyle,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowWidth || textLayoutResult.didOverflowHeight) {
                if (textSize.value > minTextSize.value) {
                    textSize = (textSize.value - 0.5f).sp
                } else {
                    readyToDraw = true
                }
            } else {
                readyToDraw = true
            }
        }
    )
}
