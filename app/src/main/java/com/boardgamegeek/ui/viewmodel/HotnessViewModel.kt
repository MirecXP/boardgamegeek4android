package com.boardgamegeek.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.boardgamegeek.entities.HotGameEntity
import com.boardgamegeek.entities.RefreshableResource
import com.boardgamegeek.repository.HotnessRepository
import com.boardgamegeek.repository.PlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HotnessViewModel @Inject constructor(
    application: Application,
    private val hotnessRepository: HotnessRepository,
    private val playRepository: PlayRepository,
) : AndroidViewModel(application) {

    val hotness: LiveData<RefreshableResource<List<HotGameEntity>>> = liveData {
        try {
            emit(RefreshableResource.refreshing(latestValue?.data))
            val games = hotnessRepository.getHotness()
            emit(RefreshableResource.success(games))
        } catch (e: Exception) {
            emit(RefreshableResource.error(e, application))
        }
    }

    fun logQuickPlay(gameId: Int, gameName: String) {
        viewModelScope.launch {
            playRepository.logQuickPlay(gameId, gameName)
        }
    }
}
