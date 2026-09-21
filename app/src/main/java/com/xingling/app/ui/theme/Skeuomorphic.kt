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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// ── iOS 27 Glass Morphism Card ──
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = iOSGlassWhite,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
                        .clip(shape)
            .background(backgroundColor)
            .padding(18.dp),
        content = content
    )
}

// ── iOS 27 Flat Button (Primary) ──
@Composable
fun iOSButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(14.dp),
    backgroundColor: Color = iOSBlue,
    contentColor: Color = Color.White,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(2.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
        content = content
    )
}

// ── iOS 27 Outline Button (Secondary) ──
@Composable
fun iOSOutlineButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, iOSSeparator, shape)
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
        content = content
    )
}

// ── Backward compat aliases for existing code ──
@Composable
fun SkeuomorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = iOSGlassWhite,
    content: @Composable BoxScope.() -> Unit
) = GlassCard(modifier, shape, backgroundColor, content)

@Composable
fun SkeuomorphicButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(14.dp),
    backgroundColor: Color = iOSBlue,
    content: @Composable BoxScope.() -> Unit
) = iOSButton(modifier, onClick, shape, backgroundColor, Color.White, content)
