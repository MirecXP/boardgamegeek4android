package com.boardgamegeek.repository

import androidx.core.content.contentValuesOf
import com.boardgamegeek.BggApplication
import com.boardgamegeek.auth.AccountUtils
import com.boardgamegeek.auth.Authenticator
import com.boardgamegeek.db.UserDao
import com.boardgamegeek.entities.CollectionItemEntity
import com.boardgamegeek.entities.UserEntity
import com.boardgamegeek.io.Adapter
import com.boardgamegeek.io.BggService
import com.boardgamegeek.mappers.mapToEntities
import com.boardgamegeek.mappers.mapToEntity
import com.boardgamegeek.provider.BggContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class UserRepository(val application: BggApplication) {
    private val userDao = UserDao(application)

    suspend fun load(username: String): UserEntity? = withContext(Dispatchers.IO) {
        userDao.loadUser(username)
    }

    suspend fun refresh(username: String): UserEntity = withContext(Dispatchers.IO) {
        val response = Adapter.createForXml().userC(username)
        val user = response.mapToEntity()
        userDao.saveUser(user)
    }

    suspend fun refreshCollection(username: String, status: String): List<CollectionItemEntity> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<CollectionItemEntity>()
            val response = Adapter.createForXml().collectionC(
                username, mapOf(
                    status to "1",
                    BggService.COLLECTION_QUERY_KEY_BRIEF to "1"
                )
            )
            response.items?.forEach {
                items += it.mapToEntities().first
            }
            items
        }

    suspend fun loadBuddies(sortBy: UserDao.UsersSortBy = UserDao.UsersSortBy.USERNAME): List<UserEntity> =
        withContext(Dispatchers.IO) {
            userDao.loadBuddies(sortBy)
        }

    suspend fun refreshBuddies(timestamp: Long) = withContext(Dispatchers.IO) {
        val accountName = Authenticator.getAccount(application)?.name
        if (accountName.isNullOrBlank()) return@withContext

        val response = Adapter.createForXml().userC(accountName, 1, 1)
        response.buddies?.buddies.orEmpty()
            .map { it.mapToEntity(timestamp) }
            .filter { it.id != BggContract.INVALID_ID && it.userName.isNotBlank() }
            .forEach {
                userDao.saveBuddy(it)
            }

        val deletedCount = userDao.deleteUsersAsOf(timestamp)
        Timber.d("Deleted $deletedCount users")
    }

    suspend fun updateNickName(username: String, nickName: String) = withContext(Dispatchers.IO) {
        // TODO remove withContext() when DAO uses it
        if (username.isNotBlank()) {
            userDao.upsert(contentValuesOf(BggContract.Buddies.PLAY_NICKNAME to nickName), username)
        }
    }

    suspend fun updateSelf(user: UserEntity) = withContext(Dispatchers.IO) {
        Authenticator.putUserId(application, user.id)
        AccountUtils.setUsername(application, user.userName)
        AccountUtils.setFullName(application, user.fullName)
        AccountUtils.setAvatarUrl(application, user.avatarUrl)
    }
}
