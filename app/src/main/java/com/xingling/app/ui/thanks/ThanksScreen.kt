/*
 * 星灵 (XingLing) · 首启致谢页
 *
 * 依据任务 spec，首次启动展示版权致谢，注明本应用参考 UFI-TOOLS 开源项目
 * （作者 kanoqwq / Minikano，MIT License）。仅首启展示，用户同意后标记，
 * 之后直接进入主流程。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.thanks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSCardBackground
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSBackground

@Composable
fun ThanksScreen(onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(iOSBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("星灵", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = iOSLabel)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "移动网络控制中枢",
                fontSize = 15.sp,
                color = iOSSecondaryLabel
            )
            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "开源致谢",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iOSLabel
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "本应用的设备后台接入协议与部分界面设计参考了开源项目 " +
                            "UFI-TOOLS（作者 kanoqwq / Minikano，MIT License）。\n\n" +
                            "UFI-TOOLS 提供了面向随身 WiFi / 路由器的 JSON API 管理能力，" +
                            "本应用在其基础上重新设计并实现。感谢开源作者的贡献。",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = iOSLabel
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "本应用自身基于 GPL-3.0 开源发布，源码与致谢可随时在关于页查看。",
                        fontSize = 12.sp,
                        color = iOSSecondaryLabel
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = iOSBlue)
            ) {
                Text("同意并开始使用", fontSize = 16.sp, color = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "仅首次启动展示，可在设置中随时重新查看",
                fontSize = 12.sp,
                color = iOSSecondaryLabel,
                textAlign = TextAlign.Center
            )
        }
    }
}
