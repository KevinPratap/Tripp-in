package com.trippin.ai.data.share

import com.trippin.ai.data.model.Idea
import com.trippin.ai.data.model.IdeaStatus
import com.trippin.ai.data.model.IdeaType
import com.trippin.ai.data.model.Itinerary
import com.trippin.ai.data.model.Member
import com.trippin.ai.data.model.Trip

/**
 * The public, read-only copy of a trip that the live web page shows. It holds only what is meant
 * to be seen by anyone with the link: names, places, the plan and vote counts. No user ids,
 * emails, expenses, comments or preferences ever go in here.
 */
object ShareSnapshot {
    const val BASE_URL = "https://trippin-ee84c.web.app"

    fun link(code: String) = "$BASE_URL/t/$code"

    fun build(trip: Trip, members: List<Member>, ideas: List<Idea>, itinerary: Itinerary?): Map<String, Any?> = mapOf(
        "tripId" to trip.id,
        "title" to trip.title,
        "status" to trip.status.name,
        "destination" to trip.destination?.let { mapOf("name" to it.name, "country" to it.country, "lat" to it.lat, "lng" to it.lng) },
        "startDate" to trip.startDate.toString(),
        "days" to trip.days,
        "members" to members.take(30).map { mapOf("name" to it.name) },
        "ideas" to ideas
            .filter { it.type == IdeaType.PLACE && it.status != IdeaStatus.DISMISSED }
            .take(60)
            .map {
                mapOf(
                    "id" to it.id, "title" to it.title, "subtitle" to it.subtitle, "category" to it.category?.name,
                    "lat" to it.lat, "lng" to it.lng,
                    "love" to it.musts, "like" to (it.ups - it.musts), "pass" to it.downs,
                    "selected" to (it.status == IdeaStatus.SELECTED),
                )
            },
        "plan" to itinerary?.let { plan ->
            mapOf(
                "locked" to plan.locked,
                "days" to plan.days.map { d ->
                    mapOf(
                        "date" to d.date.toString(),
                        "stops" to d.stops.map { s ->
                            mapOf("name" to s.name, "category" to s.category.name, "start" to s.start, "leave" to s.leave, "optional" to s.optional)
                        },
                    )
                },
            )
        },
    )
}
