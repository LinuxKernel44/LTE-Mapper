# LTE Mapper

LTE Mapper is an Android app that maps cellular signal strength (2G/3G/LTE/5G) against your real-world location, so you can see coverage quality as you move rather than a single point reading.

It continuously samples the serving cell's signal metrics and pairs each reading with a GPS fix, then plots the results on an offline OpenStreetMap layer. Mapping keeps running in the background via a foreground service, even with the screen off, so you can log coverage on a drive or a walk without keeping the app open.

## Features

- **Live signal mapping** — serving-cell signal readings (2G/3G/LTE/5G) are logged alongside GPS coordinates and rendered on a map in real time.
- **Background mapping** — a foreground service keeps sampling and logging while the app is backgrounded or the screen is off.
- **Offline map tiles** — map tiles can be downloaded ahead of time via WorkManager so mapping works without a live connection.
- **History & statistics** — past sessions are stored locally (Room) and can be browsed and reviewed.
- **Import / export** — sessions can be exported to CSV, GeoJSON, and KML, and CSV data can be imported back in.
- **Battery-aware** — the app can request exemption from Doze/App Standby, only ever via an explicit user action, so long background sessions aren't killed by the OS.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Hilt (dependency injection)
- Room (local persistence) + Paging
- WorkManager (background offline tile downloads)
- osmdroid (OpenStreetMap rendering)
- Google Play Services (location)

## Requirements

- Android Studio (recent stable)
- JDK 17
- Android SDK, `compileSdk` 35 / `minSdk` 29

## Getting started

```bash
git clone https://github.com/<your-org>/LTE-Mapper.git
cd LTE-Mapper
./gradlew assembleDebug
```

Open the project in Android Studio, or install the debug build directly:

```bash
./gradlew installDebug
```

### Release builds

Release builds are signed using a keystore that is **not** part of this repository. To produce a signed release build locally, create `keystore.properties` at the project root:

```properties
storeFile=keystore/your-release-key.jks
storePassword=***
keyAlias=***
keyPassword=***
```

If `keystore.properties` is absent, `assembleRelease` still works and simply produces an unsigned build — this file and the keystore itself must never be committed (see `.gitignore`).

## Project structure

```
app/src/main/java/com/ltemapper/app/
├── data/         # Room database, DAOs, entities, repositories
├── domain/       # Use cases and domain models
├── location/     # GPS/location handling
├── mapping/      # Map rendering and geocoding
├── telephony/    # Serving-cell signal reading
├── service/      # Foreground mapping service
├── work/         # WorkManager background jobs (offline tiles)
├── export/       # CSV / GeoJSON / KML import & export
└── ui/           # Jetpack Compose screens and components
```

## Permissions

The app requests location (fine/coarse, plus optional background), phone state (to read serving-cell signal info), and notification permissions for the foreground mapping service. Background location and battery-optimization exemption are only ever requested through an explicit user action, never on launch.

## License

No license has been chosen yet — all rights reserved by default.
