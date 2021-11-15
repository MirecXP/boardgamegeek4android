package com.boardgamegeek.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.boardgamegeek.db.PlayDao
import com.boardgamegeek.entities.*
import com.boardgamegeek.extensions.*
import com.boardgamegeek.livedata.LiveSharedPreference
import com.boardgamegeek.pref.SyncPrefs
import com.boardgamegeek.repository.PlayRepository
import com.boardgamegeek.service.SyncService
import com.boardgamegeek.util.RateLimiter
import java.util.concurrent.TimeUnit

class PlaysSummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val playRepository = PlayRepository(getApplication())
    private val playsRateLimiter = RateLimiter<Int>(10, TimeUnit.MINUTES)
    private val syncTimestamp = MutableLiveData<Long>()
    private val h = LiveSharedPreference<Int>(getApplication(), PlayStats.KEY_GAME_H_INDEX)
    private val n = LiveSharedPreference<Int>(getApplication(), PlayStats.KEY_GAME_H_INDEX + PlayStats.KEY_H_INDEX_N_SUFFIX)
    private val username = LiveSharedPreference<String>(getApplication(), AccountPreferences.KEY_USERNAME)

    init {
        refresh()
    }

    val syncPlays = LiveSharedPreference<Boolean>(getApplication(), PREFERENCES_KEY_SYNC_PLAYS)
    val syncPlaysTimestamp = LiveSharedPreference<Long>(getApplication(), PREFERENCES_KEY_SYNC_PLAYS_TIMESTAMP)
    val oldestSyncDate = LiveSharedPreference<Long>(getApplication(), SyncPrefs.TIMESTAMP_PLAYS_OLDEST_DATE, SyncPrefs.NAME)
    val newestSyncDate = LiveSharedPreference<Long>(getApplication(), SyncPrefs.TIMESTAMP_PLAYS_NEWEST_DATE, SyncPrefs.NAME)

    val plays = syncTimestamp.switchMap {
        liveData {
            try {
                val list = playRepository.getPlays()
                SyncService.sync(getApplication(), SyncService.FLAG_SYNC_PLAYS_UPLOAD)
                val refreshedList = if (syncPlays.value == true && playsRateLimiter.shouldProcess(0)) {
                    emit(RefreshableResource.refreshing(list))
                    playRepository.refreshPlays()
                    playRepository.getPlays()

                } else list
                emit(RefreshableResource.success(refreshedList))
            } catch (e: Exception) {
                playsRateLimiter.reset(0)
                emit(RefreshableResource.error<List<PlayEntity>>(e, getApplication()))
            }
        }
    }

    val playCount: LiveData<Int> = plays.map { list ->
        list.data?.sumOf { it.quantity } ?: 0
    }

    val playsInProgress: LiveData<List<PlayEntity>> = plays.map { list ->
        list.data?.filter { it.dirtyTimestamp > 0L }.orEmpty()
    }

    val playsNotInProgress: LiveData<List<PlayEntity>> = plays.map { list ->
        list.data?.filter { it.dirtyTimestamp == 0L }?.take(ITEMS_TO_DISPLAY).orEmpty()
    }

    val players: LiveData<List<PlayerEntity>> = plays.switchMap {
        liveData {
            emit(playRepository.loadPlayers(PlayDao.PlayerSortBy.PLAY_COUNT))
        }
    }.map { p ->
        p.filter { it.username != username.value }.take(ITEMS_TO_DISPLAY)
    }

    val locations: LiveData<List<LocationEntity>> = plays.switchMap {
        liveData {
            emit(playRepository.loadLocations(PlayDao.LocationSortBy.PLAY_COUNT))
        }
    }.map { p ->
        p.filter { it.name.isNotBlank() }.take(ITEMS_TO_DISPLAY)
    }

    val colors: LiveData<List<PlayerColorEntity>> = liveData {
        emit(
            if (username.value.isNullOrBlank()) emptyList()
            else playRepository.loadUserColors(username.value.orEmpty())
        )
    }

    val hIndex = MediatorLiveData<HIndexEntity>().apply {
        addSource(h) {
            value = HIndexEntity(it ?: HIndexEntity.INVALID_H_INDEX, n.value ?: 0)
        }
        addSource(n) {
            value = HIndexEntity(h.value ?: HIndexEntity.INVALID_H_INDEX, it ?: 0)
        }
    }

    fun refresh(): Boolean {
        val value = syncTimestamp.value
        return if (value == null || value.isOlderThan(1, TimeUnit.SECONDS)) {
            syncTimestamp.postValue(System.currentTimeMillis())
            true
        } else false
    }

    companion object {
        const val ITEMS_TO_DISPLAY = 5
    }
}
