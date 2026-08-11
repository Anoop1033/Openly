package com.openly.app.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openly.app.data.model.Interest
import com.openly.app.data.repository.InterestRepository
import com.openly.app.data.repository.ProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An outgoing interest paired with the recipient's profile, since Interest only stores the sender's info. */
data class OutgoingRequest(val interest: Interest, val toName: String, val toEmoji: String)

data class RequestsUiState(
    val incoming: List<Interest> = emptyList(),
    val outgoing: List<OutgoingRequest> = emptyList()
)

class RequestsViewModel(
    selfUid: String,
    private val interestRepository: InterestRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    val uiState: StateFlow<RequestsUiState> = combine(
        interestRepository.observeIncoming(selfUid),
        interestRepository.observeOutgoingPending(selfUid)
    ) { incoming, outgoing ->
        val enrichedOutgoing = coroutineScope {
            outgoing.map { interest ->
                async {
                    val profile = profileRepository.getProfile(interest.toUid)
                    OutgoingRequest(
                        interest = interest,
                        toName = profile?.displayName ?: "Someone",
                        toEmoji = profile?.avatarEmoji ?: "🙂"
                    )
                }
            }.awaitAll()
        }
        RequestsUiState(incoming, enrichedOutgoing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RequestsUiState())

    fun accept(interest: Interest) {
        viewModelScope.launch { interestRepository.accept(interest) }
    }

    fun decline(interest: Interest) {
        viewModelScope.launch { interestRepository.decline(interest) }
    }
}

class RequestsViewModelFactory(
    private val selfUid: String,
    private val interestRepository: InterestRepository,
    private val profileRepository: ProfileRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RequestsViewModel(selfUid, interestRepository, profileRepository) as T
    }
}
