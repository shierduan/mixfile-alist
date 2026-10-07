package com.purewrite.writer.ui.stats

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.purewrite.writer.App
import com.purewrite.writer.R
import com.purewrite.writer.databinding.ActivityStatsBinding
import com.purewrite.writer.util.PrefsManager
import com.purewrite.writer.util.WordCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding
    private val repository get() = (application as App).repository
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        loadStats()
    }

    private fun loadStats() {
        val goal = prefs.dailyWordGoal
        val todayWords = prefs.todayWords
        val progress = if (goal > 0) (todayWords.toFloat() / goal * 100).toInt() else 0

        binding.tvTodayWords.text = todayWords.toString()
        binding.tvGoalTarget.text = "/ $goal"
        binding.progressGoal.progress = progress.coerceIn(0, 100)
        binding.tvGoalStatus.text = if (todayWords >= goal) getString(R.string.goal_reached)
            else "还差 ${goal - todayWords} 字达成目标"

        lifecycleScope.launch {
            val books = repository.getAllBooks().first()
            var totalWords = 0
            var totalChapters = 0
            var totalParagraphs = 0
            val allContent = StringBuilder()

            books.forEach { book ->
                val chapters = repository.getChaptersByBook(book.id).first()
                totalChapters += chapters.size
                chapters.forEach { ch ->
                    totalWords += ch.wordCount
                    totalParagraphs += WordCounter.countParagraphs(ch.content)
                    allContent.append(ch.content)
                }
            }

            withContext(Dispatchers.Main) {
                binding.tvTotalWords.text = totalWords.toString()
                binding.tvTotalChapters.text = totalChapters.toString()
                binding.tvParagraphs.text = totalParagraphs.toString()
                val reading = WordCounter.estimateReadingTime(allContent.toString())
                binding.tvReadingTime.text = "$reading ${getString(R.string.minutes)}"
            }
        }
    }
}
