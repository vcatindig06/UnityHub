# UnityHub

UnityHub is a native Android application for managing a church worship team, its song library, and worship service planning.

## Planned technology

- Kotlin and Jetpack Compose
- Minimum Android version: Android 8.0 (API 26)
- Supabase for authentication, database, and private file storage

## Core features

- Account access and organization membership
- Team roles and membership approval
- Song library with lyrics, chords, ChordPro, and transposition
- Worship service plans, setlists, and assignments
- Team communication, shared resources, and notifications
- Offline access to cached song charts

## Security requirements

UnityHub will use the existing WorshipFlow Supabase project and preserve its row-level security policies and authorization model. The Android client must use only the Supabase publishable key. Never include a Supabase service-role or secret key in the Android app. Organization membership and permissions must be verified by the backend, not trusted from client-supplied values.

## Development status

This repository is the home for the native Android implementation. Features and build configuration will be added incrementally and verified against the Code on the Go Android IDE environment.