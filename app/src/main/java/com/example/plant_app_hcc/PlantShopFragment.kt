package com.example.plant_app_hcc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.plant_app_hcc.search.Ui

class PlantShopFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val store = ChallengeStore(context)
        val page = Ui.column(context).apply { setBackgroundColor(Ui.BG) }
        val heading = Ui.column(context).apply {
            setPadding(Ui.dp(context, 20f), Ui.dp(context, 16f), Ui.dp(context, 20f), Ui.dp(context, 8f))
        }
        val balance = Ui.text(context, "", 22, Ui.INK, true).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        heading.addView(balance)
        page.addView(heading)

        val content = Ui.column(context).apply {
            setPadding(Ui.dp(context, 20f), 0, Ui.dp(context, 20f), Ui.dp(context, 12f))
        }
        content.addView(Ui.text(context, getString(R.string.plant_shop_intro), 15, Ui.INK, false))
        Ui.gap(content, 14)
        val actions = PotStyle.entries.filter { it.price > 0 }.associateWith { pot ->
            val card = Ui.card(context)
            val header = Ui.row(context)
            header.addView(ImageView(context).apply {
                setImageResource(pot.icon)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(Ui.dp(context, 42f), Ui.dp(context, 42f)).apply {
                marginEnd = Ui.dp(context, 12f)
            })
            header.addView(Ui.text(context, getString(pot.title), 18, Ui.INK, true))
            card.addView(header)
            val action = Ui.button(context, "", true) {}
            card.addView(action)
            content.addView(card)
            action
        }

        fun refreshShop() {
            val available = store.availablePoints()
            val owned = store.ownedPots()
            balance.text = getString(R.string.plant_shop_balance, available)
            actions.forEach { (pot, action) ->
                val isOwned = pot in owned
                action.text = if (isOwned) getString(R.string.plant_shop_owned)
                    else getString(R.string.plant_shop_buy, pot.price)
                action.isEnabled = !isOwned && available >= pot.price
                action.alpha = if (action.isEnabled) 1f else 0.6f
            }
        }

        actions.forEach { (pot, action) ->
            action.setOnClickListener {
                if (store.buyPot(pot)) {
                    Toast.makeText(context, getString(R.string.plant_shop_purchased, getString(pot.title)),
                        Toast.LENGTH_SHORT).show()
                }
                refreshShop()
            }
        }
        refreshShop()
        page.addView(Ui.scroll(context, content),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return page
    }
}
