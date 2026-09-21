/*
 * 星灵 (XingLing) · 小组件快照缓存
 *
 * 刷新成功后写入；设备不可达/口令失效时读出上一次成功快照，
 * 由小组件标注「缓存」继续展示，避免空白。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.widget

import android.content.Context

object WidgetSnapshotStore {

    private const val FILE_NAME = "xingling_widget_snapshot"
    private const val KEY_SNAPSHOT = "snapshot"

    fun read(context: Context): WidgetSnapshot? = try {
        WidgetSnapshot.fromJson(prefs(context).getString(KEY_SNAPSHOT, null))
    } catch (e: Throwable) {
        null
    }

    fun write(context: Context, snapshot: WidgetSnapshot) {
        try {
            prefs(context).edit().putString(KEY_SNAPSHOT, snapshot.toJson()).apply()
        } catch (e: Throwable) {
            // 缓存写入失败不影响小组件主流程
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
