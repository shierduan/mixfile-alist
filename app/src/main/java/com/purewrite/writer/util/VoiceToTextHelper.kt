package com.purewrite.writer.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 语音转文字助手（Vosk 离线版）
 *
 * 负责权限校验，并把 VoskSpeechRecognizer 封装给 UI 使用。
 * 模型未就绪时，会通过回调让 UI 走下载流程。
 */
class VoiceToTextHelper(private val context: Context) {

    private val voskRecognizer = VoskSpeechRecognizer(context)

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            requestCode
        )
    }

    /** 模型是否已就绪 */
    fun isModelReady(): Boolean = voskRecognizer.isModelReady()

    /**
     * 开始语音识别
     * @param onResult 完整识别结果回调
     * @param onPartial 实时中间结果回调
     * @param onError 错误回调
     * @param onTimeout 识别超时或结束回调
     */
    fun startListening(
        onResult: (String) -> Unit,
        onPartial: (String) -> Unit = {},
        onError: (String) -> Unit,
        onTimeout: () -> Unit = {}
    ) {
        if (!hasRecordPermission()) {
            onError("缺少录音权限")
            return
        }
        if (!isModelReady()) {
            onError("MODEL_NOT_READY")
            return
        }

        // 在后台线程初始化模型并启动监听
        Thread {
            try {
                voskRecognizer.initModel()
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError("模型加载失败：${e.message}")
                }
                return@Thread
            }
            // 在主线程回调 UI
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val started = voskRecognizer.startListening(object : VoskSpeechRecognizer.Callback {
                override fun onResult(text: String) {
                    mainHandler.post { onResult(text) }
                }
                override fun onPartial(text: String) {
                    mainHandler.post { onPartial(text) }
                }
                override fun onError(message: String) {
                    mainHandler.post { onError(message) }
                }
                override fun onTimeout() {
                    mainHandler.post { onTimeout() }
                }
            })
            if (!started) {
                mainHandler.post { onError("语音识别启动失败") }
            }
        }.also { it.isDaemon = true }.start()
    }

    /** 停止语音识别 */
    fun stopListening() {
        voskRecognizer.stopListening()
    }

    /** 释放资源 */
    fun destroy() {
        voskRecognizer.destroy()
    }
}
