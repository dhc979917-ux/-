package com.example.myreader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

// 承载 MediaProjection 的前台服务
// Android 14 (API 34) 规定：使用屏幕投影必须先启动 mediaProjection 类型的前台服务，否则直接抛异常
class MediaProjectionService : Service() {

    companion object {
        // 投影缓存在这里：授权一次，会话内可反复截图
        var 投影: MediaProjection? = null
            private set

        private const val 通知ID = 2001
        private const val 通道ID = "screen_capture"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(通道ID, "屏幕识别", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, 通道ID)
            .setContentTitle("万物皆可读")
            .setContentText("正在准备屏幕识别")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        // 先进入前台（带 mediaProjection 类型），再创建投影 —— 顺序不能反
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(通知ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(通知ID, notification)
        }

        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (resultCode != 0 && data != null) {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            投影?.stop()
            投影 = manager.getMediaProjection(resultCode, data)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        投影?.stop()
        投影 = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
