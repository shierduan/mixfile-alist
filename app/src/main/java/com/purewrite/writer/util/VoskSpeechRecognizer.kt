package com.purewrite.writer.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.IOException

/**
 * 基于 Vosk 的离线中文语音识别
 *
 * 使用 16kHz 单声道 PCM 作为输入源，通过 AudioRecord 采集后传入 Vosk 识别器。
 * 无需联网，无需 API Key，完全本地处理。
 */
class VoskSpeechRecognizer(private val context: Context) {

    private val modelDirPath: String by lazy {
        VoskModelManager.getModelDir(context).absolutePath
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null

    @Volatile private var isRunning = false

    interface Callback {
        fun onResult(text: String)
        fun onPartial(text: String)
        fun onError(message: String)
        fun onTimeout()
    }

    /** 模型是否已就绪 */
    fun isModelReady(): Boolean = VoskModelManager.isModelReady(context)

    /** 初始化模型（耗时操作，应在后台线程调用） */
    @Throws(IOException::class)
    fun initModel() {
        if (model != null) return
        val dir = modelDirPath
        model = Model(dir)
        recognizer = Recognizer(model, 16000.0f)
    }

    /**
     * 开始语音识别
     * @param callback 结果回调
     * @return 是否成功启动
     */
    fun startListening(callback: Callback): Boolean {
        if (isRunning) return false
        try {
            if (model == null) initModel()
        } catch (e: Exception) {
            Log.e(TAG, "模型加载失败", e)
            callback.onError("模型加载失败：${e.message}")
            return false
        }

        val rec = recognizer ?: run {
            callback.onError("识别器未初始化")
            return false
        }

        try {
            speechService = SpeechService(rec, 16000.0f)
            val listener = object : RecognitionListener {
                override fun onResult(hypothesis: String?) {
                    // 兼容旧版方法名
                }
                override fun onFinalResult(hypothesis: String?) {
                    val text = parseText(hypothesis)
                    if (text.isNotBlank()) callback.onResult(text)
                }
                override fun onPartialResult(hypothesis: String?) {
                    val text = parsePartial(hypothesis)
                    if (text.isNotBlank()) callback.onPartial(text)
                }
                override fun onTimeout() {
                    isRunning = false
                    callback.onTimeout()
                }
                override fun onError(exception: Exception?) {
                    isRunning = false
                    callback.onError(exception?.message ?: "未知错误")
                }
            }
            val started = speechService?.startListening(listener) ?: false
            isRunning = started
            return started
        } catch (e: IOException) {
            Log.e(TAG, "麦克风启动失败", e)
            callback.onError("麦克风启动失败：${e.message}")
            return false
        }
    }

    /** 停止识别 */
    fun stopListening() {
        if (!isRunning) return
        isRunning = false
        try {
            speechService?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "停止 SpeechService 失败", e)
        }
    }

    /** 释放所有资源 */
    fun destroy() {
        stopListening()
        speechService = null
        try {
            recognizer?.close()
        } catch (_: Exception) {}
        recognizer = null
        model = null
    }

    /** 解析 Vosk 完整结果 JSON {"text": "..."} */
    private fun parseText(json: String?): String {
        if (json.isNullOrBlank()) return ""
        return try {
            JSONObject(json).optString("text", "").trim()
        } catch (e: Exception) {
            ""
        }
    }

    /** 解析 Vosk 部分结果 JSON {"partial": "..."} */
    private fun parsePartial(json: String?): String {
        if (json.isNullOrBlank()) return ""
        return try {
            JSONObject(json).optString("partial", "").trim()
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        private const val TAG = "VoskSpeech"
    }
}
