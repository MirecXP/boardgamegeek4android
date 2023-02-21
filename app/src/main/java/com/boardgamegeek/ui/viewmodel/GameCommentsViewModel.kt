package com.boardgamegeek.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.liveData
import com.boardgamegeek.io.model.Game
import com.boardgamegeek.livedata.CommentsPagingSource
import com.boardgamegeek.provider.BggContract
import com.boardgamegeek.repository.GameRepository
import com.boardgamegeek.repository.PlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GameCommentsViewModel @Inject constructor(
    application: Application,
    playRepository: PlayRepository,
) : AndroidViewModel(application) {
    enum class SortType {
        RATING, USER
    }

    private val repository = GameRepository(getApplication(), playRepository)
    private val _id = MutableLiveData<Pair<Int, SortType>>()

    val sort: LiveData<SortType> = Transformations.map(_id) {
        _id.value?.second
    }

    fun setGameId(id: Int) {
        if (_id.value?.first != id) _id.value = id to (_id.value?.second ?: SortType.RATING)
    }

    fun setSort(sort: SortType) {
        if (_id.value?.second != sort) _id.value = (_id.value?.first ?: BggContract.INVALID_ID) to sort
    }

    val comments = _id.switchMap {
        val sortByRating = it.second == SortType.RATING
        Pager(
            PagingConfig(
                pageSize = Game.PAGE_SIZE,
                initialLoadSize = Game.PAGE_SIZE,
                prefetchDistance = 30,
                enablePlaceholders = true,
            )
        ) {
            CommentsPagingSource(it.first, sortByRating, repository)
        }.liveData
    }
}
