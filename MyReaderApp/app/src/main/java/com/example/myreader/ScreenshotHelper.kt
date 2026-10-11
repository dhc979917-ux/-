package com.example.myreader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.ByteArrayOutputStream

class ScreenshotHelper(private val context: Context) {
    private var activeProjection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { clearCapture() }
    }

    @Synchronized
    private fun clearCapture() {
        display?.release()
        reader?.close()
        activeProjection?.unregisterCallback(callback)
        display = null
        reader = null
        activeProjection = null
    }

    companion object {
        const val 授权请求码 = 1001
    }

    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    // 授权走系统弹窗，一个会话只需一次；拿到投影后缓存在前台服务里，后续截图复用
    fun 请求授权(activity: Activity) {
        activity.startActivityForResult(projectionManager.createScreenCaptureIntent(), 授权请求码)
    }

    fun 处理授权结果(resultCode: Int, data: Intent?) {
        // Android 14 强制要求：先启动 mediaProjection 类型的前台服务，再拿投影
        // （所以投影逻辑放在 MediaProjectionService 里，这里只负责转交授权结果）
        val service = Intent(context, MediaProjectionService::class.java)
        service.putExtra("resultCode", resultCode)
        service.putExtra("data", data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(service)
        } else {
            context.startService(service)
        }
    }

    fun 是否已授权(): Boolean = MediaProjectionService.投影 != null

    @Synchronized
    fun 截图(): Bitmap? {
        val projection = MediaProjectionService.投影 ?: return null
        return try {
            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            if (activeProjection !== projection) {
                clearCapture()
                activeProjection = projection
                projection.registerCallback(callback, Handler(Looper.getMainLooper()))
                reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                display = projection.createVirtualDisplay(
                "myreader_capture",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface,
                null,
                null
            )
            }
            reader?.acquireLatestImage()?.close()
            Thread.sleep(400)
            val image = reader?.acquireLatestImage()
            val bitmap = image?.use { img ->
                val plane = img.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                // ImageReader 的行宽可能带对齐填充，先整行拷出来再裁掉
                val full = Bitmap.createBitmap(
                    img.width + (rowStride - pixelStride * img.width) / pixelStride,
                    img.height,
                    Bitmap.Config.ARGB_8888
                )
                full.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(full, 0, 0, img.width, img.height)
                if (cropped !== full) full.recycle()
                cropped
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun 转Base64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    fun 释放() {
        clearCapture()
        context.stopService(Intent(context, MediaProjectionService::class.java))
    }
}
