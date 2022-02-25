package com.boardgamegeek.filterer

import android.content.Context
import com.boardgamegeek.R
import com.boardgamegeek.entities.CollectionItemEntity
import com.boardgamegeek.provider.BggContract.Games

class GeekRatingFilterer(context: Context) : RatingFilterer(context) {
    override val typeResourceId = R.string.collection_filter_type_geek_rating

    override val columnName = Games.Columns.STATS_BAYES_AVERAGE

    override val iconResourceId: Int
        get() = R.drawable.ic_rating // TODO use multiple stars

    override fun chipText() = describe(R.string.rating, R.string.unrated_abbr)

    override fun description() = describe(R.string.geek_rating, R.string.unrated)

    override fun filter(item: CollectionItemEntity) = filter(item.geekRating)
}
