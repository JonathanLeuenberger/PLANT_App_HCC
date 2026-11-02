package com.example.plant_app_hcc

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.plant_app_hcc.databinding.FragmentChallengesBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlin.math.ceil

/** One destination renders the list or a chosen challenge; Navigation handles Back and Up. */
class ChallengesFragment : Fragment() {
    private var _binding: FragmentChallengesBinding? = null
    private val binding get() = _binding!!
    private lateinit var store: ChallengeStore
    private var completedTab = false
    private val selected get() = ChallengeStore.challenges.find {
        it.id == arguments?.getString("challengeId")
    }
    private val handler = Handler(Looper.getMainLooper())
    private val refreshTime = object : Runnable {
        override fun run() {
            if (_binding == null) return
            selected?.let { refreshDetail(it) }
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ChallengeStore(requireContext())
        completedTab = savedInstanceState?.getBoolean("completedTab") ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentChallengesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.challengeRewards.setOnClickListener { showRewards() }
        binding.challengeAvailable.setOnClickListener { completedTab = false; render() }
        binding.challengeCompleted.setOnClickListener { completedTab = true; render() }
        render()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshTime)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshTime)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("completedTab", completedTab)
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        binding.challengePoints.text = getString(R.string.challenge_points, store.points())
        val challenge = selected
        binding.challengeTabs.visibility = if (challenge == null) View.VISIBLE else View.GONE
        binding.challengeList.visibility = if (challenge == null) View.VISIBLE else View.GONE
        binding.challengeDetail.visibility = if (challenge == null) View.GONE else View.VISIBLE
        binding.challengeHeading.setText(challenge?.title ?: R.string.challenges_title)
        if (challenge == null) renderList() else renderDetail(challenge)
    }

    private fun renderList() {
        selectTab(binding.challengeAvailable, !completedTab)
        selectTab(binding.challengeCompleted, completedTab)
        val container = binding.challengeList
        container.removeAllViews()
        val visible = ChallengeStore.challenges.filter { store.progress(it).completed == completedTab }
        if (visible.isEmpty()) {
            container.addView(label(getString(if (completedTab) R.string.challenge_empty_completed
                else R.string.challenge_all_completed), 16).apply { setPadding(0, dp(24), 0, dp(24)) })
        }
        for (challenge in visible) {
            val progress = store.progress(challenge)
            val expired = progress.expired(challenge, System.currentTimeMillis())
            val card = MaterialCardView(requireContext()).apply {
                radius = dp(14).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                setStrokeColor(color(R.color.challenge_soft))
                setCardBackgroundColor(color(R.color.challenge_card))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
            }
            val content = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(12))
            }
            content.addView(label(getString(challenge.title), 21, true).apply {
                ViewCompat.setAccessibilityHeading(this, true)
                setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_challenges, 0, 0, 0)
                compoundDrawablePadding = dp(10)
            })
            content.addView(label(getString(challenge.description), 15).apply {
                setPadding(0, dp(8), 0, dp(8))
            })
            val summary = when {
                progress.completed -> getString(R.string.challenge_earned, challenge.points)
                expired -> getString(R.string.challenge_expired_card)
                progress.startedAt > 0 -> getString(R.string.challenge_progress,
                    progress.checked.size, challenge.steps.size) + " · " + timeLeft(challenge, progress)
                else -> getString(R.string.challenge_offer, challenge.points, challenge.hours / 24)
            }
            content.addView(label(summary, 14, true))
            val action = MaterialButton(requireContext()).apply {
                tag = "challenge:${challenge.id}"
                setText(when {
                    progress.completed -> R.string.challenge_view_completed
                    expired -> R.string.challenge_try_again
                    progress.startedAt > 0 -> R.string.challenge_continue
                    else -> R.string.challenge_start
                })
                minHeight = dp(48)
                cornerRadius = dp(10)
                backgroundTintList = ColorStateList.valueOf(color(R.color.challenge_green))
                setTextColor(color(R.color.white))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
                setOnClickListener {
                    if (!progress.completed) store.start(challenge)
                    findNavController().navigate(R.id.ChallengesFragment, Bundle().apply {
                        putString("challengeId", challenge.id)
                    })
                }
            }
            content.addView(action)
            card.addView(content)
            container.addView(card)
        }
    }

    private fun renderDetail(challenge: PlantChallenge) {
        binding.challengeDescription.setText(challenge.description)
        val progress = store.progress(challenge)
        binding.challengeSteps.removeAllViews()
        challenge.steps.forEachIndexed { index, text ->
            binding.challengeSteps.addView(MaterialCheckBox(requireContext()).apply {
                setText(text)
                textSize = 16f
                setTextColor(color(R.color.challenge_ink))
                minHeight = dp(52)
                isSaveEnabled = false // ChallengeStore is the source of truth after recreation.
                isChecked = index in progress.checked
                setOnCheckedChangeListener { _, checked ->
                    if (!store.check(challenge, index, checked)) {
                        setOnCheckedChangeListener(null)
                        isChecked = index in store.progress(challenge).checked
                    }
                    refreshDetail(challenge)
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        binding.challengeFinish.setOnClickListener {
            val old = store.progress(challenge)
            if (old.expired(challenge, System.currentTimeMillis())) {
                if (store.start(challenge)) renderDetail(challenge)
            } else if (store.finish(challenge)) {
                render()
                Snackbar.make(binding.root, getString(R.string.challenge_congratulations,
                    challenge.points), Snackbar.LENGTH_LONG).show()
            } else {
                refreshDetail(challenge)
            }
        }
        refreshDetail(challenge)
    }

    private fun refreshDetail(challenge: PlantChallenge) {
        val progress = store.progress(challenge)
        val expired = progress.expired(challenge, System.currentTimeMillis())
        binding.challengePoints.text = getString(R.string.challenge_points, store.points())
        binding.challengeDeadline.text = if (progress.completed)
            getString(R.string.challenge_earned, challenge.points)
        else if (expired) getString(R.string.challenge_expired_detail)
        else timeLeft(challenge, progress)
        binding.challengeProgressLabel.text = getString(R.string.challenge_progress,
            progress.checked.size, challenge.steps.size)
        binding.challengeProgressBar.max = challenge.steps.size
        binding.challengeProgressBar.progress = progress.checked.size
        for (i in 0 until binding.challengeSteps.childCount) {
            binding.challengeSteps.getChildAt(i).isEnabled =
                progress.startedAt > 0 && !expired && !progress.completed
        }
        binding.challengeFinish.setText(when {
            progress.completed -> R.string.challenge_completed
            expired -> R.string.challenge_try_again
            else -> R.string.challenge_finish
        })
        binding.challengeFinish.isEnabled = !progress.completed &&
            (expired || (progress.startedAt > 0 && progress.checked.size == challenge.steps.size))
        binding.challengeFinish.backgroundTintList = ColorStateList.valueOf(color(
            if (binding.challengeFinish.isEnabled) R.color.challenge_green else R.color.challenge_soft))
        binding.challengeFinish.setTextColor(color(
            if (binding.challengeFinish.isEnabled) R.color.white else R.color.challenge_ink))
        binding.challengeFinishHint.text = when {
            progress.completed -> getString(R.string.challenge_points_saved)
            expired -> getString(R.string.challenge_restart_hint)
            else -> getString(R.string.challenge_finish_hint, challenge.points)
        }
    }

    private fun timeLeft(challenge: PlantChallenge, progress: ChallengeProgress): String {
        val millis = (progress.startedAt + challenge.durationMillis - System.currentTimeMillis())
            .coerceAtLeast(0)
        val minutes = ceil(millis / 60_000.0).toInt().coerceAtLeast(1)
        return if (minutes <= 60) resources.getQuantityString(R.plurals.challenge_minutes_left, minutes, minutes)
        else {
            val hours = ceil(minutes / 60.0).toInt()
            resources.getQuantityString(R.plurals.challenge_hours_left, hours, hours)
        }
    }

    private fun showRewards() {
        val points = store.points()
        val badges = listOf(30 to R.string.challenge_badge_sprout,
            75 to R.string.challenge_badge_growing, 140 to R.string.challenge_badge_green_thumb)
        val lines = badges.joinToString("\n\n") { (threshold, title) ->
            getString(if (points >= threshold) R.string.challenge_badge_unlocked
                else R.string.challenge_badge_locked, getString(title), threshold)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.challenge_rewards)
            .setMessage(getString(R.string.challenge_rewards_intro, points) + "\n\n" + lines)
            .setPositiveButton(R.string.challenge_close, null)
            .show()
    }

    private fun selectTab(button: MaterialButton, selected: Boolean) {
        button.isSelected = selected
        ViewCompat.setStateDescription(button, getString(
            if (selected) R.string.challenge_selected else R.string.challenge_not_selected))
        button.backgroundTintList = ColorStateList.valueOf(color(
            if (selected) R.color.challenge_green else R.color.challenge_soft))
        button.setTextColor(color(if (selected) R.color.white else R.color.challenge_ink))
    }

    private fun label(text: String, size: Int, bold: Boolean = false) = TextView(requireContext()).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color(R.color.challenge_ink))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()

    override fun onDestroyView() {
        handler.removeCallbacks(refreshTime)
        _binding = null
        super.onDestroyView()
    }
}
