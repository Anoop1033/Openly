package com.openly.app.ui.radar

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openly.app.data.local.DEFAULT_RADAR_RADIUS_METERS
import com.openly.app.data.local.OpenlyPrefs
import com.openly.app.data.location.LocationTracker
import com.openly.app.data.model.NearbyUser
import com.openly.app.data.model.UserProfile
import com.openly.app.data.repository.InterestRepository
import com.openly.app.data.repository.ProfileRepository
import com.openly.app.data.repository.RadarRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RadarUiState(
    val selfProfile: UserProfile? = null,
    val nearbyUsers: List<NearbyUser> = emptyList(),
    val radiusMeters: Double = DEFAULT_RADAR_RADIUS_METERS,
    val hasLocationPermission: Boolean = false,
    val sentInterestTo: Set<String> = emptySet()
)

@OptIn(ExperimentalCoroutinesApi::class)
class RadarViewModel(
    private val selfUid: String,
    private val profileRepository: ProfileRepository,
    private val radarRepository: RadarRepository,
    private val interestRepository: InterestRepository,
    private val locationTracker: LocationTracker,
    private val prefs: OpenlyPrefs
) : ViewModel() {

    private val sentInterestTo = MutableStateFlow<Set<String>>(emptySet())
    private val hasPermission = MutableStateFlow(locationTracker.hasLocationPermission)

    // A fresh Firestore listener per location tick is wasteful at scale, but at cafe-sized
    // presence counts it keeps the radar simple: no separate "did I move far enough" tracking.
    //
    // Keyed off hasPermission (not just called once) because locationUpdates() is a callbackFlow
    // that closes for good the moment it sees no permission — without re-keying, granting access
    // after an initial denial would never restart it.
    private val nearby: StateFlow<List<NearbyUser>> = hasPermission
        .flatMapLatest { granted ->
            if (!granted) return@flatMapLatest flowOf(emptyList())
            combine(
                locationTracker.locationUpdates(),
                prefs.radarRadiusMeters
            ) { location: Location, radius: Double -> location to radius }
                .onEach { (location, _) ->
                    // Publish our own position so we appear on *other* people's radars, and so our
                    // lastActiveMillis keeps refreshing (presence goes stale after 5 minutes).
                    // Without this the radar is read-only and nobody is ever visible to anyone.
                    profileRepository.updateLocation(selfUid, location.latitude, location.longitude)
                }
                .flatMapLatest { (location, radius) ->
                    radarRepository.observeNearbyOpenUsers(selfUid, location.latitude, location.longitude, radius)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<RadarUiState> = combine(
        profileRepository.observeOwnProfile(selfUid),
        nearby,
        prefs.radarRadiusMeters,
        sentInterestTo,
        hasPermission
    ) { profile, nearbyUsers, radius, sentTo, permission ->
        RadarUiState(
            selfProfile = profile,
            nearbyUsers = nearbyUsers,
            radiusMeters = radius,
            hasLocationPermission = permission,
            sentInterestTo = sentTo
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RadarUiState())

    fun onPermissionResult(granted: Boolean) {
        hasPermission.value = granted
    }

    fun toggleOpen(isOpen: Boolean) {
        viewModelScope.launch { profileRepository.setOpenStatus(selfUid, isOpen) }
    }

    fun sendInterest(target: NearbyUser) {
        val self = uiState.value.selfProfile ?: return
        sentInterestTo.value = sentInterestTo.value + target.profile.uid
        viewModelScope.launch {
            interestRepository.sendInterest(
                fromUid = selfUid,
                fromName = self.displayName,
                fromEmoji = self.avatarEmoji,
                toUid = target.profile.uid
            )
        }
    }
}

class RadarViewModelFactory(
    private val selfUid: String,
    private val profileRepository: ProfileRepository,
    private val radarRepository: RadarRepository,
    private val interestRepository: InterestRepository,
    private val locationTracker: LocationTracker,
    private val prefs: OpenlyPrefs
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RadarViewModel(
            selfUid, profileRepository, radarRepository, interestRepository, locationTracker, prefs
        ) as T
    }
}
