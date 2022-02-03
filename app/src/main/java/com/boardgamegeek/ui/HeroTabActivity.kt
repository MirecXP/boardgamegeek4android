package com.boardgamegeek.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.boardgamegeek.R
import com.boardgamegeek.extensions.ImageLoadCallback
import com.boardgamegeek.extensions.applyDarkScrim
import com.boardgamegeek.extensions.loadUrl
import com.boardgamegeek.extensions.safelyLoadImage
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.activity_hero_tab.*
import kotlinx.coroutines.launch

/**
 * A navigation drawer activity that displays a hero image over a view pager.
 */
abstract class HeroTabActivity : DrawerActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    protected fun initializeViewPager() {
        viewPager.adapter = createAdapter()
        createOnPageChangeListener()?.let { viewPager.registerOnPageChangeCallback(it) }
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = getPageTitle(position)
        }.attach()
    }

    protected abstract fun getPageTitle(position: Int): CharSequence

    protected abstract fun createAdapter(): FragmentStateAdapter

    protected open fun createOnPageChangeListener(): ViewPager2.OnPageChangeCallback? {
        return null
    }

    override val layoutResId = R.layout.activity_hero_tab

    protected fun safelySetTitle(title: String?) {
        if (!title.isNullOrBlank()) {
            collapsingToolbar.title = title
        }
    }

    protected fun loadToolbarImage(url: String) {
        toolbarImage.loadUrl(url, object : ImageLoadCallback {
            override fun onSuccessfulImageLoad(palette: Palette?) {
                onPaletteLoaded(palette)
                scrimView.applyDarkScrim()
            }

            override fun onFailedImageLoad() {}
        })
    }

    protected fun loadToolbarImage(imageId: Int) {
        lifecycleScope.launch {
            toolbarImage.safelyLoadImage(imageId, object : ImageLoadCallback {
                override fun onSuccessfulImageLoad(palette: Palette?) {
                    onPaletteLoaded(palette)
                    scrimView.applyDarkScrim()
                }

                override fun onFailedImageLoad() {}
            })
        }
    }

    protected open fun onPaletteLoaded(palette: Palette?) {
    }

    protected fun getCoordinatorLayout() = coordinatorLayout!!
}
