package com.trippin.core.repository

import com.trippin.core.common.DataResult
import com.trippin.core.common.runNetwork
import com.trippin.core.network.ApiService
import com.trippin.core.network.CreateTravellerRequestDto
import com.trippin.core.network.JoinTripRequestDto
import com.trippin.core.network.JoinTripResponseDto
import com.trippin.core.network.ShareLinkDto
import com.trippin.core.network.TripCollabDto
import com.trippin.core.network.TripVersionsDto
import com.trippin.core.network.VoteRequestDto
import com.trippin.core.network.TravellerDto
import com.trippin.core.network.UpdateTravellerRequestDto
import javax.inject.Inject
import javax.inject.Singleton

/** The people on a trip: who is going, what each wants and can spend, and the invite link. */
@Singleton
class GroupRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun getTravellers(tripId: String): DataResult<List<TravellerDto>> =
        runNetwork { api.getTravellers(tripId) }

    suspend fun addTraveller(tripId: String, body: CreateTravellerRequestDto): DataResult<TravellerDto> =
        runNetwork { api.addTraveller(tripId, body) }

    suspend fun updateTraveller(
        tripId: String,
        travellerId: String,
        body: UpdateTravellerRequestDto
    ): DataResult<TravellerDto> =
        runNetwork { api.updateTraveller(tripId, travellerId, body) }

    suspend fun deleteTraveller(tripId: String, travellerId: String): DataResult<Unit> =
        runNetwork { api.deleteTraveller(tripId, travellerId); Unit }

    suspend fun joinTrip(body: JoinTripRequestDto): DataResult<JoinTripResponseDto> =
        runNetwork { api.joinTrip(body) }

    suspend fun createShareLink(tripId: String): DataResult<ShareLinkDto> =
        runNetwork { api.createShareLink(tripId) }

    /** Stops every live link to the trip. Anyone holding an old link can no longer open it. */
    suspend fun revokeShareLinks(tripId: String): DataResult<Int> =
        runNetwork { api.revokeShareLinks(tripId).revoked }

    suspend fun getCollab(tripId: String): DataResult<TripCollabDto> =
        runNetwork { api.getCollab(tripId) }

    suspend fun vote(tripId: String, body: VoteRequestDto): DataResult<TripCollabDto> =
        runNetwork { api.vote(tripId, body) }

    /** Every saved version of the plan, newest first. */
    suspend fun getVersions(tripId: String): DataResult<TripVersionsDto> =
        runNetwork { api.getVersions(tripId) }
}
