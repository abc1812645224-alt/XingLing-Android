/*
 * 星灵 (XingLing) · P1 组功能后端实现（root_shell 系统级控制）
 *
 * 经 UFI-TOOLS /api/root_shell 在设备上执行 root shell，实现：
 *   ① CPU 核心控制（online / governor / max_freq）
 *   ② 电池定量停充（charge_control_limit / charging_enabled 节点探测与写入）
 *   ③ 开机自启脚本（/data/local/tmp/init.d/）
 *   ④ Crontab 定时任务（crontab -l / crontab <file>）
 *
 * 方法签名与 DeviceFeatures 的 P1 批（G 段）一致，可作为
 * UfiToolsBackend.features 的替换/扩展实现接入。所有写操作均先探测节点，
 * 文件/节点不存在时返回友好错误而非崩溃；每条命令带 2>&1 捕获错误输出。
 *
 * 注意：shell 变量在 Kotlin 字符串中以 ${'$'} 转义，避免被 Kotlin 字符串模板误解析。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

import android.util.Base64
import org.json.JSONObject

class P1FeatureHelper(
    private val api: UfiToolsApi,
    private val goform: UfiToolsGoform,
    private val host: String
) {

    /** root shell 调用：POST /api/root_shell，解析 result（字符串或 {content:...}） */
    private suspend fun rootShell(command: String): Result<String> = runCatching {
        val j = JSONObject(
            api.post(
                "/api/root_shell",
                JSONObject()
                    .put("command", command)
                    .put("timeout", 100000)
                    .toString()
            )
        )
        val result = j.opt("result")
        when (result) {
            is String -> result
            is JSONObject -> result.optString("content", "")
            else -> throw IllegalStateException("root_shell 失败：${j.optString("error")}")
        }
    }

    /** 便捷执行：失败直接抛异常（供 runCatching 包裹） */
    private suspend fun sh(command: String): String = rootShell(command).getOrThrow()

    // ═══════════════════ ① CPU 核心控制 ═══════════════════

    /**
     * 一条复合命令批量读取所有核心状态。
     * 输出形如：
     *   === cpu0 ===
     *   online=1
     *   governor=schedutil
     *   max=1804800
     *   cur=652800
     */
    suspend fun getCpuCoreInfo(): Result<List<CpuCoreInfo>> = runCatching {
        val cmd = """
            for i in /sys/devices/system/cpu/cpu[0-9]*; do
              n=${'$'}(basename "${'$'}i")
              echo "=== ${'$'}n ==="
              echo "online=${'$'}(cat "${'$'}i/online" 2>/dev/null || echo 1)"
              echo "governor=${'$'}(cat "${'$'}i/cpufreq/scaling_governor" 2>/dev/null)"
              echo "max=${'$'}(cat "${'$'}i/cpufreq/scaling_max_freq" 2>/dev/null)"
              echo "cur=${'$'}(cat "${'$'}i/cpufreq/scaling_cur_freq" 2>/dev/null)"
            done 2>&1
        """.trimIndent()
        parseCpuCoreInfo(sh(cmd))
    }

    private fun parseCpuCoreInfo(out: String): List<CpuCoreInfo> {
        val list = ArrayList<CpuCoreInfo>()
        var core = -1
        var online = true
        var governor = ""
        var max = 0
        var cur = 0
        fun flush() {
            if (core >= 0) list.add(CpuCoreInfo(core, online, governor, max, cur))
        }
        out.lineSequence().forEach { raw ->
            val line = raw.trim()
            val head = Regex("""^===\s*(cpu(\d+))\s*===$""").find(line)
            when {
                head != null -> {
                    flush()
                    core = head.groupValues[2].toIntOrNull() ?: -1
                    online = true; governor = ""; max = 0; cur = 0
                }
                line.startsWith("online=") -> online = line.substringAfter("online=").trim() == "1"
                line.startsWith("governor=") -> governor = line.substringAfter("governor=").trim()
                line.startsWith("max=") -> max = line.substringAfter("max=").trim().toIntOrNull() ?: 0
                line.startsWith("cur=") -> cur = line.substringAfter("cur=").trim().toIntOrNull() ?: 0
            }
        }
        flush()
        if (list.isEmpty()) throw IllegalStateException("未检测到 CPU 核心节点（/sys/devices/system/cpu/）")
        return list
    }

    suspend fun setCpuCoreOnline(core: Int, online: Boolean): Result<Unit> = runCatching {
        require(core >= 0) { "核心号非法" }
        val v = if (online) 1 else 0
        sh("echo $v > /sys/devices/system/cpu/cpu$core/online 2>&1")
        Unit
    }

    suspend fun setCpuGovernor(core: Int, governor: String): Result<Unit> = runCatching {
        require(core >= 0) { "核心号非法" }
        val g = governor.trim()
        require(g.isNotEmpty()) { "调度策略不能为空" }
        sh("echo '$g' > /sys/devices/system/cpu/cpu$core/cpufreq/scaling_governor 2>&1")
        Unit
    }

    suspend fun setCpuMaxFreq(core: Int, freqKhz: Int): Result<Unit> = runCatching {
        require(core >= 0) { "核心号非法" }
        require(freqKhz > 0) { "频率必须大于 0" }
        sh("echo $freqKhz > /sys/devices/system/cpu/cpu$core/cpufreq/scaling_max_freq 2>&1")
        Unit
    }

    suspend fun getCpuAvailableGovernors(): Result<List<String>> = runCatching {
        val out = sh("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>&1")
            .trim()
        if (out.startsWith("cat:")) throw IllegalStateException("读取可用调度策略失败：$out")
        out.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    suspend fun getCpuAvailableFreqs(core: Int): Result<List<Int>> = runCatching {
        require(core >= 0) { "核心号非法" }
        val out = sh("cat /sys/devices/system/cpu/cpu$core/cpufreq/scaling_available_frequencies 2>&1")
            .trim()
        if (out.startsWith("cat:")) throw IllegalStateException("读取可用频率失败：$out")
        out.split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
    }

    // ═══════════════════ ② 电池定量停充 ═══════════════════

    private val batteryDir = "/sys/class/power_supply/battery"

    /** 探测充电控制节点：列出全部节点，定位限流节点与充电使能节点，并读当前值 */
    suspend fun getBatteryChargeInfo(): Result<BatteryChargeInfo> = runCatching {
        val cmd = """
            D=$batteryDir
            echo "==LIST=="
            ls "${'$'}D" 2>&1
            LIM=""
            EN=""
            for n in charge_control_limit charge_control_limit_max charge_stop_threshold; do
              if [ -e "${'$'}D/${'$'}n" ]; then LIM="${'$'}D/${'$'}n"; break; fi
            done
            for n in battery_charging_enabled charging_enabled; do
              if [ -e "${'$'}D/${'$'}n" ]; then EN="${'$'}D/${'$'}n"; break; fi
            done
            echo "LIM=${'$'}LIM"
            echo "EN=${'$'}EN"
            echo "LVAL=${'$'}(cat "${'$'}LIM" 2>/dev/null)"
            echo "EVAL=${'$'}(cat "${'$'}EN" 2>/dev/null)"
        """.trimIndent()
        val out = sh(cmd)

        var limNode = ""
        var enNode = ""
        var lval = ""
        var eval = ""
        val listBuilder = StringBuilder()
        out.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line == "==LIST==" -> Unit
                line.startsWith("LIM=") -> limNode = line.substringAfter("LIM=").trim()
                line.startsWith("EN=") -> enNode = line.substringAfter("EN=").trim()
                line.startsWith("LVAL=") -> lval = line.substringAfter("LVAL=").trim()
                line.startsWith("EVAL=") -> eval = line.substringAfter("EVAL=").trim()
                else -> listBuilder.appendLine(line)
            }
        }

        // 限流值判断：节点值在 0..100 视为百分比，否则视为 mAh 等单位（按 100 兜底）
        var percent = 100
        lval.toIntOrNull()?.let { v -> if (v in 0..100) percent = v }
        val chargingOn = when {
            eval.isBlank() -> true
            else -> eval.trim() == "1" || eval.equals("true", true) || eval.equals("Y", true)
        }
        BatteryChargeInfo(
            controlNode = if (limNode.isNotBlank()) limNode else enNode,
            chargeLimitPercent = percent,
            chargingEnabled = chargingOn,
            rawOutput = listBuilder.toString().trim()
        )
    }

    suspend fun setBatteryChargeLimit(percent: Int): Result<Unit> = runCatching {
        require(percent in 50..100) { "充电上限需在 50~100 之间" }
        val cmd = """
            D=$batteryDir
            LIM=""
            for n in charge_control_limit charge_control_limit_max charge_stop_threshold; do
              if [ -e "${'$'}D/${'$'}n" ]; then LIM="${'$'}D/${'$'}n"; break; fi
            done
            if [ -z "${'$'}LIM" ]; then echo "NO_LIMIT_NODE"; exit 0; fi
            echo $percent > "${'$'}LIM" 2>&1 && echo "LIMIT_OK" || echo "LIMIT_FAIL"
        """.trimIndent()
        val out = sh(cmd)
        when {
            out.contains("NO_LIMIT_NODE") ->
                throw IllegalStateException("未探测到充电限流节点（charge_control_limit 等）")
            out.contains("LIMIT_FAIL") ->
                throw IllegalStateException("写入充电上限失败（节点只读或不支持）")
        }
        Unit
    }

    suspend fun setBatteryChargingEnabled(enabled: Boolean): Result<Unit> = runCatching {
        val bit = if (enabled) 1 else 0
        val cmd = """
            D=$batteryDir
            EN=""
            for n in battery_charging_enabled charging_enabled; do
              if [ -e "${'$'}D/${'$'}n" ]; then EN="${'$'}D/${'$'}n"; break; fi
            done
            if [ -z "${'$'}EN" ]; then echo "NO_ENABLE_NODE"; exit 0; fi
            echo $bit > "${'$'}EN" 2>&1 && echo "ENABLE_OK" || echo "ENABLE_FAIL"
        """.trimIndent()
        val out = sh(cmd)
        when {
            out.contains("NO_ENABLE_NODE") ->
                throw IllegalStateException("未探测到充电使能节点（battery_charging_enabled 等）")
            out.contains("ENABLE_FAIL") ->
                throw IllegalStateException("写入充电开关失败（节点只读或不支持）")
        }
        Unit
    }

    // ═══════════════════ ③ 开机自启脚本 ═══════════════════

    private val initDir = "/data/local/tmp/init.d"

    /** 校验脚本名，防止路径穿越 */
    private fun safeName(name: String): String {
        val n = name.trim()
        require(n.isNotEmpty()) { "脚本名不能为空" }
        require(!n.contains("/") && !n.contains("\\") && !n.contains("..")) {
            "脚本名含非法字符（仅文件名，不含路径）"
        }
        return n
    }

    suspend fun listBootScripts(): Result<List<BootScript>> = runCatching {
        val cmd = """
            D=$initDir
            mkdir -p "${'$'}D" 2>&1
            for f in "${'$'}D"/*; do
              [ -e "${'$'}f" ] || continue
              n=${'$'}(basename "${'$'}f")
              echo "===SCRIPT=== ${'$'}n"
              echo "EXEC=${'$'}([ -x "${'$'}f" ] && echo 1 || echo 0)"
              echo "---CONTENT---"
              cat "${'$'}f" 2>&1
              echo "===END==="
            done
        """.trimIndent()
        parseBootScripts(sh(cmd))
    }

    private fun parseBootScripts(out: String): List<BootScript> {
        val list = ArrayList<BootScript>()
        var name = ""
        var exec = true
        var inContent = false
        val content = StringBuilder()
        fun flush() {
            if (name.isNotBlank()) {
                list.add(BootScript(name, content.toString().trim(), exec))
            }
        }
        out.lineSequence().forEach { raw ->
            val line = raw
            when {
                line.startsWith("===SCRIPT===") -> {
                    flush()
                    name = line.removePrefix("===SCRIPT===").trim()
                    exec = true; content.clear(); inContent = false
                }
                line.startsWith("EXEC=") -> exec = line.removePrefix("EXEC=").trim() == "1"
                line == "---CONTENT---" -> inContent = true
                line == "===END===" -> { inContent = false }
                inContent -> {
                    if (content.isNotEmpty()) content.append('\n')
                    content.append(line)
                }
            }
        }
        flush()
        return list
    }

    suspend fun saveBootScript(name: String, content: String): Result<Unit> = runCatching {
        val n = safeName(name)
        val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val cmd = """
            D=$initDir
            mkdir -p "${'$'}D" 2>&1
            printf '%s' '$b64' | base64 -d > "${'$'}D/$n" 2>&1 && echo "SAVE_OK" || echo "SAVE_FAIL"
        """.trimIndent()
        val out = sh(cmd)
        if (out.contains("SAVE_FAIL")) throw IllegalStateException("写入脚本失败：$out")
        Unit
    }

    suspend fun deleteBootScript(name: String): Result<Unit> = runCatching {
        val n = safeName(name)
        val out = sh("rm -f $initDir/'$n' 2>&1 && echo RM_OK || echo RM_FAIL")
        if (out.contains("RM_FAIL")) throw IllegalStateException("删除脚本失败：$out")
        Unit
    }

    suspend fun setBootScriptEnabled(name: String, enabled: Boolean): Result<Unit> = runCatching {
        val n = safeName(name)
        val mode = if (enabled) 755 else 644
        val out = sh("chmod $mode $initDir/'$n' 2>&1 && echo CHMOD_OK || echo CHMOD_FAIL")
        if (out.contains("CHMOD_FAIL")) throw IllegalStateException("修改权限失败：$out")
        Unit
    }

    suspend fun runBootScript(name: String): Result<String> = runCatching {
        val n = safeName(name)
        sh("sh $initDir/'$n' 2>&1")
    }

    // ═══════════════════ ④ Crontab 定时任务 ═══════════════════

    suspend fun getCrontab(): Result<String> = runCatching {
        val out = sh("crontab -l 2>&1 || echo '(无 crontab 任务)'")
        out.trim()
    }

    suspend fun setCrontab(content: String): Result<Unit> = runCatching {
        val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val cmd = """
            printf '%s' '$b64' | base64 -d > /data/local/tmp/.crontab_tmp 2>&1 \
              && crontab /data/local/tmp/.crontab_tmp 2>&1 && echo "CRON_OK" || echo "CRON_FAIL"
        """.trimIndent()
        val out = sh(cmd)
        if (out.contains("CRON_FAIL")) throw IllegalStateException("写入 crontab 失败：$out")
        Unit
    }
}
