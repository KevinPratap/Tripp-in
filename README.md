# Trippin' AI

A group trip planner for Android that helps friends align on travel plans through interactive voting. Travel companions create a trip, suggest places to visit, and swipe on destination cards to build consensus. The highest-voted stops are turned into a day-by-day itinerary with shared expense tracking and live updates.

## What you can do

- **Swipe to vote.** Swipe right to like a place, left to pass, up for a must. The app shows where the group agrees.
- **Ideas and votes.** Anyone can suggest a destination or a place. Votes are counted per person.
- **Plan.** Turn the favourite places into a route for each day.
- **Budget.** Record expenses and split them between the people on the trip.
- **People.** Invite friends with a code or a link.
- **Today.** See what is happening now while you travel.

## How it is built

- Kotlin and Jetpack Compose (Material 3), minimum Android 8.0 (API 26).
- Room for data stored on the phone.
- Firebase sign-in and Firestore for sharing a trip with friends. Without a Firebase file the app still runs in an on-device mode.
- Foursquare Places for place search and photos. Without a key the app falls back to free OpenStreetMap search.
- A separate pure-Kotlin module, `intelligence/`, holds the planning logic: group voting and consensus, expense splitting, route ordering with a genetic algorithm, a fuzzy fatigue model, a Bayesian risk model and a Q-learning recommender.

## Run it

1. Install Android Studio and open this folder. Use JDK 17.
2. Optional: put your `google-services.json` in `app/` to turn on Firebase.
3. Optional: add `FOURSQUARE_API_KEY=your_key` to `local.properties` to turn on Foursquare.
4. Press Run.

Neither optional file is ever committed.

## Check your work

```bash
./gradlew :intelligence:test
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

## Folders

| Folder | What is in it |
| --- | --- |
| `app/` | The Android app |
| `app/src/main/java/com/trippin/ai/ui/` | Screens, theme, brand art and icons |
| `app/src/main/java/com/trippin/ai/data/` | Storage, Firebase, network and repositories |
| `intelligence/` | Planning logic, with unit tests |
| `apps/` | Earlier web and backend prototypes. Gradle does not build them |

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before you open a pull request.
