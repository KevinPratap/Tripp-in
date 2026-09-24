# Trippin' AI

An Android trip planner built with four intelligent-systems techniques. It orders your day's stops with a **genetic algorithm**, estimates each stop's chance of going wrong with a **Bayesian network**, tracks how tired you are with **fuzzy inference**, and uses **Q-learning** to learn what kind of stop you want next.

Kotlin · Jetpack Compose · MVVM · Room · Retrofit · WorkManager · Firebase Auth. It uses free data sources (OpenStreetMap, Open-Meteo) and needs no API keys.

---

## Run it

1. Open the `TrippinAI` folder in **Android Studio** (Ladybug or newer, JDK 17).
2. Let Gradle sync, then run the **app** configuration on an emulator or phone (Android 8.0 or newer).
3. *(Optional)* To turn on sign-in, create a Firebase project and enable **Email/Password** auth. Download `google-services.json` into `app/`. Without it the app runs in guest mode.
4. To run the AI unit tests: `./gradlew :intelligence:test`

If the network is unavailable, the app falls back to a built-in South Mumbai demo city, so the demo still works offline.

## Project layout

```
TrippinAI/
├── intelligence/        Pure Kotlin module: the four IS-II techniques, no Android code, fully unit-tested
│   ├── bayes/           BayesianNetwork (exact inference by enumeration) + StopRiskModel
│   ├── fuzzy/           MamdaniSystem (membership functions, rules, centroid) + FatigueController
│   ├── genetic/         RouteOptimizer (TSP with time windows)
│   ├── rl/              QLearningRecommender (tabular Q-learning, ε-greedy)
│   ├── model/           Place, Geo (haversine), OsmMapper, SampleData
│   └── TripBrain.kt     Chains all four into a day plan
└── app/                 Android app (MVVM)
    ├── data/local       Room: trips, stops, GA generations, Q-table, feedback
    ├── data/remote      Retrofit: geocoding, forecast, Overpass (OpenStreetMap)
    ├── data/repository  TripRepository, LearningRepository, PreferencesRepository (DataStore)
    ├── data/export      .ics calendar export via FileProvider
    ├── auth/            Firebase Auth + guest mode
    ├── work/            WorkManager daily briefing + notifications
    ├── sensors/         Step counter, fused location
    └── ui/              Compose screens: Auth, Trips, Planner, Plan, Today, AI Lab, You
```

---

## Intelligent Systems II — where each unit is used

| Unit | Technique | In the app | Code |
|---|---|---|---|
| 1 · Uncertain knowledge & reasoning | Bayesian network with exact inference by enumeration. Supports predictive queries (P(Disrupted \| evidence)) and diagnostic ones (P(Rain \| Disrupted)). | Every stop shows a risk pill. **Why?** opens the posterior probabilities. The rain prior comes from the live forecast. | `intelligence/bayes/` |
| 2 · Fuzzy inference systems | Mamdani FIS: triangular and trapezoidal membership functions, 10 rules, min/max operators, centroid defuzzification | Today mode: turns distance walked (from the step sensor), hours out and temperature into a fatigue score and advice | `intelligence/fuzzy/` |
| 3 · Evolutionary intelligence | Genetic algorithm for the TSP with time windows: permutation encoding, tournament selection, OX1 crossover, swap and inversion mutation, elitism | Orders each day's stops; the evolution chart is saved per day | `intelligence/genetic/` |
| 4 · Reinforcement learning | Tabular Q-learning: 9 states × 6 actions, ε-greedy exploration with decay, Bellman update | Today mode suggests the next kind of stop; your Love it / Fine / Skip is the reward. The Q-table is stored in Room and reorders interests for the next plan. | `intelligence/rl/` |

The **AI Lab** tab lets you run each technique live: toggle Bayesian evidence, move the fuzzy inputs, re-run the GA with your own parameters, or train the Q-table on a simulated traveller and watch the heatmap change.

**The Bayesian probabilities are assumptions, not measured data.** The conditional probability tables in `StopRiskModel.kt` were chosen by hand and are written down so you can defend and tune them. Say so in the viva.

### Tests (`intelligence/src/test`)
- **Bayesian network:** the textbook burglary network gives P(B | j, m) ≈ 0.284 (Russell & Norvig). Rain raises risk; estimated hours raise P(Closed).
- **Fuzzy:** membership shapes, fresh and exhausted extremes, and fatigue never falls as distance rises.
- **GA:** crossover and mutation always produce valid permutations. On 7 stops the GA matches the brute-force optimum (5,040 orders). Elitism means the best cost never gets worse. Opening hours are respected.
- **Q-learning:** one update matches the Bellman formula by hand, and the agent learns a simulated traveller's taste.
- **OSM mapper:** parses opening hours, including ones that run past midnight, and marks hours as estimated when none are published.

---

## Mobile Application Development — topics covered

| Topic | Where |
|---|---|
| Activity lifecycle | `MainActivity` logs every callback (filter Logcat by `Lifecycle`) |
| Single-activity architecture, navigation | `ui/navigation/NavHost.kt`: Navigation-Compose, bottom bar, back stack |
| Explicit and implicit intents, deep links | Maps (`geo:`), share chooser, notification → `trippin://trip/{id}`, `onNewIntent` |
| UI with Jetpack Compose | Custom theme and typography (bundled fonts), `LazyColumn`, `FlowRow`, dialogs, bottom sheet, date picker, sliders, switches |
| Custom drawing | `Canvas` charts: GA evolution, fuzzy output, Q-table heatmap |
| MVVM + StateFlow | One `ViewModel` per screen; repositories; manual DI in `AppContainer` |
| SQLite with Room | Entities, relations (`TripWithStops`), foreign keys with cascade, `@Transaction`, `Flow` queries |
| Key-value storage | DataStore Preferences (guest name, briefing toggle) |
| File handling + content provider | `.ics` export written to cache and shared through `FileProvider` |
| Networking (REST/JSON) | Retrofit + OkHttp + kotlinx.serialization, three public APIs |
| Background work | `WorkManager` periodic worker with a network constraint |
| Notifications | Notification channel, `PendingIntent`, runtime `POST_NOTIFICATIONS` |
| Sensors | `TYPE_STEP_COUNTER` through `callbackFlow` |
| Location | Fused Location Provider |
| Runtime permissions | Location, activity recognition, notifications |
| Firebase | Email/password authentication, with guest fallback |
| Coroutines | `viewModelScope`, `Dispatchers.IO/Default`, `Mutex` |
| Testing | JUnit tests for the intelligence module |
| Release | R8 minification + ProGuard rules for serialization |

---

## Viva demo script (about 6 minutes)

1. **Plan a trip:** Mumbai, 2 days, Standard pace. The build screen names each stage and the technique behind it.
2. **Plan screen:** tap **Why?** on a stop to show the Bayesian posteriors and the diagnostic probability. Scroll down to the GA evolution chart.
3. **Today mode:** turn on demo controls and drag *Walked* from 2 km to 12 km. The fuzzy score moves from Fresh to Exhausted and the advice changes. Tap **Skip** on the suggestion to show the Q-learning update.
4. **AI Lab → Genetic:** set mutation to 0 and evolve, then set it to 0.3 and evolve again. Compare the curves.
5. **AI Lab → Q-learn:** train 200 episodes and watch the heatmap fill in.
6. **Mobile App Dev:** export to calendar (FileProvider), share, open in Maps, and show the Logcat lifecycle logs.

## Known limits (say these before the examiner does)
- Travel times use straight-line distance with walking/transit speeds, not live routing.
- Opening hours come from OpenStreetMap. Only simple `HH:MM-HH:MM` values are parsed; anything else is marked estimated.
- The demo city's coordinates and hours are approximate.

Fonts: Big Shoulders Display, Bricolage Grotesque and JetBrains Mono, all under the SIL Open Font License.
