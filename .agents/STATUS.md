# Tripp'in AI: Agent Status Board

**Last Updated**: 2026-09-24T06:10:00Z
**Mainline Commit**: `350f565`
**Active Head**: `main`. Local `HEAD` and `origin/main` are both `350f565`, checked with
`git ls-remote origin refs/heads/main` at 06:04 UTC on 2026-09-24.

**Read this before you commit.** agy's `eefd056` ("populate checks array on every activity") also
contains part of a HERMES change set that was uncommitted in the tree at the time: the deletion of
`HomeScreen.kt`, `ExploreScreen.kt` and `TrippinNavHost.kt`, and eight new DTOs in
`core/network/NetworkModels.kt`. Nothing was lost, but that commit is not only a backend change and
`git show eefd056 --stat` is worth reading before either of us reasons about it. Stage explicit file
paths, never a directory.

**What changed in this rewrite (HERMES, 2026-09-24 06:10 UTC).** The board named `553dec0`, which is
fourteen commits behind `main` (`git rev-list --count 553dec0..HEAD` is 14, thirteen of them Android
and one of them this file). Specific corrections, each provable from this clone:

- The three lines above name `350f565`, and the HERMES row in section 1 names the work that landed
  after `553dec0` instead of stopping at it.
- Section 2 item 5 was wrong in three ways and is corrected: the planner no longer carries its own
  seven interest words (that list held `Art` and `Architecture`, which are on no vocabulary in the
  repo), so the two lists are one list now; the declaration has moved to
  `feature/group/GroupScreen.kt:535` and `:541`; and the planner reads it at
  `feature/planner/TripPlannerScreen.kt:21`.
- Section 3 gains a row for each of those fourteen commits, so the task list matches
  `git log --oneline 553dec0..HEAD`.
- Section 4 gains the item that decides a user visible sentence on the create form: the interests a
  traveller picks are dropped by `createTrip` before they reach the planner. Its checks and the three
  re-read items carry 2026-09-24 06:0x UTC.
- Section 5's device gap is re-measured: `git rev-list --count 03fc15b..HEAD` is 60 now, and
  `adb devices` lists nothing at 06:04 UTC on 2026-09-24.
- Nothing in this file was corrected from memory. `git log`, `git ls-remote`, `git rev-list`, four
  greps over `apps/android/app/src/main/java` and three live reads (the home feed, the Kyoto trip and
  `adb devices`) are the whole basis for it.

**What changed in the previous rewrite (HERMES, 2026-09-23 15:55 UTC).** The board named `eda05d4`,
which was twenty three commits behind `main` at that time. Specific corrections, each provable from
this clone:

- The two lines above named `553dec0`, and the HERMES row in section 1 named the work that landed
  after `eda05d4` instead of stopping at it.
- Section 3 gained a row for that work, so the task list matched `git log --oneline eda05d4..HEAD`.
- Section 4's checks carried 2026-09-23 rather than 2026-09-21: the demo user was still served with no
  credentials, `activity.support` was still absent from the wire, and one number in section 4 was
  wrong and was corrected there (the home feed returns five rows in `recentTrips`, not six).
- The device gap was re-measured rather than restated: `adb devices` listed nothing and the install
  step exited 1 with `device 'emulator-5554' not found`, while the wrapper still reported
  `BUILD SUCCESSFUL` with `GRADLE_EXIT_CODE=0`.

**What changed in the previous rewrite (HERMES, 2026-09-21 21:25 UTC).** The board named `696dc94`,
which was about forty commits behind `main` at that time. Specific corrections, each provable from this
clone:

- The open HERMES item about a demo shortcut on the sign-in screen is deleted. There is no such
  affordance: `git grep -in demo -- apps/android/app/src/main/java` returns nothing, and
  `SignInScreen.kt` holds no seeded account, no skip button and no test hook.
- The board did not know that the planner asks for the currency and the dates, that the Group tab can
  add, change and remove a person, that a joiner sets their own budget, pace and interests, or that
  the wait for a plan is a state of the Plan screen rather than a screen of its own. All four are in
  section 3 below with their commits.
- The HERMES lane now names `core/navigation/**` explicitly, because `Screen.kt`, `TrippinAppShell.kt`
  and the only bottom bar live there.
- One honest caveat belongs at the top rather than at the bottom: every Android commit below is
  verified by build and by grep, and the device half of every commit after `03fc15b` (2026-09-21
  02:54 UTC) is outstanding, which is now 60 commits by `git rev-list --count 03fc15b..HEAD`.
  `adb devices` lists nothing at 2026-09-24 06:04 UTC and the install step exits 1 with
  `device 'emulator-5554' not found`. The android-main-install.ps1 wrapper still reports BUILD
  SUCCESSFUL, so the gap is in the install and the visual read only.

---

## 1. Active Component Leases

| Component | Owner | Status | Notes |
| :--- | :--- | :--- | :--- |
| `apps/android` (UI files) | **HERMES** | **BUILD AND GREP VERIFIED, NOT SEEN ON A DEVICE** | `MainActivity.kt`, `feature/**`, `core/design/**`, `core/navigation/**`. Flat surfaces with no offset shadow anywhere, semantic colour tokens with no raw hex and no palette hue name in a screen (the only hue references left outside `Color.kt` are the ten rows of the Material scheme mapping in `Theme.kt` plus three comments, and zero `Color(0x` outside `Color.kt`, all re-measured 2026-09-24 06:05 UTC), seven-role type scale with zero bare `fontSize`, zero `MaterialTheme.typography`, and zero `MaterialTheme` and `colorScheme` reference outside `core/design` (same minute, every count zero). Recent work, newest first: the create form offers the one interest vocabulary the engine matches on (`350f565`), the Plan day card states only what the day's own data shows (`59cf1cb`), the You screen stops counting saved places nothing can save (`d2f0eb8`), the Trips card draws no dash where the trip has no date (`da27059`), the Group tab states a trip it could not load once with a way to try again (`9a9e924`), the Group empty state stops claiming the trip is offline (`64fd331`), the wait for a plan says so when it stops asking (`bed409c`), a photo source is named only for a host the app can read (`39107bd`), the Today empty state stops claiming today on a day that is not today (`411221a`), a solo trip says person rather than people (`4ea34fc`), the Group card stops printing the server's own status word (`7c95fcc`), a zero typed into an amount field is a number (`cb8bf1c`), the money fields read the numbers this app itself prints (`e97c6f2`), the zero-caller status badge deleted and that sweep closed (`553dec0`), sixteen dead imports deleted (`9136243`), the dead private helper `dateRangeLine` deleted with its class swept clean at 0 of 53 (`77c1777`), a refused write no longer printed as a status code (`2dcfe8c`), the ink on a crimson fill named `OnCrimson` (`09a89a1`, `b8ff144`), the 44dp touch floor declared on every control under it (`fdd7093`), every Material surface, plate, arc, rule and ink named by the app rather than the phone's theme (`e8deabd`, `eb7d87e`, `e39a817`, `09e597e`, `8fd7281`, `845b2f2`, `f32820e`, `3e49087`, `e06f654`, `9199ebf`), one name per colour with the dead palette rows gone (`36728f4`), two smaller honesty fixes (`8bc62a9`, `404d6e4`), the plan wait reading the server's own progress or none (`d1ba017`), the plan failure naming no cause (`f58f47f`), Trips list (`19804c8`), Plan shell with Days and Today (`d24f608`), Group write path (`f327c62`, `129cc11`, `c64274d`, `349cc8c`), the build state drawn inside Plan (`eda05d4`). |
| `apps/android` (Network) | **AGY** | **SHIPPED, SILENT SINCE 2026-09-20** | `core/network/**`. Guest identity removed; `SessionStore` holds the session token and the client sends `Authorization: Bearer`. Auth, share and my-trips calls added (`56ead62`, by HERMES while the lane was temporarily shared; the lane is agy's again and that is the last HERMES edit to it). No commit has touched `core/network/**` since `56ead62` on 2026-09-20. |
| `apps/backend` | **AGY** | **DEPLOYED & VERIFIED, SILENT SINCE 2026-09-20** | Price Provenance Guard (`4fd2965`), photo honesty (`cdf14bb`), reason normalisation (`6a65224`), checks backfill (`eefd056`), venue photo backfill on read (`0959974`, `231f0c8`), destination photographs (`28b4e95`). The last commit touching `apps/backend/**` or `packages/shared-types/**` is `28b4e95` on 2026-09-20, and agy's last word on the bus is 2026-09-20T06:35Z, so the open items in section 4 have had nobody on their side for four days. |
| `packages/shared-types` | **AGY** | **READY & SHIPPED** | `TravellerDto`, `TripOptionDto`, `StopSupportDto`, `TripSummary.currency`, `TripSummary.shareToken`, `HomeFeedResponse.totalTripsCount`. |

---

## 2. Recent Decisions & Alignments

1. **Frozen Interface (`.agents/INTERFACE.md`)**:
   - `TravellerDto` fields: `id`, `name`, `budgetCap`, `interests`, `dislikes`, `pace`, `joinedAt`.
   - Fixed vocabulary: `culture`, `food`, `nightlife`, `nature`, `adventure`, `shopping`, `museums`, `history`, `photography`, `wellness`, `relaxation`, `landmark`.
   - Endpoints:
     - `GET /api/v1/trips/:id/travellers`
     - `POST /api/v1/trips/:id/travellers`
     - `PATCH /api/v1/trips/:id/travellers/:tid`
     - `DELETE /api/v1/trips/:id/travellers/:tid`
     - `POST /api/v1/trips/join`
   - Trip additions: `trip.currency`, `trip.shareToken`, `trip.travellers`, `trip.perTravellerCost`, `trip.options`, `stop.support`.
   - Home Feed: `totalTripsCount?: number`.

2. **Non-Negotiable Truth Invariants**:
   - Zero invented counts: `support.want` and `support.total` count real travellers only.
   - `overCap` is honest: computed against that traveller's own budgetCap.
   - An option may only exist if the engine actually produced it under that objective.
   - Costs remain honest ranges with sources.
   - Price Provenance Guard: The API serializer must omit `estimatedCost` and `currency` on a stop unless a named source and checks accompany them.
   - Pure reads on getTripDetails (no share creation on read).
   - An address is navigable or it is not printed as one. A ward with no street is replaced by
     coordinates, and no button may offer an action the app cannot take.

3. **Accounts replaced the guest model (Kevin's call, 2026-09-20)**: no `X-Guest-Session`, no
   `guest:<id>` identity, no `migrateGuestTrips`. Sign-in is the magic link flow that already existed:
   `POST /auth/request-link` -> `{ email, expiresAt, delivery, loginUrl }`,
   `POST /auth/verify` -> `{ sessionToken, expiresAt, user, migratedTrips }`, and
   `GET /me/trips` with an `Authorization: Bearer` header doubles as the session check (401 means signed out).
   No mail provider is configured, so `delivery` is `console` and the app says so instead of claiming
   an email was sent. The backend half is still agy's: `firebase-auth.guard.ts` still mints guest
   identities and still falls back to the demo user for anonymous reads, which is item 1 of section 4
   and was re-read live at 06:05 UTC on 2026-09-24.

4. **The images trap, so nobody pays for it twice**: Coil builds on OkHttp, OkHttp's default
   User-Agent is `okhttp/<version>`, and Wikimedia answers that with **403**. The photograph was on the
   wire the whole time. Coil now gets its own client from an `ImageLoaderFactory` with a descriptive
   agent carrying a contact (`19eb5b5`), kept separate from the API client so an image request never
   carries the session token. One consequence reached the UI at `39107bd`: the photo provenance tag
   used to label every host that was not Wikimedia as OpenStreetMap map data, a source the app had
   read nothing about, and it now names a source only for a host it can actually read.

5. **The traveller vocabulary has one home and it is in the UI lane**: `TRAVELLER_INTEREST_WORDS` and
   `TRAVELLER_PACE_CHOICES`, both `internal`, declared in `feature/group/GroupScreen.kt:535` and
   `:541`, read by the three Group sheets, imported by `TripsScreen.kt:60` and `:61` for the join
   sheet, and imported by `TripPlannerScreen.kt:21` for the create form. There is no second list to
   drift, and that last import is new: `350f565` removed the planner's own seven words, which held
   `Art` and `Architecture` (on no vocabulary in the repo) and was missing five of the contract's
   twelve. The name change from `GROUP_*` to `TRAVELLER_*` came with `129cc11`, which is also why the
   join sheet asks the same optional fields the Add someone sheet asks.
   **Do not conclude from this that the field works.** The words are only half of it: `createTrip`
   drops the array before the planner ever sees it, see section 4 item 7, so a traveller can tick
   interests and the plan is built as if they had ticked nothing.

6. **A cap or a pace cannot be removed from the Android app, and that is a wire fact, not a UI
   choice.** `core/network`'s `Json` is `Json { ignoreUnknownKeys = true }`, and
   `kotlinx.serialization`'s `encodeDefaults` defaults to false, so any property still holding its
   default is omitted from the body: `budgetCap = null` and `pace = null` never reach the wire, while
   `interests = emptyList()` does encode as `{"interests":[]}`. Measured with the app's own
   `UpdateTravellerRequestDto` against the real serializer in an offline JVM harness at
   `/home/prata/tmp/ktest`, not reasoned from the documentation. The edit sheet states the limitation
   where the owner hits it and holds Save, rather than reporting a clearance it did not make
   (`c64274d`).
   **Do not flip `encodeDefaults` on by itself.** The edit sheet sends only the fields that changed, so
   an explicit null reaching the update path would wipe that person's cap, pace, interests and dislikes
   on a rename alone. The safe shapes are an explicit clear flag on the wire, or the sheet sending the
   whole field set it knows about in the same change that turns nulls on.

7. **Building a plan is a state of Plan, not a screen of its own** (`eda05d4`). `Screen.Generating` and
   its composable are deleted, and `GeneratingScreen` is drawn inside the Plan screen through
   `embedded` and `modifier` parameters that default to the old full window behaviour. The wait is
   gated on the server's own answer rather than a local guess: it draws only where there is no plan AND
   the trip's own `status` is `GENERATING` or `FAILED`. `status` is `GENERATING` from
   `triggerGeneration` (`trips.service.ts:191`) until the run lands, and `READY`, `FAILED` or `DRAFT`
   otherwise (`prisma/schema.prisma:153`); the details payload republishes it at
   `trips.service.ts:376`. A trip that was never generated still reads "No plan yet". `bed409c` closed
   the other half of this: the poll's budget runs out after about ninety seconds and the screen now
   says so rather than spinning on work it had stopped asking about.

8. **`travelersCount` is no longer the client's notion of travellers** (`a3c7ac2`, section 8 item 10 of
   the production plan). The Trips card counts `trip.travellers` and states the set-up size as a plan
   only while nobody has joined ("Set up for 2 travellers"), so the card and the Group tab agree. On
   the live list payload every trip is `travelersCount: 2` with `travellers: []` and
   `perTravellerCost: []`, re-read at 06:05 UTC on 2026-09-24 across five trips whose `currency` is
   USD, EUR, EUR, JPY and EUR. `4ea34fc` fixed the singular on the Group tab's own version of that
   sentence, which read "set up for 1 people".

9. **A state word the app has not read is not a label it may print.** `7c95fcc` removed
   `Plan version 1 - Not locked - Ready` from the Group card, where the last word was `trip.status`
   lowercased and capitalised: READY is a generation job state, and a READY label drawn off it is on
   the plan's delete list (section 8 item 9 of the production plan). The same rule covers the money
   fields, which is why `e97c6f2` and `cb8bf1c` had to move together.

---

## 3. Active Task List

- [x] **AGY**: Commit Android overhaul files to `main` (`c00859c`).
- [x] **AGY**: Multi-agent collaboration protocol & bus (`ae1361c`).
- [x] **HERMES**: Step 1 4-tab shell landed on `main` (`28259d8`).
- [x] **AGY**: Boot emulator-5554, install Step 1 APK, smoke-test and report UI findings to Hermes.
- [x] **AGY**: Implement backend Traveller model, CRUD, `/trips/join`, `perTravellerCost`, `stop.support`, and multi-objective options (`589cfbb`).
- [x] **AGY**: Implement Android network DTOs & `ApiService` endpoints in `core/network/**` (`589cfbb`).
- [x] **HERMES**: Rebuild Group screen with real travellers, caps, and conflicts (`ba56f34`).
- [x] **AGY**: Add currency & shareToken to TripSummaryDto and trips.service (`8140cbb`, `92bb21d`, `645e493`).
- [x] **HERMES**: Rebuild Trips list cards with state chip, countdown, per-person range, needs-you (`19804c8`).
- [x] **AGY**: Populate perTravellerCost, currency, shareToken, totalTripsCount on list and home feed (`826cfaa`).
- [x] **HERMES**: Section 7 copy pass across Plan, You, Today, Planner, Generating, Map (`1151882`).
- [x] **AGY**: Visual smoke test of 12sp labels and expanded You/Plan cards on emulator-5554.
- [x] **HERMES**: Real currency on Plan day card & stop prices, removed hardcoded dollar signs (`c6559dd`).
- [x] **AGY**: Visual verification of currency formatting (JPY/¥) on emulator-5554 & Tokyo costJson data audit.
- [x] **HERMES**: Raise last sub-12sp labels on reachable screens to 12sp (`dec4013`).
- [x] **AGY**: Visual inspection of 12sp Trips filter row on emulator-5554 (confirmed 38dp pill inner height fits 12sp cleanly).
- [x] **AGY**: Land and deploy Price Provenance Guard to Railway backend (`4fd2965`, deployment `ff5eb114`).
- [x] **AGY**: Live production endpoint verification & emulator-5554 visual verification on Kyoto without unverified prices.
- [x] **AGY**: Photo honesty & Paris fallback closed (`cdf14bb`, deployment `9828bed8`).
- [x] **AGY**: Section 4.2 Reason Normalization deployed & live on wire (`6a65224`, deployment `bd8f0bbe`).
- [x] **AGY**: Checks array populated on every activity via baseline backfill on read (`eefd056`). Contains part of a HERMES change set, see the note at the top of this file.
- [x] **HERMES**: Flatten every offset shadow, delete the three dead screens, add semantic colour tokens and the seven-role type scale (`56ead62`, `696dc94`).
- [x] **HERMES**: Real accounts end to end, with invite and join reachable from the Trips card and the fake device id gone from You (`56ead62`).
- [x] **HERMES**: Venue photographs filled in on read, matched to the name's own Wikipedia script, deployed and verified live (`0959974`, `231f0c8`).
- [x] **HERMES**: Addresses navigable or replaced by coordinates, and attribution corrected for `thumb.wikimedia.org` (`9a6ec57`).
- [x] **HERMES**: Coil given a descriptive User-Agent, which is what actually made the photographs appear (`19eb5b5`).
- [x] **HERMES**: Plan shell, first slice: a Days and Today switcher under the replan chips, opening on Today only while the trip's own dates contain today (`d24f608`). Options, Changes and Money are deliberately absent until there is something real behind them.
- [x] **HERMES**: The planner asks for the currency and both dates instead of defaulting them: currency chips default to INR, both dates come from a Material date picker, the destination starts empty, and nothing is submitted until the form is complete (`b818298`).
- [x] **HERMES**: Money is honest on the Plan screen: a stop amount prints only in the currency that stop itself declares, and a day total only when every priced stop in that day agrees, so a trip's currency is never stamped onto a number priced in another one (`c6559dd`, `f29026b`).
- [x] **HERMES**: Group write path, all four parts: Add someone (`f327c62`), the join sheet's own cap, pace and interests (`129cc11`), Edit a person with a diff body (`c64274d`), Remove a person with a confirm (`349cc8c`).
- [x] **HERMES**: The build state is drawn inside the Plan screen and the Generating route is deleted (`eda05d4`).
- [x] **HERMES**: The dead routes are gone from `core/navigation/Screen.kt`: `Home`, `Explore`, `Profile` and the duplicate `Itinerary` registration (`93ebbd9`). `Screen.kt` now says a route object exists there only while something navigates to it.
- [x] **HERMES**: Seven-role type scale with zero bare sizes, zero Material styles and zero role-less Text calls anywhere in the app; the last metric overrides went with `7aab7c8` and the stop row figures are on the tabular role (`07e04c1`).
- [x] **HERMES**: Colour with meaning, closed against the strongest claim available: no raw hex, no `MaterialTheme` or `colorScheme` reference outside `core/design`, and no palette hue name outside `Color.kt` in any screen or shared component (`a0c81e4`, `b8763bb`, `03fc15b`, `ff2f816`, `61a661e`, `53ee4a5`, `09a89a1`, `969a737`). One open decision sits beside this one and it is Kevin's: the dark scheme in `Theme.kt` now has no user.
- [x] **HERMES**: One `formatStatedAmount` in `core/design/Components.kt` replaces the three private copies that decided whether a currency symbol may be printed (`6fb8fbb`).
- [x] **HERMES**: No screen puts an exception's own words in front of a person any more, and no screen titles itself with a place the trip does not have: closed on Map (`b0c3c62`), Today (`1b609fa`), Plan (`7743ec1`, `86a6e30`) and the planner (`d0c53b7`).
- [x] **HERMES**: The Trips card counts `trip.travellers` and states the set-up size as a plan only while nobody has joined, so it agrees with the Group tab (`a3c7ac2`).
- [x] **HERMES**: The venue plate, the honest fallback for a venue with no genuine photograph: the venue's own initials and its own category, wired at the Plan stop card, the Today hero and the Today day row (`8e152f4`).
- [x] **HERMES**: The Plan tab's no-trip body no longer names Options, Changes and Money, which was the last place in the app promising a sub-view that does not exist (`6336125`).
- [x] **HERMES**: The money fields read the numbers this app itself prints: all four amount fields (the budget on Plan a trip, the cap on Add someone, the cap on Edit a person and the cap on the join sheet) refused a grouped figure because they filtered input to digits, a dot and a comma (`e97c6f2`).
- [x] **HERMES**: A typed zero in an amount field is a number, and the two faults are stated apart: a field holding no readable number is not the same problem as a number that has to be above zero (`cb8bf1c`).
- [x] **HERMES**: The Group card stops printing the server's own status word, so `Plan version 1 - Not locked - Ready` no longer draws a generation job state as a label (`7c95fcc`).
- [x] **HERMES**: The set-up sentence on Group has a singular branch, so a solo trip says person rather than people (`4ea34fc`).
- [x] **HERMES**: The Today empty state stops claiming today on a day that is not today, matching the Now chip gate already on that screen (`411221a`).
- [x] **HERMES**: A photo source is named only for a host the app can read, so a non-Wikimedia host is no longer labelled OpenStreetMap map data (`39107bd`).
- [x] **HERMES**: The plan wait says so when it stops asking: the poll's budget running out is drawn as a state rather than hidden behind a ring that never stops (`bed409c`).
- [x] **HERMES**: The Group empty state stops claiming the trip is offline, because that screen had not read a cause (`64fd331`).
- [x] **HERMES**: The Group tab draws the app's one shape for a trip that is not on the device, with a Retry, which was the last screen in the app whose failed load offered no way to try again (`9a9e924`).
- [x] **HERMES**: The Trips card draws no dash where the trip has no readable date, and reserves no date column for a date it cannot read (`da27059`).
- [x] **HERMES**: The You screen stops counting saved places nothing can save, a figure that was a constant zero no action could raise (`d2f0eb8`).
- [x] **HERMES**: The Plan day card states only what the day's own data shows: the untotalled note names the reason it actually read, and the swipe hint draws only when there is another day (`59cf1cb`).
- [x] **HERMES**: The create form offers the one interest vocabulary the engine matches on, replacing its own seven word list (`350f565`). That makes the words right, not the field working; see section 4 item 7.
- [x] **HERMES**: This board's own previous update (`43eb48d`), and this rewrite.
- [ ] **AGY**: The interests a traveller picks on the create form are dropped before the planner sees them, see section 4 item 7. Either `createTrip` stores them and the generation requirements carry them into `planItinerary`, or the form says so instead of letting the chips imply the plan will use them.
- [ ] **AGY**: Remove the guest identity and the demo-user read fallback from `firebase-auth.guard.ts`, as Kevin asked. The client half is already done, so the backend is now the only place a device-scoped identity can still be minted. Still live and re-read from outside at 06:05 UTC on 2026-09-24, see section 4.
- [ ] **AGY**: Regenerate or re-verify the Lisbon plan, which still carries no photographs, and decide the fate of the seeded `Destination.imageUrl` Unsplash stand-ins. Last stated by agy on 2026-09-20; not re-checked by HERMES.
- [ ] **AGY**: Land `activity.support` on the wire, which is the only thing between the app and a real per stop support count. A traveller with interests can exist as of `f327c62` and `129cc11`. Re-read at 06:05 UTC on 2026-09-24: still absent on all six activities of the Kyoto trip, while `checks` is populated on all six and every trip on the feed has `travellers: []`.
- [x] **HERMES**: The demo shortcut on the sign-in screen. CLOSED AS NOT EXISTING: `git grep -in demo -- apps/android/app/src/main/java` returns nothing, and `SignInScreen.kt` holds no seeded account, no skip button and no test hook. Do not re-open it without a grep that finds one.
- [x] **HERMES**: One name per colour, and the dead palette rows gone (`36728f4`, `404d6e4`).
- [x] **HERMES**: Every Material surface, plate, arc, rule and ink the app draws is named by the app rather than by the phone's theme: top bars (`e8deabd`), the day picker (`eb7d87e`), a field and a dialog (`e39a817`, `09e597e`), filled and outlined buttons (`8fd7281`, `845b2f2`), the one popup menu (`f32820e`), pull to refresh (`3e49087`), the progress readout (`e06f654`), the day selector's own underline and rule (`9199ebf`).
- [x] **HERMES**: Every control under the 44dp touch floor declares 44dp (`fdd7093`). The travellers field's value line is deliberately the count alone; that is open for Kevin, not a defect.
- [x] **HERMES**: The plan wait draws the server's own progress or nothing, never a guessed 10 percent (`d1ba017`), and the plan failure names no cause with the server's exception string gone (`f58f47f`).
- [x] **HERMES**: The Today hero prints an address only when it is walkable (`8bc62a9`), and the delete path names itself Delete while the overflow icon says More options (`404d6e4`).
- [x] **HERMES**: The dead code class, run four times and each time closed: the dead routes (`93ebbd9`), sixteen dead imports (`9136243`), the dead private helper `dateRangeLine` with its class swept clean at 0 of 53 (`77c1777`), and the zero-caller status badge with all 46 public declarations of `core/design` swept (`553dec0`). `TrippinStamp` has no caller and is KEPT on purpose, because design system section 4 names a stamp for a plan finishing or a lock landing, which is motion still owed.
- [x] **HERMES**: A refused write is not a status code in front of a person (`2dcfe8c`), and the ink on a crimson fill names `OnCrimson` rather than a surface token (`09a89a1`, `b8ff144`).
- [x] **HERMES**: This board's own previous update (`1f6955f`), which named `main` and deleted the demo-shortcut item it now closes as not existing.

---

## 4. Standing items in AGY's lane, so both agents read one list

Each item says how it was last checked and by whom. Nothing here blocks a UI commit.

1. **The demo user is still served to a caller with no credentials.** Re-read from this clone at
   06:05 UTC on 2026-09-24, unchanged from 15:52 UTC on 2026-09-23:
   `GET https://backend-production-011e.up.railway.app/api/v1/home` returns HTTP 200 with
   `user.email = traveler@trippin.ai`, `user.displayName = Alex Rivers`, `totalTripsCount = 6` and
   **five** rows in `recentTrips`, with no `Authorization` header and no session. That is the demo read
   fallback in `firebase-auth.guard.ts`. The Android client signs in properly, so this is a wire
   exposure and not a UI defect. First checked 2026-09-21 21:25 UTC, so it has now been re-read three
   times without changing.
2. **`activity.support` is absent from the wire**, so the per stop support count (`3 of 4 want this`)
   cannot be built honestly. First checked by HERMES on 2026-09-20 12:50 UTC against the Paris trip,
   where all six activities carried no `support` object and the trip had no travellers. Re-read at
   06:05 UTC on 2026-09-24 against the Kyoto trip (`89657715-6c2e-4dad-9bb2-38991c278791`): six
   activities, none carrying a `support` object, `travellers` empty and no `perTravellerCost`, while
   `checks` IS populated on all six. The two fields therefore differ on the same payload, so the
   absence is not a serializer that hides both. A traveller with interests can exist as of `f327c62`
   and `129cc11`, and every trip on the feed has `travellers: []`, so this field is still the only
   thing between the app and a real per stop count.
3. **The Changes sub-view needs the Retrofit call for `GET /api/v1/trips/:id/versions`** in
   `core/network/**`. The route exists and answers (`trips.controller.ts:56`); a restore or undo route
   does not exist anywhere in `apps/backend/src`.
4. **Section 9 item 9, the `Why this` disclosure, cannot be built until it is known whether the engine
   exposes per stop scores** (interest match, budget fit, pace fit). The card already shows the reason
   sentence, the travel leg, the venue source and the weather, so a disclosure built from those four
   repeats the card.
5. **`CreateTripDto` still defaults `currency` to USD, `create-trip.dto.ts` still accepts a `startDate`
   in the past, and one Kyoto `place.rating` still returns 4.5 with no source in the tree.** The
   Android planner refuses a past first day and now asks for the currency, so the app is no longer the
   exposure; the API still is.
6. **Trips stuck in status `GENERATING` with no itinerary need an answer**, because the Plan screen
   draws the stage readout off that status alone (`eda05d4`, bounded by `bed409c`). If such rows exist
   the readout now stops after its own budget instead of spinning forever, but the honest fix is still
   a firmer gate on the client or retiring the stuck status on the server. No trip is known to be in
   that state; all five rows on the feed read `status: READY` at 06:05 UTC on 2026-09-24, and HERMES
   holds no account session, so reads here are ownership scoped.
7. **The interests a traveller picks on the create form are dropped by `createTrip`.** Found by
   HERMES while reading the planner at `350f565`, and every step is checkable in this clone:
   - `apps/backend/src/trips/dto/create-trip.dto.ts:56-59` accepts `interests?: string[]` with no
     vocabulary check.
   - `apps/backend/src/trips/trips.service.ts:143-160` (`createTrip`) writes `userId`,
     `destinationName`, `startDate`, `endDate`, `travelersCount`, `budgetTotal`, `costAssumptionsJson`,
     `currency`, `status`, `pace`, `transportPreference`, `originCity`, `notes` and `heroImageUrl`.
     `interests` is not among them, and `costAssumptionsFrom` takes only the six rate keys.
   - `apps/backend/prisma/schema.prisma:107` already has the column: `interests String[] @default([])`,
     so the storage is there and nothing writes it.
   - `apps/backend/src/trips/trips.service.ts:213-227`, the requirements the generation job builds, has
     no `interests` key either, so even a stored value would not reach `aiPlanner.planItinerary` at
     `:240`. The only readers of `requirements.interests` are the two prompt lines at
     `ai-planner.service.ts:347` and `:526`, and nothing feeds them.
   - `apps/backend/src/trips/trips.service.ts:393-405`, the requirements on `GET /trips/:id`, has no
     `interests` key, so the app cannot read the picks back either.
   Either the storage and both requirements blocks carry the field, in which case nothing in the UI
   moves, or it stays dropped and the interests card says so rather than implying the picks are used.
   Put to agy on the bus at 05:41 UTC on 2026-09-24.
8. **`core/cache/TripCacheManager.kt:3` imports `mutableStateMapOf` and the name appears nowhere in
   that file**, so it is provably dead. `core/cache` is agy's lane, repeated on the last ten bus
   messages. Related, from `d2f0eb8`: `SavedSpotsManager` now has zero callers in the whole app, so
   either a save action gets an owner or the file is dead code, and that call is agy's as well.
9. **The `encodeDefaults` question on `UpdateTravellerRequestDto`**, unchanged and not blocking: with
   `Json { ignoreUnknownKeys = true }` and `encodeDefaults` false, a cap or a pace cannot be cleared
   from the app, and flipping the flag on its own would turn the edit sheet's diff-against-the-server
   body into a data wiper. See section 2 item 6 for the safe shapes.

---

## 5. For Kevin: the one gap this board cannot close

Every HERMES commit from `a23f32f` onward is build verified and grep verified, and the device half of
every commit after `03fc15b` (2026-09-21 02:54 UTC) is outstanding, which is now 60 commits
(`git rev-list --count 03fc15b..HEAD`). `adb devices` listed nothing at 06:04 UTC on 2026-09-24 and
the install step exits 1 with
`device 'emulator-5554' not found`, while the build reported `BUILD SUCCESSFUL` with
`GRADLE_EXIT_CODE=0`. The code compiles clean with zero Kotlin warnings, and each claim is backed by a
grep, a script over the source roots, or a pixel measurement where a screenshot was possible earlier in
the day. What is unread is motion, colour on screen, layout at 390px, and every write path (add, join,
edit, remove a traveller). The cheapest reads when a device is back, in order: the create trip flow end
to end, including whether the interest chips change anything about the plan (section 4 item 7 says
they cannot yet), the Group write path on a real trip, the Map screen with the network off, and the
Plan failure state.
