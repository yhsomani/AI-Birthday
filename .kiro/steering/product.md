# RelateAI Product Overview

RelateAI is an Android application that helps users maintain and nurture relationships by automating birthday/celebration messages through WhatsApp and email. The app integrates with Google Contacts to identify important dates, uses Firebase for authentication, and leverages Google Gemini AI to generate personalized messages.

## Core Features

- **Contact Management**: Import contacts from Google, track birthdays and special events
- **Relationship Health Scoring**: Automated scoring based on interaction frequency, recency, and consistency
- **AI-Powered Messaging**: Generate personalized WhatsApp and email messages using Gemini AI
- **Automation**: Schedule and send messages with customizable timing and tone
- **Security**: Biometric lock, encrypted local storage with SQLCipher
- **Widget Support**: Birthday widget for quick access to upcoming events

## Tech Stack Highlights

- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: Clean Architecture (Domain → Data → UI layers)
- **Dependency Injection**: Hilt (Dagger Hilt)
- **Database**: Room Database with SQLCipher encryption
- **Authentication**: Firebase Authentication (Google Sign-In)
- **AI Integration**: Google Firebase Vertex AI (Gemini models)
- **Background Work**: WorkManager for scheduled tasks
- **Testing**: JUnit, Robolectric, Roborazzi for UI testing

## User Base

The app targets Android users (API 24+) who want to maintain relationships across family, friends, colleagues, and clients. Users are often professionals who value automation and personalization in their social interactions.
