package com.boardgamegeek.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.boardgamegeek.io.BggService
import com.boardgamegeek.io.model.GeekListsResponse
import com.boardgamegeek.livedata.GeekListsPagingSource
import com.boardgamegeek.repository.GeekListRepository
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent

class GeekListsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GeekListRepository()
    private val _sort = MutableLiveData<String>()

    fun setSort(sort: SortType) {
        val sortString = when (sort) {
            SortType.HOT -> BggService.GEEK_LIST_SORT_HOT
            SortType.RECENT -> BggService.GEEK_LIST_SORT_RECENT
            SortType.ACTIVE -> BggService.GEEK_LIST_SORT_ACTIVE
        }
        if (_sort.value != sortString) {
            _sort.value = sortString
            FirebaseAnalytics.getInstance(getApplication()).logEvent("Sort") {
                param(FirebaseAnalytics.Param.CONTENT_TYPE, "GeekLists")
                param("SortBy", sort.toString())
            }
        }
    }

    val geekLists = _sort.switchMap { sort ->
        Pager(
            PagingConfig(
                pageSize = GeekListsResponse.PAGE_SIZE,
                initialLoadSize = GeekListsResponse.PAGE_SIZE,
                prefetchDistance = 30,
                enablePlaceholders = true,
            )
        ) {
            GeekListsPagingSource(sort, repository)
        }.flow.asLiveData()
    }

    enum class SortType {
        HOT, RECENT, ACTIVE
    }
}
