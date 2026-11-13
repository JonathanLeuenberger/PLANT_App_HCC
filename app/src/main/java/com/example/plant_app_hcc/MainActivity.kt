package com.example.plant_app_hcc

import android.os.Bundle
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.NavController
import androidx.navigation.navOptions
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import com.example.plant_app_hcc.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChallengeStore.syncProgress(this)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.contentMain.tabMyGarden.setOnClickListener {
            if (navController.currentDestination?.id == R.id.FirstFragment) {
                findViewById<RecyclerView>(R.id.recycler_plants)?.smoothScrollToPosition(0)
            } else {
                navController.popBackStack(R.id.FirstFragment, false)
            }
        }
        binding.contentMain.tabCalendar.setOnClickListener {
            openHub(navController, R.id.CalendarFragment)
        }
        binding.contentMain.tabToDo.setOnClickListener {
            openHub(navController, R.id.ToDoFragment)
        }
        binding.contentMain.tabChallenges.setOnClickListener {
            openHub(navController, R.id.ChallengesFragment)
        }

        navController.addOnDestinationChangedListener { _, destination, args ->
            binding.fab.visibility = android.view.View.GONE
            // The same bar stays fixed under each hub, including challenge details.
            binding.contentMain.bottomBarContainer.visibility = if (destination.id in setOf(
                R.id.FirstFragment, R.id.ChallengesFragment, R.id.CalendarFragment, R.id.ToDoFragment, R.id.PlaceholderFragment
            )) View.VISIBLE else View.GONE
            val selectedTab = when (destination.id) {
                R.id.FirstFragment -> R.id.tab_my_garden
                R.id.ChallengesFragment -> R.id.tab_challenges
                R.id.CalendarFragment -> R.id.tab_calendar
                R.id.ToDoFragment -> R.id.tab_to_do
                else -> if (args?.getString("title") == "Calendar") R.id.tab_calendar else R.id.tab_to_do
            }
            updateHubSelection(selectedTab)
            val inGarden = destination.id == R.id.FirstFragment
            showGardenScene(if (inGarden) UserGarden.background(this) else GardenBackground.DEFAULT)
            if (inGarden) {
                supportActionBar?.hide()
            } else {
                supportActionBar?.show()
            }
        }

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }
    }

    internal fun showGardenScene(background: GardenBackground) {
        binding.contentMain.gardenScene.apply {
            setImageResource(background.image)
            visibility = if (background == GardenBackground.DEFAULT) View.GONE else View.VISIBLE
        }
    }

    private fun openHub(nav: NavController, destination: Int, title: String? = null) {
        val current = nav.currentBackStackEntry
        if (current?.destination?.id == destination &&
            current.arguments?.getString("title") == title &&
            current.arguments?.getString("challengeId").isNullOrEmpty()) return
        val args = title?.let { Bundle().apply { putString("title", it) } }
        // Switching tabs returns to the hub root instead of stacking duplicate screens.
        nav.navigate(destination, args, navOptions {
            popUpTo(R.id.FirstFragment) { inclusive = false }
            launchSingleTop = true
        })
    }

    private fun updateHubSelection(selectedId: Int) {
        val bar = binding.contentMain.bottomBarContainer
        for (i in 0 until bar.childCount) {
            val tab = bar.getChildAt(i) as ViewGroup
            val selected = tab.id == selectedId
            tab.isSelected = selected
            tab.minimumHeight = (48 * resources.displayMetrics.density).toInt()
            ViewCompat.setStateDescription(tab, getString(
                if (selected) R.string.challenge_selected else R.string.challenge_not_selected))
            tab.background = if (selected) GradientDrawable().apply {
                cornerRadius = 24 * resources.displayMetrics.density
                setColor(ContextCompat.getColor(this@MainActivity, R.color.challenge_green))
            } else null
            val ink = ContextCompat.getColor(this,
                if (selected) R.color.white else R.color.challenge_ink)
            for (j in 0 until tab.childCount) {
                when (val child = tab.getChildAt(j)) {
                    is ImageView -> child.imageTintList = ColorStateList.valueOf(ink)
                    is TextView -> child.setTextColor(ink)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
