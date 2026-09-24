/*
 * 鏄熺伒 (XingLing) 路 浜睆楂橀鍒锋柊绐楀彛锛堢煭鏃跺墠鍙版湇鍔★級
 *
 * 鑱岃矗锛氬睆骞曠偣浜?瑙ｉ攣鍚庡紑鍚竴涓€屾渶澶?3 鍒嗛挓銆嶇殑 15 绉掕疆璇㈢獥鍙ｃ€? *   - Android 14+锛圓PI 34锛夎蛋 FOREGROUND_SERVICE_TYPE_SHORT_SERVICE + 瀵瑰簲鏉冮檺锛? *     鐢辩郴缁熶繚璇?3 鍒嗛挓涓婇檺锛堣秴鏃跺洖璋?onTimeout 鍋滄锛夛紱
 *   - 浣庣増鏈紙API 28~33锛夎蛋鏅€氬墠鍙版湇鍔?+ IMPORTANCE_MIN 闈欓粯閫氱煡娓犻亾锛? *     鐢辨湰鏈嶅姟鍐呴儴鐨?3 鍒嗛挓 deadline 鑷鍋滄銆? *   - 閫氱煡浠呭湪绐楀彛鏈熷唴瀛樺湪锛屽仠姝㈡椂绔嬪嵆鎾ら攢銆? *   - 鐔勫睆锛圓CTION_SCREEN_OFF锛夋垨鏄熺伒 App 鍥炲埌鍓嶅彴鏃剁珛鍗冲仠姝€? *   - 姣?15 绉掑彇涓€娆℃暟锛屽厛涓庡揩鐓ф瘮瀵癸細鏃犲彉鍖栬烦杩囬噸缁橈紝鏈夊彉鍖栨墠閲嶇粯銆? *
 * 鐢熷懡鍛ㄦ湡杈圭晫锛氳繘绋嬭鏉€ 鈫?绐楀彛闅忎箣缁撴潫锛堜笉甯搁┗銆佷笉闂归挓缁窇锛夛紝
 * 杩欐槸銆屼笉缁欓殢韬?WiFi 鍚庡彴閫犳垚鍏ㄥぉ鍊欏帇鍔涖€嶇殑鍏抽敭绾︽潫銆? *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.xingling.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WidgetFastRefreshService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        active = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopWindow()
            return START_NOT_STICKY
        }
        try {
            promoteToForeground()
        } catch (t: Throwable) {
            // 系统不允许此时提升前台（后台 FGS 限制 / Android 14 短服务配额）：
            // 立即安全退出，交给 goAsync 立即刷新与 WorkManager 兜底，绝不让系统抛出
            // ForegroundServiceDidNotStartInTimeException。
            stopSelf()
            return START_NOT_STICKY
        }
        if (pollJob?.isActive != true) {
            pollJob = scope.launch { runWindow() }
        }
        return START_NOT_STICKY
    }

    /** Android 14+ 鐭椂鍓嶅彴鏈嶅姟鍒扮偣鍥炶皟锛堢郴缁熺骇 3 鍒嗛挓纭笂闄愶級 */
    override fun onTimeout(startId: Int) {
        stopWindow()
    }

    override fun onDestroy() {
        active = false
        scope.cancel()
        super.onDestroy()
    }

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€ 绐楀彛涓讳綋 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private suspend fun runWindow() {
        val deadline = SystemClock.elapsedRealtime() + WidgetRefreshPolicy.WINDOW_MAX_MS
        var previous = WidgetSnapshotStore.read(this)

        while (scope.isActive && SystemClock.elapsedRealtime() < deadline) {
            if (!isScreenOn()) break // 熄屏：立即退出窗口
            val fresh = runCatching {
                WidgetDataLoader.load(this, commit = false)
            }.getOrNull()
            if (WidgetRefreshPolicy.shouldRepaint(previous, fresh) && fresh != null) {
                WidgetSnapshotStore.write(this, fresh)
                WidgetRefreshPolicy.repaint(this)
                previous = fresh
            }
            delay(WidgetRefreshPolicy.POLL_INTERVAL_MS)
        }
        stopWindow()
    }

    private fun stopWindow() {
        pollJob?.cancel()
        pollJob = null
        stopForegroundCompat()
        stopSelf()
    }

    private fun isScreenOn(): Boolean = runCatching {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isInteractive ?: false
    }.getOrDefault(false)

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€ 鍓嶅彴鍖?/ 閫氱煡 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private fun promoteToForeground() {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+锛氱煭鏃跺墠鍙版湇鍔★紙绯荤粺淇濊瘉 3 鍒嗛挓涓婇檺锛?            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "小组件实时刷新",
            NotificationManager.IMPORTANCE_MIN // 静默：不响铃、不亮屏、不进状态栏提示
        ).apply {
            description = "屏幕点亮时短暂刷新桌面小组件，最长 3 分钟"
            setShowBadge(false)
        }
        runCatching { nm.createNotificationChannel(channel) }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("正在刷新小组件")
            .setContentText("屏幕点亮期间实时同步设备状态（最长 3 分钟）")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()

    companion object {

        private const val CHANNEL_ID = "xingling_widget_fast_refresh"
        private const val NOTIFICATION_ID = 0x5747 // "WG"

        const val ACTION_STOP = "com.xingling.app.widget.action.STOP_FAST_REFRESH"

        @Volatile
        var active: Boolean = false
            private set

        /**
         * 寮€鍚埛鏂扮獥鍙ｃ€傝嫢绯荤粺绂佹鍚庡彴鍚姩鍓嶅彴鏈嶅姟锛圓ndroid 12+ 鐨勫悗鍙伴檺鍒讹級锛?         * 闄嶇骇涓轰竴娆℃€?WorkManager 绔嬪嵆鍒锋柊锛屼繚璇併€屼寒灞忓綋鍦哄埛涓€娆°€嶄笉钀界┖銆?         */
        fun start(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, WidgetFastRefreshService::class.java)
            runCatching { ContextCompat.startForegroundService(app, intent) }
                .onFailure { WidgetRefreshScheduler.enqueueImmediate(app) }
        }

        /** 鍋滄鍒锋柊绐楀彛锛堝箓绛夛紱App 鍥炲墠鍙般€佺唲灞忔椂璋冪敤锛?*/
        fun stop(context: Context) {
            runCatching {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, WidgetFastRefreshService::class.java)
                )
            }
        }
    }
}

