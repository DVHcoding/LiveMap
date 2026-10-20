# LiveMap

### Real-time location sharing for two Android devices.

LiveMap is an Android app built with Kotlin and Jetpack Compose that lets two people follow each other's location on a map, with background updates, smooth marker interpolation, and device status information.

[Quick start](#quick-start) · [Features](#features) · [Development setup](#development-setup) · [Architecture](#architecture) · [Privacy](#privacy)

---

## Features

- **Live sharing for two people** — pair devices with a room code; each room accepts one device pair.
- **Background location updates** — a foreground service keeps sending locations while the app is not on screen, with the notification Android requires.
- **Smooth map movement** — markers interpolate between updates and rotate to match the direction of travel.
- **Two map providers** — OpenStreetMap is the default, with optional Google Maps support after configuring an API key.
- **Useful status information** — view speed, battery level, charging status, and copy coordinates.
- **Quick controls** — focus the map on your location, your partner's location, or both devices.

## Quick start

### Install the debug APK

Connect an Android device over USB or start an emulator, then run:

```bash
./gradlew installDebug
```

After installation, grant location and notification permissions. To keep sharing reliable while the screen is off, allow the app to run in the background when Android asks.

### Pair two devices

1. Open **Settings** on both devices.
2. Use the same **room code**; you can generate a random code and copy it to send to the other person.
3. Select different roles: **User A** on one device and **User B** on the other.
4. Enter display names, save the settings, and enable location sharing.

## Development setup

### Requirements

- Android Studio or JDK 17.
- Android SDK with `compileSdk 34`.
- An Android device or emulator with Google Play services for location updates.

### Clone and build

```bash
git clone https://github.com/DVHcoding/LiveMap.git
cd LiveMap
./gradlew assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

### Configure Google Maps (optional)

OpenStreetMap works without an API key. To use Google Maps, add your key to `local.properties` at the project root:

```properties
MAPS_API_KEY=your_google_maps_api_key
```

`local.properties` is ignored by Git; never commit API keys.

## Architecture

```text
Fused Location Provider
        │
        ▼
LiveLocationService ── MQTT ── EMQX public broker
        │                            │
        ├── Foreground notification  └── Partner device
        ▼
Jetpack Compose UI
        │
        ├── OpenStreetMap / osmdroid
        └── Google Maps Compose (optional)
```

| Component | Responsibility |
| --- | --- |
| `LiveLocationService` | Obtains locations, maintains the foreground service, and publishes location data. |
| `MqttManager` | Connects to MQTT, synchronizes state, and limits rooms to two devices. |
| `AppPreferences` | Stores room code, display names, roles, device identity, and map preference. |
| Compose UI | Renders the map, markers, dashboard, and settings. |

## App permissions

| Permission | Why it is needed |
| --- | --- |
| Precise and background location | Sends location while sharing is enabled, including when the app is not on screen. |
| Foreground service | Keeps background location sharing running as required by Android. |
| Notifications | Shows the active location-sharing service status. |
| Internet and network state | Connects to MQTT and loads map data. |
| Wake lock / battery optimization | Reduces the risk of the sharing session being stopped by battery-saving modes. |

## Privacy

LiveMap publishes location data over MQTT to a topic derived from the room code. Only share a room code with people you trust, and stop sharing when it is no longer needed.

The project currently uses a public MQTT broker over a `tcp` connection. A room code is not a substitute for authentication or end-to-end encryption. Do not use the default configuration for sensitive data or production deployments; use your own broker with TLS, authentication, and topic-level access controls instead.

## Operational notes

- The location update frequency changes with movement to balance smoothness and battery use.
- Some Android vendors apply aggressive battery optimization; allow LiveMap to run unrestricted if background updates become unreliable.
- Google Maps requires a valid API key and may incur charges under Google Maps Platform policies.

## Technology

- Kotlin, Coroutines, and Jetpack Compose
- Android Foreground Service and Fused Location Provider
- Eclipse Paho MQTT
- osmdroid / OpenStreetMap
- Google Maps SDK and Maps Compose
- DataStore Preferences

## Contributing

Open an issue to report a bug or propose an improvement. For pull requests, clearly describe the change and verify the debug build before submitting:

```bash
./gradlew assembleDebug
```
