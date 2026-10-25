# GameReview Android App

This is the Android client for the GameReview API. It lets users sign in with Auth0, browse games, read reviews, write reviews, and use admin actions when signed in as an administrator.

## Tech used

- Kotlin
- Jetpack Compose
- Auth0
- Android Studio

## Run locally

1. Open this project in Android Studio.
2. Start an Android emulator or connect an Android device.
3. Run the `app` configuration.

The app is already configured to use the deployed API:

```text
https://gamereview-api-gqbj.onrender.com
```

The emulator or device needs internet access. The first request may take a little longer if the Render service is waking up.

## Main screens

- Sign in
- Games list
- Game details and reviews
- My reviews
- Admin user and game actions for admin accounts

## Build a debug APK

From the project folder, run:

```bash
./gradlew assembleDebug
```

The APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Run tests

```bash
./gradlew test
```

Instrumented Android tests can be run from Android Studio or with:

```bash
./gradlew connectedAndroidTest
```
