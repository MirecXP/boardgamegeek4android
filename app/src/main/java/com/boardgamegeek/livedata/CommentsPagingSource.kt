package com.boardgamegeek.livedata

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.boardgamegeek.entities.GameCommentEntity
import com.boardgamegeek.io.model.Game
import com.boardgamegeek.provider.BggContract
import com.boardgamegeek.repository.GameRepository
import retrofit2.HttpException
import timber.log.Timber

class CommentsPagingSource(val gameId: Int, private val sortByRating: Boolean = false, val repository: GameRepository) :
    PagingSource<Int, GameCommentEntity>() {
    override fun getRefreshKey(state: PagingState<Int, GameCommentEntity>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GameCommentEntity> {
        return try {
            if (gameId == BggContract.INVALID_ID) return LoadResult.Error(Exception("Invalid ID"))

            val page = params.key ?: 1
            val entity = if (sortByRating) repository.loadRatings(gameId, page) else repository.loadComments(gameId, page)
            val nextPage = if (page * Game.PAGE_SIZE < (entity?.numberOfRatings ?: 0)) page + 1 else null
            LoadResult.Page(entity?.ratings.orEmpty(), null, nextPage)
        } catch (e: Exception) {
            if (e is HttpException) {
                Timber.w("Error code: ${e.code()}\n${e.response()?.body()}")
            } else {
                Timber.w(e)
            }
            LoadResult.Error(e)
        }
    }
}
