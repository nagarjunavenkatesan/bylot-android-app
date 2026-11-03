# Bylot Android

The Android app displays the **actual Bylot React website UI**, built from the source project and packaged inside the APK. The app uses a native Kotlin Android shell and Android WebView to render the same React components, responsive CSS, Framer Motion animations, navigation, and website routes. This avoids maintaining a second, visually different implementation.

The website's static files are bundled in `app/src/main/assets/site`. They load locally; requests to `/api` and `/uploads` continue to go to the Bylot backend on `bylot.in`.

## Refresh the bundled website

After changing the website source, rebuild and copy its production assets into the Android app:

```powershell
Set-Location 'C:\path\to\bylot-android-app'
.\sync-web-ui.ps1
```

By default the script looks for the Bylot web source in a sibling `Bylot-main\Bylot-main\bylot` folder. If the web source is elsewhere, pass its path:

```powershell
.\sync-web-ui.ps1 -WebProjectPath 'C:\path\to\Bylot\bylot'
```

The script sets Vite's base path to `/assets/site/`, so the website routes and static resources work inside the app's local Bylot origin.

## Build and run

Open `C:\Users\HP\Music\and bylot` in Android Studio, sync Gradle, and run the `app` configuration on an emulator or device. The debug APK can also be built with:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

## Backend and device permissions

The app must be online to load live listings, seller images, sign-in, and other backend-powered features. Those requests use the same `https://bylot.in` origin as the website; the production backend must be reachable from the device. The bundled website layout and screens remain available offline, while API-dependent sections show the website's normal connection/error state.

The app requests location access only when the website asks to use GPS. Image selection uses the native Android file picker. Bylot does not process online payments.

## Android security and performance

- Cleartext traffic is disabled in both debug and release builds. The WebView also rejects mixed content and cancels TLS certificate errors.
- The bundled page is served with a restrictive Content Security Policy. File URL access, third-party cookies, automatic pop-ups, and camera/microphone permissions are disabled.
- Only HTTPS links from Bylot remain in the app; external HTTPS, email, and phone links are handed to Android. Location prompts are accepted only for the Bylot origin.
- App data is excluded from cloud backup and device-to-device transfer. Saved navigation state contains only the local route path, not query strings.
- Fingerprinted Vite assets are cached as immutable resources; the HTML entry point is not cached so app updates take effect immediately.
- WebView remote debugging is not enabled by the app.

These are client-side protections; authentication, authorization, secure cookies, rate limiting, and server-side input validation must also be enforced by the Bylot backend.
