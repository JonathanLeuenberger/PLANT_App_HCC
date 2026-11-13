package com.example.plant_app_hcc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Button
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.example.plant_app_hcc.search.Ui

class PlantShopFragment : Fragment() {
    private var showBackgrounds = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showBackgrounds = savedInstanceState?.getBoolean("showBackgrounds") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("showBackgrounds", showBackgrounds)
        super.onSaveInstanceState(outState)
    }

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
        Ui.gap(heading, 8)
        val tabs = Ui.row(context)
        val pots = Ui.small(context, getString(R.string.plant_shop_pots)) {}
        val backgrounds = Ui.small(context, getString(R.string.plant_shop_backgrounds)) {}
        tabs.addView(pots, LinearLayout.LayoutParams(0, Ui.dp(context, 48f), 1f).apply {
            marginEnd = Ui.dp(context, 8f)
        })
        tabs.addView(backgrounds, LinearLayout.LayoutParams(0, Ui.dp(context, 48f), 1f))
        heading.addView(tabs)
        page.addView(heading)

        val content = Ui.column(context).apply {
            setPadding(Ui.dp(context, 20f), 0, Ui.dp(context, 20f), Ui.dp(context, 12f))
        }
        val scroll = Ui.scroll(context, content)
        val potOffers = PotStyle.entries.filter { it.price > 0 }.map { pot ->
            Offer(pot.id, pot.title, pot.icon, pot.price) { store.buyPot(pot) }
        }
        val backgroundOffers = GardenBackground.entries.filter { it.price > 0 }.map { background ->
            Offer(background.id, background.title, background.image, background.price) {
                if (!store.buyBackground(background)) false
                else UserGarden.useBackground(context, background)
            }
        }
        fun selectTab(tab: Button, selected: Boolean) {
            tab.isSelected = selected
            tab.setBackground(Ui.shape(if (selected) Ui.GREEN else Ui.PALE, Ui.dp(context, 13f)))
            tab.setTextColor(if (selected) Ui.WHITE else Ui.GREEN)
            ViewCompat.setStateDescription(tab, getString(
                if (selected) R.string.challenge_selected else R.string.challenge_not_selected))
        }
        fun render() {
            val available = store.availablePoints()
            balance.text = getString(R.string.plant_shop_balance, available)
            selectTab(pots, !showBackgrounds)
            selectTab(backgrounds, showBackgrounds)
            val offers = if (showBackgrounds) backgroundOffers else potOffers
            val owned = if (showBackgrounds) store.ownedBackgrounds().map { it.id }
                else store.ownedPots().map { it.id }
            content.removeAllViews()
            content.addView(Ui.text(context, getString(
                if (showBackgrounds) R.string.plant_shop_background_intro else R.string.plant_shop_intro),
                15, Ui.INK, false))
            Ui.gap(content, 14)
            offers.forEach { offer ->
                val card = Ui.card(context)
                val header = Ui.row(context)
                header.addView(ImageView(context).apply {
                    setImageResource(offer.image)
                    scaleType = if (showBackgrounds) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(Ui.dp(context, if (showBackgrounds) 58f else 42f),
                    Ui.dp(context, if (showBackgrounds) 78f else 42f)).apply { marginEnd = Ui.dp(context, 12f) })
                val title = getString(offer.title)
                header.addView(Ui.text(context, title, 18, Ui.INK, true))
                card.addView(header)
                val isOwned = offer.id in owned
                val label = if (isOwned) getString(R.string.plant_shop_owned)
                    else getString(R.string.plant_shop_buy, offer.price)
                val action = Ui.button(context, label, true) {
                    if (offer.buy()) {
                        val message = if (showBackgrounds) R.string.plant_shop_background_applied
                            else R.string.plant_shop_purchased
                        Toast.makeText(context, getString(message, title), Toast.LENGTH_SHORT).show()
                    }
                    render()
                }
                action.contentDescription = getString(R.string.plant_shop_item_action, title, label)
                action.isEnabled = !isOwned && available >= offer.price
                action.alpha = if (action.isEnabled) 1f else 0.6f
                card.addView(action)
                content.addView(card)
            }
        }
        pots.setOnClickListener {
            showBackgrounds = false
            render()
            scroll.scrollTo(0, 0)
        }
        backgrounds.setOnClickListener {
            showBackgrounds = true
            render()
            scroll.scrollTo(0, 0)
        }
        render()
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return page
    }

    private data class Offer(val id: String, val title: Int, val image: Int, val price: Int, val buy: () -> Boolean)
}
