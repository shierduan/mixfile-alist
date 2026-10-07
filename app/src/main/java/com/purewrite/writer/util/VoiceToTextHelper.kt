package com.purewrite.writer.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.purewrite.writer.R

/**
 * 语音转文字助手，使用 Android 原生 SpeechRecognizer
 */
class VoiceToTextHelper(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var onResult: ((String) -> Unit)? = null
    private var onError: (() -> Unit)? = null

    /**
     * 检查是否有录音权限
     */
    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 请求录音权限
     */
    fun requestPermission(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            requestCode
        )
    }

    /**
     * 检查设备是否支持语音识别
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * 开始语音识别
     * @param onResult 识别结果回调（在主线程）
     * @param onError 错误回调
     */
    fun startListening(onResult: (String) -> Unit, onError: () -> Unit) {
        if (!isAvailable()) {
            Toast.makeText(context, R.string.voice_not_available, Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasRecordPermission()) {
            Toast.makeText(context, R.string.voice_permission_needed, Toast.LENGTH_LONG).show()
            return
        }

        this.onResult = onResult
        this.onError = onError

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val callback = this@VoiceToTextHelper.onError
                    this@VoiceToTextHelper.onError = null
                    callback?.invoke()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    val callback = this@VoiceToTextHelper.onResult
                    this@VoiceToTextHelper.onResult = null
                    callback?.invoke(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // 使用中文识别，支持简体
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.startListening(intent)
    }

    /**
     * 停止语音识别
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    /**
     * 释放资源
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
