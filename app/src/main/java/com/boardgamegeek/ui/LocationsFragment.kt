package com.boardgamegeek.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.boardgamegeek.R
import com.boardgamegeek.entities.LocationEntity
import com.boardgamegeek.extensions.fadeIn
import com.boardgamegeek.extensions.fadeOut
import com.boardgamegeek.extensions.inflate
import com.boardgamegeek.ui.adapter.AutoUpdatableAdapter
import com.boardgamegeek.ui.viewmodel.LocationsViewModel
import com.boardgamegeek.ui.widget.RecyclerSectionItemDecoration
import com.boardgamegeek.ui.widget.RecyclerSectionItemDecoration.SectionCallback
import kotlinx.android.synthetic.main.fragment_locations.*
import kotlinx.android.synthetic.main.row_location.view.*
import kotlin.properties.Delegates

class LocationsFragment : Fragment(R.layout.fragment_locations) {
    private val viewModel by activityViewModels<LocationsViewModel>()

    private val adapter: LocationsAdapter by lazy {
        LocationsAdapter(viewModel)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(
            RecyclerSectionItemDecoration(
                resources.getDimensionPixelSize(R.dimen.recycler_section_header_height),
                adapter
            )
        )

        swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        viewModel.locations.observe(viewLifecycleOwner) {
            adapter.locations = it
            if (adapter.itemCount == 0) {
                recyclerView.fadeOut()
                emptyContainer.fadeIn()
            } else {
                emptyContainer.fadeOut()
                recyclerView.fadeIn(recyclerView.windowToken != null)
            }
            progressBar?.hide()
            swipeRefresh.isRefreshing = false
        }
    }

    private class LocationsAdapter(val viewModel: LocationsViewModel) :
        RecyclerView.Adapter<LocationsAdapter.LocationsViewHolder>(), AutoUpdatableAdapter, SectionCallback {
        var locations: List<LocationEntity> by Delegates.observable(emptyList()) { _, oldValue, newValue ->
            autoNotify(oldValue, newValue) { old, new ->
                old.name == new.name
            }
        }

        override fun getItemCount() = locations.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationsViewHolder {
            return LocationsViewHolder(parent.inflate(R.layout.row_location))
        }

        override fun onBindViewHolder(holder: LocationsViewHolder, position: Int) {
            holder.bind(locations.getOrNull(position))
        }

        override fun isSection(position: Int): Boolean {
            if (position == RecyclerView.NO_POSITION) return false
            if (locations.isEmpty()) return false
            if (position == 0) return true
            val thisLetter = viewModel.getSectionHeader(locations.getOrNull(position))
            val lastLetter = viewModel.getSectionHeader(locations.getOrNull(position - 1))
            return thisLetter != lastLetter
        }

        override fun getSectionHeader(position: Int): CharSequence {
            return when {
                position == RecyclerView.NO_POSITION -> "-"
                locations.isEmpty() -> "-"
                else -> viewModel.getSectionHeader(locations.getOrNull(position))
            }
        }

        inner class LocationsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(location: LocationEntity?) {
                location?.let { l ->
                    if (l.name.isBlank()) {
                        itemView.nameView.setText(R.string.no_location)
                    } else {
                        itemView.nameView.text = l.name
                    }
                    itemView.quantityView.text =
                        itemView.resources.getQuantityString(R.plurals.plays_suffix, l.playCount, l.playCount)
                    itemView.setOnClickListener {
                        LocationActivity.start(itemView.context, l.name)
                    }
                }
            }
        }
    }
}
