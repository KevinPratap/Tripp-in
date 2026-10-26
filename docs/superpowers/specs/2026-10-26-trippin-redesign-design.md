# Trippin' AI v4: redesign and Swipe Deck

Date: 2026-10-26. Target app: root `app/` (com.trippin.ai). Status: Phases 1 and 2 built, Phase 3 pending a compile check.

## Intent
Make Trippin' feel as polished and fast as Blinkit and Instagram, and add a Tinder-style Swipe Deck
so a group can upvote and downvote places while planning. Success: a group of friends can open a
trip, swipe through candidate places together, see live who agrees, and lock the winners into the plan.

## Constraints (from AGENTS.md and the repo)
- Keep Compose, Hilt-free manual DI as it is, Room cache, Firestore backend and rules. No paid services.
- Never invent ratings, reviews, hours or photos. Unknown values are shown as unknown.
- No emojis and no dashes in user-facing copy. Icons are vector icons only.
- Deterministic itinerary checks stay. Vote writes only touch the caller's own vote.
- The AGENTS.md palette rule is updated to the new identity as part of Phase 1.

## Visual language (Blinkit density, Instagram media)
- Clean light surface and true dark mode. One brand gradient (coral to violet) for primary actions,
  vote states and story rings. A warm yellow highlight is reserved for MUST.
- Large radii, soft elevation, no heavy borders. Hierarchy from scale, imagery and whitespace.
- Blinkit patterns: sticky header with destination chip and search, horizontally scrolling category
  chips, dense two-column place grid, a floating "shortlist bar" at the bottom ("5 picked, view").
- Instagram patterns: story-style rings row (trips and members, ring shows voting progress),
  five-tab bottom bar with a raised centre Swipe button, full-bleed media cards, double-tap to
  upvote with a heart burst, bottom-sheet comments, activity feed in the trip.
- Motion: spring physics on all transitions, shared-element card to detail, shimmer skeletons,
  haptics on votes. Respect the system reduce-motion setting.
- Cards without a real photo use a deterministic generated gradient plus category icon.

## Navigation
Bottom bar: Home, Explore, Swipe (centre), Trips, You.
- Home: story rings, sticky search, chips, "Needs your vote" rail, upcoming trip card.
- Explore: place discovery from existing PlacesRepository, save and add to a trip.
- Swipe: pick a trip, then the deck. Also reachable from a trip hub.
- Trips: list of trips with progress. Trip hub tabs: Feed, Swipe, Plan, People, Budget.
- You: profile, vibe check, preferences, saved places.

## Swipe Deck
- Gestures: right = UP, left = DOWN, up = MUST. Stamps fade in with drag distance. Button row for
  accessibility. Undo last swipe within the session.
- Data: reuses `trips/{id}/ideas/{ideaId}.votes[uid]` with MUST, UP, DOWN. No rules change.
- Deck order: unvoted by me first, ranked by group affinity and vibe check fit.
- Card content: name, category, distance, opening hours with estimated flag, Bayes risk pill,
  who suggested it, how many have voted.
- Live strip: avatars of members with voted/not voted, updating through the existing Firestore listener.
- Consensus (pure Kotlin, `intelligence/group/Consensus.kt`, unit tested): reuses the planner weights
  (MUST 3, UP 1, DOWN -2). Levels: LOVED, LIKED, SPLIT, DISLIKED, UNVOTED. SPLIT ideas go to a tiebreak
  round where everyone re-votes on just those.
- Match screen: ranked winners, group agreement percent, owner or admin taps "Lock into plan".

## New feature layer (wave 1)
1. Vibe check per member (pace, budget band, dealbreakers) stored in `prefs/{uid}`, used for ranking.
2. Budget tab: shared expenses and split, in the destination currency.
3. Trip recap card: shareable image of the locked plan.
4. Presence and activity feed from the existing `events` collection.
5. Day-of mode refresh using existing fatigue and next-stop engines.

## Phases (each compiles and ships alone)
1. Design system, navigation shell, Home, trip hub restyle.
2. Swipe Deck, consensus module and tests, match screen, lock into plan.
3. Vibe check, budget, recap, feed polish.

## Verification
After each phase on the user's machine: `.\gradlew.bat compileDebugKotlin` and
`.\gradlew.bat :intelligence:test`. Consensus logic gets tests first.

## Out of scope
Web app, backend, the older apps/android app, paid APIs.
