package com.trippin.ai.ui.screens.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trippin.ai.AppContainer
import com.trippin.ai.auth.Session
import com.trippin.ai.data.model.AltPlace
import com.trippin.ai.data.model.Comment
import com.trippin.ai.data.model.Decision
import com.trippin.ai.data.model.Destination
import com.trippin.ai.data.model.Idea
import com.trippin.ai.data.model.IdeaStatus
import com.trippin.ai.data.model.IdeaType
import com.trippin.ai.data.model.Itinerary
import com.trippin.ai.data.model.ItineraryStop
import com.trippin.ai.data.model.Member
import com.trippin.ai.data.model.MemberPrefs
import com.trippin.ai.data.model.PlaceHit
import com.trippin.ai.data.model.Proposal
import com.trippin.ai.data.model.ProposalStatus
import com.trippin.ai.data.model.ProposalType
import com.trippin.ai.data.model.Role
import com.trippin.ai.data.model.Trip
import com.trippin.ai.data.model.TripEvent
import com.trippin.ai.data.repository.DayWeather
import com.trippin.ai.data.repository.PlanStage
import com.trippin.intelligence.TripBrain
import com.trippin.intelligence.group.Diet
import com.trippin.intelligence.group.StartPreference
import com.trippin.intelligence.group.VoteKind
import com.trippin.intelligence.model.Category
import com.trippin.intelligence.model.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Everything the trip screen shows, combined from the live Firestore listeners. */
data class TripUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val trip: Trip? = null,
    val members: List<Member> = emptyList(),
    val ideas: List<Idea> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val decisions: List<Decision> = emptyList(),
    val prefs: List<MemberPrefs> = emptyList(),
    val itinerary: Itinerary? = null,
    val reactions: Map<String, Map<String, VoteKind>> = emptyMap(),
    val proposals: List<Proposal> = emptyList(),
    val events: List<TripEvent> = emptyList(),
) {
    fun roleOf(uid: String): Role? = members.firstOrNull { it.uid == uid }?.role
    val destinationIdeas: List<Idea> get() = ideas.filter { it.type == IdeaType.DESTINATION && it.status != IdeaStatus.DISMISSED }.sortedByDescending { it.score }
    val placeIdeas: List<Idea> get() = ideas.filter { it.type == IdeaType.PLACE && it.status != IdeaStatus.DISMISSED }.sortedByDescending { it.score }
    val dismissedIdeas: List<Idea> get() = ideas.filter { it.status == IdeaStatus.DISMISSED }
    val pendingProposals: List<Proposal> get() = proposals.filter { it.status == ProposalStatus.PENDING }
    fun commentsFor(ideaId: String?): List<Comment> = comments.filter { it.ideaId == ideaId }
    fun prefsOf(uid: String): MemberPrefs? = prefs.firstOrNull { it.uid == uid }
    /** Members who've told the planner something about themselves. */
    val prefsSetCount: Int get() = members.count { m -> prefs.any { it.uid == m.uid && it.updatedAt > 0 } }
}

sealed interface Generation {
    data object Idle : Generation
    data class Running(val stage: PlanStage) : Generation
}

class TripViewModel(private val c: AppContainer, val tripId: String, val me: Session) : ViewModel() {

    private data class Core(val trip: Trip?, val members: List<Member>, val ideas: List<Idea>, val comments: List<Comment>, val decisions: List<Decision>)
    private data class Plan(val prefs: List<MemberPrefs>, val itinerary: Itinerary?, val reactions: Map<String, Map<String, VoteKind>>, val proposals: List<Proposal>, val events: List<TripEvent>)

    private var tripSeen = false

    private val core = combine(
        c.tripRepository.trip(tripId),
        c.tripRepository.members(tripId),
        c.collabRepository.ideas(tripId),
        c.collabRepository.comments(tripId),
        c.collabRepository.decisions(tripId),
    ) { t, m, i, cm, d -> Core(t, m, i, cm, d) }

    private val plan = combine(
        c.collabRepository.prefs(tripId),
        c.planRepository.itinerary(tripId),
        c.planRepository.reactions(tripId),
        c.planRepository.proposals(tripId),
        c.tripRepository.events(tripId),
    ) { p, it, r, pr, e -> Plan(p, it, r, pr, e) }

    val ui: StateFlow<TripUi> = combine(core, plan) { a, b ->
        if (a.trip != null) tripSeen = true
        TripUi(
            loading = false,
            missing = a.trip == null,
            trip = a.trip, members = a.members, ideas = a.ideas, comments = a.comments, decisions = a.decisions,
            prefs = b.prefs, itinerary = b.itinerary, reactions = b.reactions, proposals = b.proposals, events = b.events,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripUi())

    val generation = MutableStateFlow<Generation>(Generation.Idle)
    val weather = MutableStateFlow<List<DayWeather>?>(null)
    private var weatherFor: String? = null

    init {
        viewModelScope.launch { c.preferences.markSeen(tripId) }
    }

    private fun trip(): Trip? = ui.value.trip
    private val role: Role? get() = ui.value.roleOf(me.uid)
    val canManage: Boolean get() = role?.canManage == true
    val canContribute: Boolean get() = role?.canContribute == true

    fun loadWeather(t: Trip) {
        val d = t.stay ?: t.destination ?: return
        val key = "${d.lat},${d.lng},${t.startDate},${t.days}"
        if (key == weatherFor) return
        weatherFor = key
        viewModelScope.launch { weather.value = c.placesRepository.forecast(d.lat, d.lng, t.startDate, t.days) }
    }

    // ---- Suggestions & votes ----
    suspend fun searchDestinations(q: String) = c.placesRepository.searchDestinations(q)
    suspend fun searchPlaces(q: String): Result<List<PlaceHit>> {
        val d = trip()?.destination ?: return Result.success(emptyList())
        return c.placesRepository.searchPlaces(q, d)
    }
    suspend fun nearby(): Result<List<Place>> {
        val t = trip() ?: return Result.success(emptyList())
        val d = t.stay ?: t.destination ?: return Result.success(emptyList())
        return c.placesRepository.nearby(d.lat, d.lng, t.days)
    }

    fun suggest(type: IdeaType, hit: PlaceHit) { trip()?.let { c.collabRepository.suggest(it, me, type, hit, ui.value.ideas) } }
    fun suggestPlace(p: Place) = suggest(IdeaType.PLACE, PlaceHit(p.name, null, null, p.lat, p.lng, p.category, p.tags))

    /** Tapping the vote you already gave clears it. */
    fun vote(idea: Idea, kind: VoteKind) {
        val t = trip() ?: return
        val current = idea.votes[me.uid]
        c.collabRepository.vote(t, me, idea, if (current == kind) null else kind)
    }

    fun dismiss(idea: Idea) { trip()?.let { c.collabRepository.dismiss(it, idea) } }
    fun restore(idea: Idea) { trip()?.let { c.collabRepository.restore(it, idea) } }
    fun comment(idea: Idea?, text: String) { trip()?.let { c.collabRepository.comment(it, me, idea, text) } }
    fun deleteComment(cm: Comment) { trip()?.let { c.collabRepository.deleteComment(it, cm) } }

    fun openVote(closesAt: Long?) { trip()?.let { c.collabRepository.openDestinationVote(it, me, closesAt) } }
    fun decide(winner: Idea) { trip()?.let { c.collabRepository.decideDestination(it, me, winner, ui.value.ideas) } }
    fun setDestination(d: Destination?) { trip()?.let { c.collabRepository.setDestination(it, me, d) } }
    fun setStay(d: Destination?) { trip()?.let { c.tripRepository.setStay(it, me, d) } }
    fun updateDetails(title: String, start: LocalDate, days: Int, pace: TripBrain.Pace) {
        trip()?.let { c.tripRepository.updateDetails(it, me, title, start, days, pace) }
    }

    // ---- Plan ----
    fun generate(onDone: (Result<Itinerary>) -> Unit) {
        val t = trip() ?: return
        if (generation.value is Generation.Running) return
        viewModelScope.launch {
            val u = ui.value
            val r = c.planRepository.generate(t, me, u.prefs, u.ideas, u.itinerary) { stage -> generation.value = Generation.Running(stage) }
            generation.value = Generation.Idle
            onDone(r)
        }
    }

    fun react(stop: ItineraryStop, kind: VoteKind) {
        val t = trip() ?: return
        val current = ui.value.reactions[stop.key]?.get(me.uid)
        c.planRepository.react(t, me, stop.key, if (current == kind) null else kind)
    }

    /** Direct edit when you manage an unlocked plan; otherwise a proposal for the owner to approve. */
    fun removeStop(stop: ItineraryStop, reason: String = "") {
        val t = trip() ?: return
        val it = ui.value.itinerary ?: return
        if (canManage && !it.locked) c.planRepository.removeStop(t, me, it, stop.key)
        else c.planRepository.propose(t, me, ProposalType.REMOVE_STOP, stop, null, reason)
    }

    fun moveStop(stop: ItineraryStop, toDay: Int, reason: String = "") {
        val t = trip() ?: return
        val it = ui.value.itinerary ?: return
        if (canManage && !it.locked) c.planRepository.moveStop(t, me, it, stop.key, toDay)
        else c.planRepository.propose(t, me, ProposalType.MOVE_STOP, stop, toDay, reason)
    }

    fun addAlternate(alt: AltPlace, toDay: Int) {
        val t = trip() ?: return
        val it = ui.value.itinerary ?: return
        c.planRepository.addAlternate(t, me, it, alt, toDay)
    }

    fun resolve(p: Proposal, approve: Boolean) { trip()?.let { c.planRepository.resolve(it, me, p, approve, ui.value.itinerary) } }
    fun confirmPlan() { val t = trip() ?: return; ui.value.itinerary?.let { c.planRepository.confirm(t, me, it) } }
    fun reopenPlan() { trip()?.let { c.planRepository.unlock(it, me) } }

    // ---- People ----
    fun createInvite(): String? = trip()?.let { c.tripRepository.createInvite(it, me) }
    fun revokeInvite() { trip()?.let { c.tripRepository.revokeInvite(it, me) } }
    fun setRole(m: Member, r: Role) { trip()?.let { c.tripRepository.setRole(it, m, r, me) } }
    fun remove(m: Member) { trip()?.let { c.tripRepository.removeMember(it, m, me) } }
    fun leave() { trip()?.let { c.tripRepository.leave(it, me) } }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.tripRepository.delete(tripId); onDone() }

    val isDemo: Boolean get() = c.backend.isDemo

    /**
     * Demo mode only: adds three simulated friends with different constraints and votes, so the
     * group features (voting, conflicting preferences, the planner's compromises) can be shown on one phone.
     */
    fun simulateFriends() = viewModelScope.launch {
        val t = trip() ?: return@launch
        val friends = listOf(
            Triple("Sara", setOf(Category.FOOD, Category.MUSEUM), MemberPrefs("", "Sara", setOf(Category.FOOD, Category.MUSEUM), diet = Diet.VEGETARIAN, start = StartPreference.LATE)),
            Triple("Rahul", setOf(Category.LANDMARK, Category.NIGHTLIFE), MemberPrefs("", "Rahul", setOf(Category.LANDMARK, Category.NIGHTLIFE), noAlcohol = false)),
            Triple("Mei", setOf(Category.PARK, Category.SHOPPING), MemberPrefs("", "Mei", setOf(Category.PARK, Category.SHOPPING), maxWalkKm = 6, noAlcohol = true)),
        )
        val existing = ui.value.members.map { it.name }.toSet()
        val ideas = ui.value.ideas
        friends.filter { it.first !in existing }.forEachIndexed { i, (name, _, p) ->
            val uid = c.tripRepository.addSimulatedMember(t.id, name)
            c.collabRepository.savePrefsAs(t.id, p.copy(uid = uid, name = name, updatedAt = System.currentTimeMillis()))
            ideas.filter { it.status != IdeaStatus.DISMISSED }.forEachIndexed { j, idea ->
                val kind = when ((i + j) % 4) { 0 -> VoteKind.MUST; 1, 2 -> VoteKind.UP; else -> VoteKind.DOWN }
                c.collabRepository.voteAs(t.id, uid, idea.id, kind)
            }
        }
    }

    suspend fun waitLoaded(): TripUi = ui.first { !it.loading }
}
