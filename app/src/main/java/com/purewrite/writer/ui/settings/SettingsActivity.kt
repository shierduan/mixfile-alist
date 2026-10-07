package com.purewrite.writer.ui.settings

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.purewrite.writer.BuildConfig
import com.purewrite.writer.R
import com.purewrite.writer.databinding.ActivitySettingsBinding
import com.purewrite.writer.util.PrefsManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupViews()
    }

    private fun setupViews() {
        // Font size
        binding.seekFontSize.progress = prefs.fontSize - 12
        binding.tvFontSizeValue.text = "${prefs.fontSize}sp"
        binding.seekFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 12
                prefs.fontSize = size
                binding.tvFontSizeValue.text = "${size}sp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Line spacing
        val spacingProgress = ((prefs.lineSpacing - 1.0f) * 10).toInt()
        binding.seekLineSpacing.progress = spacingProgress
        binding.tvLineSpacingValue.text = "%.1f".format(prefs.lineSpacing)
        binding.seekLineSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val spacing = 1.0f + progress / 10.0f
                prefs.lineSpacing = spacing
                binding.tvLineSpacingValue.text = "%.1f".format(spacing)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Switches
        binding.switchAutoSave.isChecked = prefs.autoSave
        binding.switchAutoSave.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoSave = isChecked
        }

        binding.switchPunctuation.isChecked = prefs.punctuationConvert
        binding.switchPunctuation.setOnCheckedChangeListener { _, isChecked ->
            prefs.punctuationConvert = isChecked
        }

        binding.switchFullscreen.isChecked = prefs.fullscreenEditor
        binding.switchFullscreen.setOnCheckedChangeListener { _, isChecked ->
            prefs.fullscreenEditor = isChecked
        }

        // Daily goal
        val goalProgress = prefs.dailyWordGoal / 100
        binding.seekGoal.progress = goalProgress
        binding.tvGoalValue.text = "${prefs.dailyWordGoal} 字"
        binding.seekGoal.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val goal = (progress + 5) * 100
                prefs.dailyWordGoal = goal
                binding.tvGoalValue.text = "$goal 字"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Version
        binding.tvVersion.text = "${getString(R.string.version)} ${BuildConfig.VERSION_NAME}"
    }
}
