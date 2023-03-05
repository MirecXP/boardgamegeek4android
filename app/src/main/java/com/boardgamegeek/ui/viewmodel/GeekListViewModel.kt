package com.boardgamegeek.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.boardgamegeek.entities.GeekListEntity
import com.boardgamegeek.entities.RefreshableResource
import com.boardgamegeek.provider.BggContract
import com.boardgamegeek.repository.GeekListRepository
import com.boardgamegeek.repository.ImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GeekListViewModel @Inject constructor(
    application: Application,
    private val repository: GeekListRepository,
    private val imageRepository: ImageRepository,
) : AndroidViewModel(application) {
    private val _geekListId = MutableLiveData<Int>()

    fun setId(geekListId: Int) {
        if (_geekListId.value != geekListId) _geekListId.value = geekListId
    }

    val geekList: LiveData<RefreshableResource<GeekListEntity>> = _geekListId.switchMap { id ->
        liveData {
            if (id == BggContract.INVALID_ID) {
                emit(RefreshableResource.error("Invalid ID!"))
            } else {
                try {
                    emit(RefreshableResource.refreshing(latestValue?.data))
                    val geekList = repository.getGeekList(id)
                    // TODO only fetch URLs if the UI needs to display the image
                    emit(RefreshableResource.refreshing(geekList))
                    geekList.items.forEach { // TODO if imageID == 0, then use the object's image
                        if (it.thumbnailUrls == null)
                            it.thumbnailUrls = imageRepository.getImageUrls(it.imageId, ImageRepository.ImageType.THUMBNAIL)
                        if (it.heroImageUrls == null)
                            it.heroImageUrls = imageRepository.getImageUrls(it.imageId, ImageRepository.ImageType.HERO)
                    }
                    emit(RefreshableResource.success(geekList))
                } catch (e: Exception) {
                    emit(RefreshableResource.error(e, application))
                }
            }
        }
    }
}
