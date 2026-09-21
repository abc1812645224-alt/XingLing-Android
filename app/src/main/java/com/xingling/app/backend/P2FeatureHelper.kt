/*
 * 星灵 (XingLing) · P2 设备端二进制服务管理
 *
 * 通过 UFI-TOOLS /api/root_shell 直连设备后台，管理三类自行下载到
 * /data/local/tmp 的静态二进制服务：
 *   ① ADGuardHome   广告过滤 DNS（Web 管理 :3000）
 *   ② EasyTier       异地组网（easytier-core，默认 :11010）
 *   ③ EasyConnect    校园网 VPN（无官方静态二进制，需手动推送或自定义 URL）
 *
 * 统一流程：检测是否已安装 → 未安装则下载解压到 /data/local/tmp →
 * nohup 后台启停 → 状态/日志查看。全部 rootShell 执行，零第三方依赖。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

import kotlinx.coroutines.delay
import org.json.JSONObject

class P2FeatureHelper(
    private val api: UfiToolsApi,
    private val goform: UfiToolsGoform,
    private val host: String
) {

    /** 设备地址（用于 UI 拼接 Web 管理页 URL，如 http://host:3000） */
    val hostAddress: String get() = host

    // ═══════════════════ 公共基础方法 ═══════════════════

    /** 执行 root shell（同 P1 模式），默认 100s 超时 */
    private suspend fun rootShell(command: String): Result<String> =
        rootShellTimeout(command, 100_000)

    /** 执行 root shell，可指定更长超时（下载二进制用） */
    private suspend fun rootShellTimeout(command: String, timeoutMs: Int): Result<String> = runCatching {
        val body = JSONObject().put("command", command).put("timeout", timeoutMs)
        val j = JSONObject(api.post("/api/root_shell", body.toString(), timeout = timeoutMs + 5_000))
        val result = j.opt("result")
        when (result) {
            is String -> result
            is JSONObject -> result.optString("content", "")
            else -> throw IllegalStateException("root_shell 执行失败：${j.optString("error")}")
        }
    }

    /** 通用二进制检测：installed / running / pid / port / version */
    private suspend fun detectBinary(name: String, port: Int): BinaryServiceStatus {
        val installedOut = rootShell("command -v $name 2>/dev/null; ls -l /data/local/tmp/$name 2>/dev/null")
            .getOrElse { "" }
        val installed = installedOut.isNotBlank()

        val procOut = rootShell("ps -ef | grep -i $name | grep -v grep").getOrElse { "" }
        val running = procOut.isNotBlank()

        val pid = if (running) {
            rootShell("pidof $name 2>/dev/null").getOrElse { "" }.trim()
                .ifBlank {
                    procOut.lineSequence().firstOrNull()
                        ?.split(Regex("\\s+"))
                        ?.getOrNull(1)
                        ?.trim()
                        .orEmpty()
                }
                .substringBefore(' ')
        } else ""

        val portOpen = if (port > 0) {
            val hex = "%04X".format(port) // 如 3000 -> 0BB8
            rootShell("cat /proc/net/tcp 2>/dev/null | grep -i ':$hex' | head -1").getOrElse { "" }
                .isNotBlank()
        } else false

        val version = rootShell("/data/local/tmp/$name --version 2>&1 | head -1").getOrElse { "" }.trim()

        return BinaryServiceStatus(
            installed = installed,
            running = running,
            pid = pid,
            port = if (portOpen) port else 0,
            version = version,
            rawOutput = "installed=$installed running=$running pid=$pid portOpen=$portOpen\n$procOut".take(600)
        )
    }

    /** 检测 CPU ABI，归一为下载架构标签：arm64 / armv7 / x86_64 */
    private suspend fun archTag(): String {
        val abi = rootShell("getprop ro.product.cpu.abi").getOrElse { "" }.trim().lowercase()
        return when {
            abi.contains("arm64") || abi.contains("aarch64") -> "arm64"
            abi.contains("armv7") || abi.contains("armeabi") -> "armv7"
            abi.contains("x86_64") || abi.contains("amd64") -> "x86_64"
            else -> "arm64" // 未知架构默认按 arm64 下载
        }
    }

    /** AdGuard 官方 release 架构名（x86_64 对应 amd64） */
    private suspend fun adGuardArch(): String = when (archTag()) {
        "armv7" -> "armv7"
        "x86_64" -> "amd64"
        else -> "arm64"
    }

    /**
     * 通用下载 + 解压 + 落盘二进制：
     * 优先 curl -L，其次 wget，最后 toybox wget；按扩展名识别 tar.gz / zip；
     * 解压后 find 定位 binaryName，chmod +x 并移动到 destDir/binaryName。
     */
    private suspend fun downloadAndExtract(
        url: String,
        destDir: String,
        binaryName: String
    ): Result<String> = runCatching {
        require(url.startsWith("http://") || url.startsWith("https://")) { "下载地址必须以 http(s):// 开头" }
        val isZip = url.contains(".zip")
        val arcName = if (isZip) "xling_pkg.zip" else "xling_pkg.tar.gz"

        // 1) 下载（curl 优先，wget / toybox wget 兜底）
        val dlCmd = """
            cd $destDir
            rm -f $arcName
            if command -v curl >/dev/null 2>&1; then
              curl -fL --connect-timeout 15 -o $arcName '$url'
            elif command -v wget >/dev/null 2>&1; then
              wget -O $arcName '$url'
            else
              toybox wget -O $arcName '$url'
            fi
            ls -l $arcName
        """.trimIndent()
        rootShellTimeout(dlCmd, 240_000).getOrThrow()

        // 2) 解压
        val extCmd = if (isZip) """
            cd $destDir
            unzip -o $arcName >/dev/null 2>&1 || toybox unzip -o $arcName
        """.trimIndent() else """
            cd $destDir
            tar xzf $arcName 2>/dev/null || toybox tar xzf $arcName
        """.trimIndent()
        rootShellTimeout(extCmd, 120_000).getOrThrow()

        // 3) 定位二进制并移动到目标路径
        val locateCmd = """
            cd $destDir
            if find . -name '$binaryName' -type f -exec chmod +x '{}' \; -exec mv -f '{}' $destDir/$binaryName \; -print 2>/dev/null | grep -q .; then
              chmod +x $destDir/$binaryName
              ls -l $destDir/$binaryName
            else
              echo '__NOT_FOUND__'
            fi
        """.trimIndent()
        val locateOut = rootShell(locateCmd).getOrThrow()
        if (locateOut.contains("__NOT_FOUND__")) {
            throw IllegalStateException("解压完成，但压缩包中未找到 $binaryName")
        }

        // 4) 清理压缩包与解压残留目录
        rootShell("cd $destDir && rm -f $arcName; rm -rf AdGuardHome easytier-linux-* 2>/dev/null; true")
            .getOrThrow()

        "$destDir/$binaryName"
    }

    /** 校验二进制已落盘，否则抛出可读错误 */
    private suspend fun requireBinary(path: String) {
        val out = rootShell("ls -l $path 2>/dev/null && echo OK || echo NO").getOrElse { "" }
        require(out.contains("OK")) { "未安装 $path，请先安装后再启动" }
    }

    /** 读取设备端日志（末尾 200 行），供 UI 日志查看 */
    suspend fun readLog(file: String): Result<String> = runCatching {
        rootShell("tail -n 200 /data/local/tmp/$file 2>/dev/null || echo '(无日志)'").getOrThrow().trim()
    }

    // ═══════════════════ ① ADGuardHome ═══════════════════

    suspend fun getAdGuardStatus(): Result<BinaryServiceStatus> =
        runCatching { detectBinary("AdGuardHome", 3000) }

    suspend fun installAdGuard(): Result<String> = runCatching {
        val arch = adGuardArch()
        val url = "https://github.com/AdguardTeam/AdGuardHome/releases/latest/download/AdGuardHome_linux_$arch.tar.gz"
        downloadAndExtract(url, "/data/local/tmp", "AdGuardHome").getOrThrow()
    }

    suspend fun startAdGuard(): Result<Unit> = runCatching {
        requireBinary("/data/local/tmp/AdGuardHome")
        rootShell("cd /data/local/tmp && nohup ./AdGuardHome -h 0.0.0.0 -p 3000 > /data/local/tmp/adguard.log 2>&1 &")
            .getOrThrow()
        delay(1500)
        val st = detectBinary("AdGuardHome", 3000)
        if (!st.running) throw IllegalStateException("AdGuardHome 启动后未检测到进程，请查看日志")
    }

    suspend fun stopAdGuard(): Result<Unit> = runCatching {
        rootShell("pkill -f AdGuardHome 2>/dev/null; sleep 1; pkill -9 -f AdGuardHome 2>/dev/null; true")
            .getOrThrow()
        Unit
    }

    // ═══════════════════ ② EasyTier 异地组网 ═══════════════════

    suspend fun getEasyTierStatus(): Result<BinaryServiceStatus> =
        runCatching { detectBinary("easytier-core", 11010) }

    suspend fun installEasyTier(): Result<String> = runCatching {
        val arch = archTag()
        val url = "https://github.com/EasyTier/EasyTier/releases/latest/download/easytier-linux-$arch.zip"
        downloadAndExtract(url, "/data/local/tmp", "easytier-core").getOrThrow()
    }

    suspend fun startEasyTier(config: EasyTierConfig): Result<Unit> = runCatching {
        require(config.nodeAddress.isNotBlank()) { "请填写虚拟 IP（节点地址）" }
        require(config.secret.isNotBlank()) { "请填写网络密钥" }
        require(config.peers.isNotBlank()) { "请填写对端地址（如 tcp://公网IP:11010）" }
        requireBinary("/data/local/tmp/easytier-core")
        val cmd = "cd /data/local/tmp && nohup ./easytier-core " +
            "-i '${config.nodeAddress}' -n '${config.secret}' -p '${config.peers}' " +
            "> /data/local/tmp/easytier.log 2>&1 &"
        rootShell(cmd).getOrThrow()
        delay(1500)
        val st = detectBinary("easytier-core", 11010)
        if (!st.running) throw IllegalStateException("EasyTier 启动后未检测到进程，请查看日志")
    }

    suspend fun stopEasyTier(): Result<Unit> = runCatching {
        rootShell("pkill -f easytier 2>/dev/null; sleep 1; pkill -9 -f easytier 2>/dev/null; true")
            .getOrThrow()
        Unit
    }

    // ═══════════════════ ③ EasyConnect 校园网 VPN ═══════════════════

    suspend fun getEasyConnectStatus(): Result<BinaryServiceStatus> =
        runCatching { detectBinary("EasyConnect", 0) }

    /**
     * EasyConnect 无官方静态二进制：仅检测 /data/local/tmp/EasyConnect 是否存在。
     * 不存在则抛出可读错误，提示用户手动 adb push 或使用 installEasyConnectFromUrl。
     */
    suspend fun installEasyConnect(): Result<String> = runCatching {
        val out = rootShell("ls -l /data/local/tmp/EasyConnect 2>/dev/null && echo OK || echo __MISSING__")
            .getOrThrow()
        if (out.contains("__MISSING__")) {
            throw IllegalStateException(
                "未在 /data/local/tmp/EasyConnect 发现二进制。EasyConnect 无官方静态二进制，" +
                    "请通过 adb push 手动推送，或使用下方「自定义 URL 下载」。"
            )
        }
        "/data/local/tmp/EasyConnect"
    }

    /** 从用户指定 URL 下载 EasyConnect 单文件二进制并落盘 */
    suspend fun installEasyConnectFromUrl(url: String): Result<String> = runCatching {
        require(url.startsWith("http://") || url.startsWith("https://")) { "下载地址必须以 http(s):// 开头" }
        val dlCmd = """
            cd /data/local/tmp
            rm -f ec.bin
            if command -v curl >/dev/null 2>&1; then
              curl -fL --connect-timeout 15 -o ec.bin '$url'
            elif command -v wget >/dev/null 2>&1; then
              wget -O ec.bin '$url'
            else
              toybox wget -O ec.bin '$url'
            fi
            mv -f ec.bin /data/local/tmp/EasyConnect
            chmod +x /data/local/tmp/EasyConnect
            ls -l /data/local/tmp/EasyConnect
        """.trimIndent()
        rootShellTimeout(dlCmd, 240_000).getOrThrow()
        "/data/local/tmp/EasyConnect"
    }

    suspend fun startEasyConnect(username: String, password: String, server: String): Result<Unit> =
        runCatching {
            require(username.isNotBlank()) { "请填写账号" }
            require(password.isNotBlank()) { "请填写密码" }
            require(server.isNotBlank()) { "请填写服务器地址" }
            requireBinary("/data/local/tmp/EasyConnect")
            val cmd = "cd /data/local/tmp && echo '${password}' | nohup ./EasyConnect " +
                "-u '${username}' -p '${password}' -s '${server}' > /data/local/tmp/easyconnect.log 2>&1 &"
            rootShell(cmd).getOrThrow()
            delay(1500)
            val st = detectBinary("EasyConnect", 0)
            if (!st.running) throw IllegalStateException("EasyConnect 启动后未检测到进程，请查看日志")
        }

    suspend fun stopEasyConnect(): Result<Unit> = runCatching {
        rootShell("pkill -f EasyConnect 2>/dev/null; sleep 1; pkill -9 -f EasyConnect 2>/dev/null; true")
            .getOrThrow()
        Unit
    }
}
