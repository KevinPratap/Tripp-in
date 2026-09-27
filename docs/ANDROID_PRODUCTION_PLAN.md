# Tripp'in Android: plan to production

Audit date 2026-09-27. Scope confirmed with you: **Android is the focus.** The web app is left
untouched. Backend work happens only where Android needs it.

---

## 1. What I actually found

I read all 51 Kotlin files (~6,100 lines) plus the backend routes that serve them.

### The architecture is good. It stays.

Compose + Material 3, Hilt, Room as an offline-first cache, DataStore, Retrofit with
kotlinx.serialization, type-safe Navigation Compose routes, a `DataResult` sealed type, ViewModels
exposing `StateFlow` collected with `collectAsStateWithLifecycle`. There is a real design system with
semantic colour tokens for light and dark, a nine-role type scale, and custom fonts (Fraunces +
Nunito). `ItineraryViewModel` reads from Room first and refreshes over the network on top, so the app
works on a bad connection. Some of the copy is genuinely honest: "Offline. Showing what your phone
has.", "Nothing is picked for you."

Rebuilding this from scratch would throw away the good part. I am not proposing that.

### Why it feels bad to use

**1. There is no autocomplete anywhere, and the code for it is already dead in the repo.**
`PlannerScreen.kt:100` is a bare text field with placeholder "e.g. Manali, Lisbon, Tokyo". The only
validation is `destination.trim().length >= 2` (`PlannerViewModel.kt:36`). Type "Lisbonn" and you
wait out a full generation before anything tells you. Meanwhile `PlacesRepository.search()` and
`ApiService.searchPlaces` exist and **nothing in the app calls either one**. The plumbing for the
feature you asked about is sitting there unused.

**2. Every surface has the identical treatment, so nothing leads the eye.**
`TrippinSurface` is documented as "the one raised surface in the app": 2.5dp ink border plus a hard
4dp offset shadow. It wraps cards, buttons, chips, text fields and tabs alike. When every element is
equally loud there is no hierarchy, and hard-shadow-on-everything is the visual signature of a
generated brutalist template. Letter-spacing of 0.8sp on labels and 1.0sp on captions widens the
text further in the same direction.

**3. Currency is hardcoded to INR.** `PlannerUiState.currency = "INR"` with no inference at all. A
Lisbon trip opens in rupees. The web app has `inferCurrency()`; Android has nothing. This breaks the
project's own invariant (AGENTS.md section 1.5, currency must follow the destination).

**4. A whole feature is unreachable.** `ProfileViewModel` observes saved spots, but no screen in the
app can save one. The "You" tab shows a list that can never fill. There is no search or discovery
surface at all.

**5. The small conveniences are all absent.** No recent or suggested destinations. No date presets,
and two separate single-date dialogs instead of one range picker. No notification when a plan
finishes, so a two-minute wait means staring at the screen. No deep links, so a shared trip link
cannot open the app. No share sheet, no add-to-calendar (the web app has ICS export, Android does
not), no maps hand-off per stop, no haptics, no undo on delete, no swipe actions. Loading states are
a text label (`LoadingBlock("Loading your trips")`) rather than skeletons.

**6. Zero tests.** No `test/` or `androidTest/` directory exists. 6,100 lines, not one test.

**7. The release build is not shippable.** `isMinifyEnabled = false` with a comment that R8 is off
until keep rules can be checked on a device. No signing config, no baseline profile.

### Two hard production blockers

**Sign-in is mandatory and does not work.** `ApiService.kt` states "The guest model is gone: these
are the only ways to get an identity." The only way in is a magic link, and the backend returns
`delivery: 'console'` and writes the link to the server log instead of emailing it
(`auth.service.ts:20`). So a real user cannot get into the app at all. This is the single most
important fix and it needs your Resend key.

**Correction to an earlier claim in this plan.** I first wrote that generation was unthrottled. That
was wrong: `POST /trips/:id/generate` carries `@RateLimit({ limit: 5, windowMs: 60000 })`
(`trips.controller.ts:34`), and every other write route is limited too. The real gap is narrower. The
limiter keeps its buckets in a process-local `Map` (`rate-limit.guard.ts:43`), so on more than one
Railway instance each instance counts separately and the effective limit multiplies by the instance
count. Redis is already wired into the app and is where that state belongs.

### Verified baseline

- Backend: 86 tests pass across 10 suites, builds clean (after `pnpm db:generate` and building the
  workspace packages, which are prerequisites the README does not mention).
- Web: builds clean, 11 routes.
- Android: **cannot be compiled in this session.** See section 4.

---

## 2. What I will change

### Phase 1 - Unblock and fix what is broken

1. Real email delivery for magic links, behind a provider interface so the transport is swappable.
   Resend on the free tier. Without this the app has no usable front door.
2. `GET /api/v1/places/autocomplete?q=` on the backend, backed by **Photon** (photon.komoot.io):
   OpenStreetMap data, free, no API key, and unlike Nominatim its usage policy permits autocomplete.
   Returns a canonical name, region, country code, coordinates and a stable id.
3. Currency inferred from the **resolved country code** that autocomplete returns, not from matching
   strings against a city name. Shared logic so Android and the backend agree.
4. Move the rate limiter's buckets into Redis so the limit holds across instances, and bound the new
   autocomplete route, which is called as the traveller types.
5. Test infrastructure for Android: JUnit + Turbine + a Compose UI test harness, plus an Android job
   in CI (compile, unit tests, lint). CI has no Android job today.

### Phase 2 - Re-tier the design system to something calmer

Keeping the Fraunces/Nunito pairing and the crimson accent, which are distinctive and worth keeping.
Changing how they are applied:

- **Tiered elevation instead of one treatment.** Page gets no border. Cards get a large radius and a
  hairline or soft shadow. The hard offset shadow is kept deliberately for the primary action only,
  so the app keeps its character without shouting on every element.
- **Tighter letter-spacing** on labels and captions, and sentence case where small caps are doing
  nothing.
- **More air**: a 20/24dp section rhythm rather than a uniform 16dp.
- **Real imagery on trip cards.** Coil is already a dependency and the backend already has
  `city-photos.ts`, so destination photography can carry the trip list instead of borders doing all
  the work. Any photo is labelled with its source, per the provenance rule.
- Material 3 motion, and bottom sheets where a full screen push is currently overkill.

### Phase 3 - The convenience layer

1. **Destination autocomplete** as a proper component: ~250ms debounce, inline loading, keyboard-aware
   dropdown, tap to select, and it keeps the resolved coordinates. This is what makes the currency
   correct and stops typos from costing a two-minute generation.
2. **Recent destinations** in Room, offered on focus.
3. **Date presets** (this weekend, next weekend, plus three days, plus a week) and a single range
   picker replacing the two dialogs.
4. **A notification when the plan is ready**, via WorkManager, so you can leave the app during the
   wait.
5. **Deep links** so a shared trip link opens the app.
6. **Share sheet**, **add to calendar**, and **open this stop in maps**.
7. **Haptics** on commit actions, **undo** on delete via snackbar, **swipe actions** on trip rows.
8. **Skeleton loaders** replacing the text labels.
9. **A search surface** that makes `PlacesRepository.search` and saved places reachable, so the "You"
   tab stops showing a list that cannot fill.

### Phase 4 - Rebuild the screens on the new system

Planner, Trips, Itinerary, Today, Group, Map, Profile, Sign-in, in that order. Same features, same
honest copy where it is already good, rebuilt on the re-tiered design system with the convenience
layer wired in. Nothing is dropped.

### Phase 5 - Production hardening

- Unit tests for planner validation, currency inference, the autocomplete reducer, `DataResult`
  mapping and repository caching. Compose UI tests for the planner form, autocomplete selection, the
  trips empty state and itinerary rendering. Room migration tests.
- R8 turned on with keep rules actually verified against a build.
- Signing config driven by a keystore and environment variables.
- Baseline profile for startup.
- Crash reporting.
- Accessibility pass: TalkBack labels, touch targets, dark-theme contrast.
- Revisit `ksp.useKSP2=false` by moving Room to 2.7.x, which supports KSP2.
- Re-run the full AGENTS.md section 4 checklist.

### Deliberately not in scope

The web app. You were explicit. It builds and works today; I am leaving it alone.

---

## 3. What I need from you

**1. `dl.google.com` added to this environment's allowed network hosts. This one is critical.**
Every Android build artifact comes from there: `maven.google.com` 301-redirects to
`dl.google.com/dl/android/maven2/...`, and the Android SDK itself is served from the same host. I
proved it: `./gradlew compileDebugKotlin` fails at
`Plugin [id: 'com.android.application', version: '8.7.3'] was not found`, and a direct fetch of that
artifact ends in `connect_rejected` on dl.google.com. **Until that host is allowed I can write Kotlin
but I cannot compile or test one line of it.** For a plan whose whole scope is Android, that is the
difference between verified work and unverified work.

Also worth adding while you are in there, for live checks and the smoke test that AGENTS.md section 4
requires: `photon.komoot.io`, `nominatim.openstreetmap.org`, `router.project-osrm.org`,
`api.open-meteo.com`, and your two Railway hosts.

You change this in the cloud environment menu in the session title bar, then Edit, then Network
access.

**2. A Resend API key.** Free tier, 3,000 emails a month, no card. This is what turns sign-in from
broken into working. Stays within the zero-cost rule.

**3. A Gemini API key** in the environment as `GEMINI_API_KEY`, if you want me running real
end-to-end generations rather than fixtures. `generativelanguage.googleapis.com` is already reachable.

**4. Optional: a crash reporting DSN** (Sentry free tier) if you want crash reports live.

**5. Optional: a release keystore.** Without one I will wire the signing config to read from
environment variables and leave you to generate and hold the keystore, which is the safer split
anyway since I should not be generating your signing key.

Nothing here introduces a paid dependency, and DeepSeek stays out of the stack, per AGENTS.md
section 1.10.

---

## 4. What I cannot verify, and I will say so rather than paper over it

Right now, with the network as it is:

- I cannot compile Kotlin, run Android unit tests, or run Compose UI tests. No AGP, no AndroidX or
  Compose artifacts, no Android SDK.
- There is no emulator or device in this container, so `installDebug` and any on-device check are out
  regardless of the network. Screenshots of the running app are not something I can produce here.
- I cannot reach your deployed backend or the OSM, OSRM and Open-Meteo services, so the live smoke
  test in AGENTS.md section 4 item 5 is not something I can run today.

With `dl.google.com` allowed, the first item is solved and the compile plus the full test suite
become real verification. The device check stays yours. I will state plainly in every report which
checks actually ran and which did not.

---

## 5. Order of work

Phase 1 first, because email delivery and autocomplete unblock the two things you actually
complained about. Phase 2 and 3 land together per screen so you can see a real screen in the new
style early rather than at the end. Phase 5 runs continuously, not as a final bolt-on: tests land
with the code they cover.

I will commit in reviewable pieces on `claude/pensive-allen-dm04zu` with explicit file paths, and
report what passed and what did not at each step.

---

## 6. Progress

Verified by CI on every push to this branch, which compiles, unit tests and lints the Android sources.
`dl.google.com` is denied in the agent environment, so there is no local Android toolchain and CI is
where the Kotlin gets checked.

### Phase 1, done

**Destination autocomplete, end to end.** `GET /api/v1/places/autocomplete` backed by Photon, and the
Android field that uses it: suggestions as you type, debounced at 250ms, a stale response dropped
rather than shown, and a tick that distinguishes a place we resolved from words still to be guessed at.
Picking one puts the full label in the field so the engine geocodes something unambiguous. Typing is
never blocked, because the suggestions come from a call that can fail and the engine resolves the
destination again at build time. `PlacesRepository.search` was already in the repo with nothing calling
it; that is no longer the case.

**The currency bug, properly.** The Android planner opened every trip in INR whatever the destination,
and the web planner carried a hand-written table of about two hundred city names. Currency now comes
from the ISO country code the geocoder returns. There is no universal default, so there is none: the
destination sets it, a choice the traveller makes outranks that, and a country we cannot price leaves
it alone rather than guessing. A resolved currency outside the five base chips is added to them, so
Bangkok resolving to THB can still be seen and kept.

**Sign in works.** Magic links are emailed through a `Mailer` seam, Resend when configured and the
console log otherwise. A half configured mailer falls back rather than dropping links silently, and a
send failure surfaces as retryable instead of telling the traveller to check an inbox nothing reached.

**An account takeover, closed.** `POST /auth/request-link` is necessarily unguarded, since it is how a
caller first gets an identity, but it returned `loginUrl` with the raw token in its response body.
Anyone could name an address and read back a working session for it. The token is now returned only
outside production and never once a provider is delivering, so a misconfigured production fails closed.

**A UI bug I introduced and then found.** `suggestionsLoading` was set after the debounce rather than
before it, so every first lookup flashed "No matches yet" for a word nothing had searched for. The
composable had been deciding that from four booleans; it is now one `SuggestionHint` on the state,
ordered so loading outranks no-matches, with tests on the ordering.

**CI actually covers this branch.** It ran only on `main` and `develop`, so a feature branch got no
verification at all until a pull request existed. Compile is now its own step, and lint runs.

### Phase 2 and 3, in progress

**Surfaces have three tiers now.** `TrippinSurface` was documented as "the one raised surface in the
app" and behaved like it: a 2.5dp edge of full ink and a hard 4dp offset shadow on cards, buttons,
chips, fields and tab strips alike. FLAT (hairline, no shadow) is the default, RAISED adds a soft
shadow or a lighter surface in dark mode, and ACTION keeps the hard offset shadow for the primary
action alone. On Trips, the trip happening right now is the only card that lifts, which is what makes
the lift mean anything. The quiet tiers also reserve no space, where the old surface padded its own
end and bottom, so a column of cards no longer sits 4dp off its own gutter.

**Tracking and case.** Label carried 0.8sp and Caption 1.0sp on nearly every string in the app, which
reads as machine-set rather than typeset; both are now effectively none. Upper case moved to a new
Eyebrow role so it is a deliberate choice for a few group labels. Status pills dropped their ink
border and are carried by their tint.

**Date shortcuts.** This weekend, next weekend, a week. Setting a weekend used to be four taps across
two calendar dialogs. The week arithmetic takes today as an argument rather than reading the clock,
which is what made the awkward days correct: on a Saturday this weekend starts today, and on a Sunday
it is today alone rather than six days away.

**Skeletons instead of spinners.** Trips, Plan, Today and Group load into the shape of the rows that
are coming, rather than a centred spinner over the words "Loading your trips". They sit still when the
device has animations off, read from `ANIMATOR_DURATION_SCALE`. The app shell's startup and the map
keep spinners, having no content shape to stand in for.

### Counts


- Backend: 135 tests across 14 suites, up from 86. Backend and web builds clean.
- Android: 46 unit tests, up from zero. The app had no `test/` directory at all.

### Not started

The screen rebuilds on the new tiers (phase 4) and release hardening (phase 5): R8 with verified keep
rules, signing from environment variables, a baseline profile, crash reporting, and the accessibility
pass. Still open in the convenience layer: a notification when a plan is ready, deep links, the share
and calendar and maps hand-offs, haptics, undo on delete, recent destinations, and a search surface
that makes saved places reachable. Plus the rate limiter's move to Redis.

Phase 4 is where the design direction wants a decision from Kevin: the mock at
https://claude.ai/artifact/QEPKAgKcWsZLBpgArc8H5h shows the current Trips screen beside the proposed
one, and the two open questions on it are whether trip cards carry a destination photo and whether the
primary button keeps its hard shadow.

### Still unverified

- Photon's live response shape. The fixtures follow its documented format, but `photon.komoot.io` is
  denied here, so one check against the real service is still owed before release.
- Resend delivery. `api.resend.com` is denied here, so the transport is tested against a fake and has
  never sent a real email.
- Anything on a device. No emulator or device exists in this container, so nothing here has been seen
  running. CI compiles, tests and lints; it does not look at the screen.
