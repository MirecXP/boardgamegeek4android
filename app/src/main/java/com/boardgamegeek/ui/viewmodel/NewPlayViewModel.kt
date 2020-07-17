package com.boardgamegeek.ui.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.*
import com.boardgamegeek.auth.AccountUtils
import com.boardgamegeek.db.PlayDao
import com.boardgamegeek.entities.*
import com.boardgamegeek.extensions.*
import com.boardgamegeek.provider.BggContract
import com.boardgamegeek.repository.GameRepository
import com.boardgamegeek.repository.PlayRepository
import org.jetbrains.anko.collections.forEachWithIndex
import java.util.*
import java.util.concurrent.TimeUnit

class NewPlayViewModel(application: Application) : AndroidViewModel(application) {
    private var gameName: String = ""
    private var playDate: Long = Calendar.getInstance().timeInMillis
    private var comments: String = ""

    private val playRepository = PlayRepository(getApplication())
    private val gameRepository = GameRepository(getApplication())
    private val prefs: SharedPreferences by lazy { application.preferences() }

    private var gameId = MutableLiveData<Int>()

    private val _currentStep = MutableLiveData<Step>()
    val currentStep: LiveData<Step>
        get() = _currentStep

    private val _startTime = MutableLiveData<Long>()
    val startTime: LiveData<Long>
        get() = _startTime

    private val _lengthInMillis = MutableLiveData<Long>()
    val length: LiveData<Int> = Transformations.map(_lengthInMillis) {
        (it / 60_000).toInt()
    }

    private val _location = MutableLiveData<String>()
    val location: LiveData<String>
        get() = _location

    // Locations
    val locations = MediatorLiveData<List<LocationEntity>>()
    private var locationFilter = ""
    private val rawLocations = playRepository.loadLocations(PlayDao.LocationSortBy.PLAY_COUNT)

    // Players
    val availablePlayers = MediatorLiveData<List<PlayerEntity>>()
    private val allPlayers = playRepository.loadPlayersByLocation()
    private val playersByLocation: LiveData<List<PlayerEntity>> = Transformations.switchMap(location) {
        playRepository.loadPlayersByLocation(it)
    }
    private var playerFilter = ""
    private val _addedPlayers = MutableLiveData<MutableList<PlayerEntity>>()
    val addedPlayers = MediatorLiveData<List<NewPlayPlayerEntity>>()
    private val playerColorMap = MutableLiveData<MutableMap<String, String>>()
    private val playerFavoriteColorMap = mutableMapOf<String, List<PlayerColorEntity>>()
    val selectedColors = MediatorLiveData<List<String>>()
    private val playerSortMap = MutableLiveData<MutableMap<String, String>>()

    val gameColors: LiveData<List<String>> = Transformations.switchMap(gameId) {
        gameRepository.getPlayColors(it)
    }

    init {
        _currentStep.value = Step.LOCATION
        playDate = Calendar.getInstance().timeInMillis

        locations.addSource(rawLocations) { result ->
            result?.let { locations.value = filterLocations(result, locationFilter) }
        }

        availablePlayers.addSource(allPlayers) { result ->
            result?.let { availablePlayers.value = filterPlayers(result, playersByLocation.value, _addedPlayers.value, playerFilter) }
        }
        availablePlayers.addSource(playersByLocation) { result ->
            result?.let { availablePlayers.value = filterPlayers(allPlayers.value, result, _addedPlayers.value, playerFilter) }
        }
        availablePlayers.addSource(_addedPlayers) { result ->
            result?.let { availablePlayers.value = filterPlayers(allPlayers.value, playersByLocation.value, result, playerFilter) }
        }

        addedPlayers.addSource(_addedPlayers) { list ->
            list?.let {
                assemblePlayers(addedPlayers = it)
            }
        }
        addedPlayers.addSource(playerColorMap) { map ->
            map?.let {
                assemblePlayers(playerColors = it)
            }
        }
        addedPlayers.addSource(playerSortMap) { map ->
            map?.let {
                assemblePlayers(playerSort = it)
            }
        }
        addedPlayers.addSource(gameColors) { list ->
            list?.let {
                assemblePlayers(gameColorList = it)
            }
        }

        selectedColors.addSource(addedPlayers) { result ->
            selectedColors.value = result.map { it.color }
        }
    }

    val insertedId = MutableLiveData<Long>()

    fun setGame(id: Int, name: String) {
        gameId.value = id
        gameName = name
    }

    fun filterLocations(filter: String) = rawLocations.value?.let { result ->
        locations.value = filterLocations(result, filter)
    }.also { locationFilter = filter }

    private fun filterLocations(list: List<LocationEntity>?, filter: String): List<LocationEntity> {
        val newList = (list?.filter { it.name.isNotBlank() } ?: emptyList()).toMutableList()
        if (isLastPlayRecent()) {
            newList.find { it.name == prefs.getLastPlayLocation() }?.let {
                newList.remove(it)
                newList.add(0, it)
            }
        }
        return newList.filter { it.name.startsWith(filter, true) }
    }

    fun setLocation(name: String) {
        if (_location.value != name) _location.value = name
        _currentStep.value = Step.PLAYERS
    }

    fun addPlayer(player: PlayerEntity) {
        val newList = _addedPlayers.value ?: mutableListOf()
        if (!newList.contains(player)) {
            newList.add(player)
            val colors = if (player.username.isNotBlank()) {
                playRepository.loadUserColorsAsLiveData(player.username)
            } else {
                playRepository.loadPlayerColorsAsLiveData(player.name)
            }
            addedPlayers.addSource(colors) { result ->
                result?.let {
                    // TODO make this LiveData
                    playerFavoriteColorMap[player.description] = it
                    assemblePlayers()
                }
            }
        }
        _addedPlayers.value = newList
    }

    fun removePlayer(player: NewPlayPlayerEntity) {
        val p = PlayerEntity(player.name, player.username)

        val newList = _addedPlayers.value ?: mutableListOf()
        newList.remove(p)
        _addedPlayers.value = newList

        val newColorMap = playerColorMap.value ?: mutableMapOf()
        newColorMap.remove(p.id)
        playerColorMap.value = newColorMap

        val newSortMap = playerSortMap.value ?: mutableMapOf()
        newSortMap.remove(p.id)
        playerSortMap.value = newSortMap
    }

    fun finishAddingPlayers() {
        _currentStep.value = Step.PLAYERS_COLOR
    }

    fun addColorToPlayer(playerIndex: Int, color: String) {
        val colorMap = playerColorMap.value ?: mutableMapOf()
        val player = _addedPlayers.value?.getOrNull(playerIndex)
        if (player != null) {
            colorMap[player.id] = color
            playerColorMap.value = colorMap
        }
    }

    fun finishPlayerColors() {
        _currentStep.value = Step.PLAYERS_SORT
    }

    fun clearSortOrder() {
        playerSortMap.value = mutableMapOf()
    }

    fun randomizePlayers() {
        val sortMap = mutableMapOf<String, String>()
        val playerCount = _addedPlayers.value?.size ?: 0
        val collection = (1..playerCount).toMutableSet()
        _addedPlayers.value?.forEach { playerEntity ->
            val sortOrder = collection.random()
            sortMap[playerEntity.id] = sortOrder.toString()
            collection.remove(sortOrder)
        }
        playerSortMap.value = sortMap
    }

    fun selectStartPlayer(index: Int) {
        val playerCount = _addedPlayers.value?.size ?: 0
        val sortMap = playerSortMap.value ?: mutableMapOf()
        if (sortMap.isNotEmpty() && sortMap.values.all { it.toIntOrNull() != null }) {
            sortMap.forEach {
                val number = (it.value.toInt() + playerCount - index - 1) % playerCount + 1
                sortMap[it.key] = number.toString()
            }
        } else {
            sortMap.clear()
            _addedPlayers.value?.forEachWithIndex { i, playerEntity ->
                val number = (i + playerCount - index) % playerCount + 1
                sortMap[playerEntity.id] = number.toString()
            }
        }
        playerSortMap.value = sortMap
    }

    fun movePlayer(fromPosition: Int, toPosition: Int): Boolean {
        val sortMap = playerSortMap.value ?: mutableMapOf()
        if (sortMap.isNotEmpty() && sortMap.values.all { it.toIntOrNull() != null }) {
            val oldMap = sortMap.mapValues { it.value.toInt() }.toMutableMap()
            val newMap = mutableMapOf<String, String>()
            if (fromPosition < toPosition) {
                // dragging down
                for (seat in oldMap) {
                    if (seat.value > fromPosition + 1 && seat.value <= toPosition + 1) {
                        newMap[seat.key] = (seat.value - 1).toString()
                    } else {
                        newMap[seat.key] = seat.value.toString()
                    }
                }
            } else {
                // dragging up
                for (seat in oldMap) {
                    if (seat.value >= toPosition + 1 && seat.value < fromPosition + 1) {
                        newMap[seat.key] = (seat.value + 1).toString()
                    } else {
                        newMap[seat.key] = seat.value.toString()
                    }
                }
            }
            for (seat in oldMap) {
                if (seat.value == fromPosition + 1) {
                    newMap[seat.key] = (toPosition + 1).toString()
                    break
                }
            }
            playerSortMap.value = newMap
            return true
        }
        return false
    }

    fun finishPlayerSort() {
        _currentStep.value = Step.COMMENTS
    }

    fun filterPlayers(filter: String) = availablePlayers.value?.let {
        availablePlayers.value = filterPlayers(allPlayers.value, playersByLocation.value, _addedPlayers.value, filter)
    }.also { playerFilter = filter }

    private fun filterPlayers(allPlayers: List<PlayerEntity>?, locationPlayers: List<PlayerEntity>?, addedPlayers: List<PlayerEntity>?, filter: String): List<PlayerEntity> {
        val self = allPlayers?.find { it.username == AccountUtils.getUsername(getApplication()) }
        val newList = mutableListOf<PlayerEntity>()
        // show players in this order:
        // 1. me
        self?.let {
            newList.add(it)
        }
        //  2. last played at this location
        if (isLastPlayRecent() && location.value == prefs.getLastPlayLocation()) {
            val lastPlayers = prefs.getLastPlayPlayerEntities()
            lastPlayers?.forEach { lastPlayer ->
                allPlayers?.find { it == lastPlayer && !newList.contains(it) }?.let {
                    newList.add(it)
                }
            }
        }
        // 3. previously played at this location
        locationPlayers?.let { list ->
            newList.addAll(list.filter { !newList.contains(it) }.asIterable())
        }
        // 4. all other players
        allPlayers?.let { list ->
            newList += list.filter {
                (self == null || it.username != self.username) && !(newList.contains(it))
            }.asIterable()
        }
        // then filter out added players and those not matching the current filter
        return newList.filter {
            !(addedPlayers ?: emptyList()).contains(it) &&
                    (it.name.contains(filter, true) || it.username.contains(filter, true))
        }
    }

    private fun assemblePlayers(
            addedPlayers: List<PlayerEntity> = _addedPlayers.value ?: emptyList(),
            playerColors: Map<String, String> = playerColorMap.value ?: emptyMap(),
            favoriteColorsMap: Map<String, List<PlayerColorEntity>> = playerFavoriteColorMap,
            playerSort: Map<String, String> = playerSortMap.value ?: emptyMap(),
            gameColorList: List<String> = gameColors.value ?: emptyList()) {
        val players = mutableListOf<NewPlayPlayerEntity>()
        addedPlayers.forEach { playerEntity ->
            val newPlayer = NewPlayPlayerEntity(playerEntity).apply {
                color = playerColors[id] ?: ""
                val favoriteForPlayer = favoriteColorsMap[description]?.map { it.description }
                        ?: emptyList()
                val rankedChoices = favoriteForPlayer
                        .filter { gameColorList.contains(it) }
                        .filterNot { playerColors.containsValue(it) }
                        .toMutableList()
                rankedChoices += gameColorList
                        .filterNot { favoriteForPlayer.contains(it) }
                        .filterNot { playerColors.containsValue(it) }
                favoriteColors = rankedChoices
                sortOrder = playerSort[id] ?: ""
            }
            players.add(newPlayer)
        }
        this.addedPlayers.value = players
    }

    private fun isLastPlayRecent(): Boolean {
        val lastPlayTime = prefs.getLastPlayTime()
        return !lastPlayTime.isOlderThan(6, TimeUnit.HOURS)
    }

    fun setComments(input: String) {
        this.comments = input
    }

    fun toggleTimer() {
        val lengthInMillis = _lengthInMillis.value ?: 0L
        if (startTime.value ?: 0L == 0L) {
            _startTime.value = System.currentTimeMillis() - lengthInMillis
            _lengthInMillis.value = 0
        } else {
            _lengthInMillis.value =
                    System.currentTimeMillis() - (startTime.value ?: 0L) + lengthInMillis
            _startTime.value = 0L
        }
    }

    fun save() {
        val startTime = startTime.value ?: 0L
        val play = PlayEntity(
                BggContract.INVALID_ID.toLong(),
                BggContract.INVALID_ID,
                PlayEntity.currentDate(),
                gameId.value ?: BggContract.INVALID_ID,
                gameName,
                quantity = 1,
                length = if (startTime == 0L) length.value ?: 0 else 0,
                location = location.value ?: "",
                incomplete = false,
                noWinStats = false,
                comments = comments,
                syncTimestamp = 0,
                playerCount = _addedPlayers.value?.size ?: 0,
                startTime = startTime,
                updateTimestamp = if (startTime == 0L) System.currentTimeMillis() else 0L,
                dirtyTimestamp = System.currentTimeMillis()
        )

        for (player in _addedPlayers.value ?: mutableListOf()) {
            val p = PlayPlayerEntity(
                    player.name,
                    player.username,
                    color = (playerColorMap.value ?: emptyMap<String, String>())[player.id]
            )
            play.addPlayer(p)
        }

        playRepository.save(play, insertedId)
    }

    enum class Step {
        LOCATION,
        PLAYERS,
        PLAYERS_COLOR,
        PLAYERS_SORT,
        COMMENTS
    }
}
