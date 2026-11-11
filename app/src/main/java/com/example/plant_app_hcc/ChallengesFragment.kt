package com.example.plant_app_hcc

import android.content.res.ColorStateList
import android.os.Bundle
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** One destination renders the list or a chosen challenge; Navigation handles Back and Up. */
class ChallengesFragment : Fragment() {
    private var _binding: FragmentChallengesBinding? = null
    private val binding get() = _binding!!
    private lateinit var store: ChallengeStore
    private var completedTab = false
    private val selected get() = PlantChallenge.entries.find {
        it.id == arguments?.getString("challengeId")
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
        binding.challengeShop.setOnClickListener {
            findNavController().navigate(R.id.action_ChallengesFragment_to_PlantShopFragment)
        }
        binding.challengeAvailable.setOnClickListener { completedTab = false; render() }
        binding.challengeCompleted.setOnClickListener { completedTab = true; render() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("completedTab", completedTab)
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        val progress = store.progress()
        binding.challengePoints.text = getString(R.string.challenge_points, store.points())
        val challenge = selected
        binding.challengeShop.visibility = if (challenge == null) View.VISIBLE else View.GONE
        binding.challengeTabs.visibility = if (challenge == null) View.VISIBLE else View.GONE
        binding.challengeList.visibility = if (challenge == null) View.VISIBLE else View.GONE
        binding.challengeDetail.visibility = if (challenge == null) View.GONE else View.VISIBLE
        binding.challengeHeading.setText(challenge?.title ?: R.string.challenges_title)
        if (challenge == null) renderList(progress) else renderDetail(challenge, progress.getValue(challenge))
    }

    private fun renderList(progress: Map<PlantChallenge, ChallengeProgress>) {
        selectTab(binding.challengeAvailable, !completedTab)
        selectTab(binding.challengeCompleted, completedTab)
        val container = binding.challengeList
        container.removeAllViews()
        val visible = PlantChallenge.entries.filter { progress.getValue(it).completed == completedTab }
        if (visible.isEmpty()) {
            container.addView(label(getString(if (completedTab) R.string.challenge_empty_completed
                else R.string.challenge_all_completed), 16).apply { setPadding(0, dp(24), 0, dp(24)) })
        }
        for (challenge in visible) {
            val status = progress.getValue(challenge)
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
            val summary = if (status.completed) getString(R.string.challenge_earned, challenge.points)
                else getString(R.string.challenge_progress_reward, status.count, challenge.goal, challenge.points)
            content.addView(label(summary, 14, true))
            val action = MaterialButton(requireContext()).apply {
                tag = "challenge:${challenge.id}"
                setText(if (status.completed) R.string.challenge_view_completed else R.string.challenge_view)
                minHeight = dp(48)
                cornerRadius = dp(10)
                backgroundTintList = ColorStateList.valueOf(color(R.color.challenge_green))
                setTextColor(color(R.color.white))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
                setOnClickListener {
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

    private fun renderDetail(challenge: PlantChallenge, progress: ChallengeProgress) {
        binding.challengeDescription.setText(challenge.description)
        binding.challengeStatus.text = getString(
            if (progress.completed) R.string.challenge_earned else R.string.challenge_offer, challenge.points)
        binding.challengeProgressLabel.text = getString(R.string.challenge_progress,
            progress.count, challenge.goal)
        binding.challengeProgressBar.max = challenge.goal
        binding.challengeProgressBar.progress = progress.count
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
        _binding = null
        super.onDestroyView()
    }
}
