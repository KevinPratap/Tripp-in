# Contributing to Trippin' AI

Thanks for helping. This page tells you how to send a change.

## Before you start

Install Android Studio (JDK 17) and open the project folder. The steps are in the README under "Run it". You do not need any secret files to build or test.

## Send a change

```bash
git switch develop
git pull
git switch -c feat/short-description

# make your change, then:
git status
git add path/to/file1 path/to/file2
git commit -m "feat(android): short description"
git push -u origin feat/short-description
```

Then open a pull request on GitHub. Set the base branch to `develop`. Kevin decides when `develop` is merged into `main`.

- Branch names start with `feat/`, `fix/`, `docs/` or `chore/`.
- Commit messages look like `type(scope): summary`, for example `fix(android): stop the card jumping on undo`. Keep them under 72 characters.
- One change per pull request. Aim for under 300 changed lines.

## Before you open the pull request

- Run `./gradlew :app:compileDebugKotlin`. It must pass.
- If you changed `intelligence/`, run `./gradlew :intelligence:test`. It must pass.
- Run the app and try your change. For any change you can see, attach a screenshot or a short recording.

## Rules

- Add files by name. Never use `git add .` or `git add -A`.
- Never force-push.
- No emojis in code, comments or any text shown in the app.
- No long dashes in text shown in the app. Use a plain hyphen or the word "to".
- Icons are vector icons from `TripIcons`. Colours come from `Trip` in `ui/theme/Theme.kt`.
- Never invent data. No made-up ratings, reviews or opening hours, and no stock photo shown as a venue's own photo.
- Never commit secrets: `local.properties`, `google-services.json` or keystores. If `git status` lists one, stop and ask.
- Match the style around you: four spaces, no unused imports, and import names instead of writing them out in full inside the code.

## Review

Someone other than the author approves every pull request, and the "Android build and tests" check must be green. Answer every comment, and push fixes to the same branch: the pull request updates by itself.
