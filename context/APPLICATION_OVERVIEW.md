# SHIELD Android Safety & Incident Documentation Application

## Project Purpose

SHIELD is a privacy-focused Android application designed to help users record incidents, alert trusted contacts during emergencies, and securely preserve evidence records. The application prioritizes:

- User safety
- Privacy-first design
- Offline reliability
- Secure encrypted data storage
- End-to-end encrypted cloud backup

The app allows users to:

- Send emergency alerts with GPS location
- Log private incident reports
- Record voice notes as evidence
- Manage a trusted support circle
- Maintain encrypted personal records
- Automatically back up encrypted data to Google Drive

The design, interface, and navigation are based directly on the prototype interface.

---

## Core Development Goal

The goal of this repository is to guide an AI or developer to generate a complete Android Kotlin application that reproduces the prototype functionality and interface behavior. The generated application must:

- Follow the UI layout and flow defined in the demo
- Maintain the same visual theme and color scheme
- Implement the described data structures and services
- Provide secure local storage and encrypted cloud backup

---

## Platform and Technology Requirements

The application should be generated using the following technologies:

| Layer | Technology |
|-------|-----------|
| Programming Language | Kotlin |
| Platform | Android |
| UI Framework | Android XML layouts or Jetpack Compose |
| Architecture | MVVM (Model-View-ViewModel) |
| Local Database | Room (SQLite) |
| Encryption | AES-256 client-side encryption |
| Cloud Backup | Google Drive API |
| Location Services | Android Location Services |
| Messaging | Android SMS Services |
| Audio Recording | Android Media Recording APIs |

The system should operate offline first, with optional cloud synchronization.

---

## Application Architecture

The application should follow a modular MVVM architecture.

### Architecture Layers

**Presentation Layer**
- Activities or Compose Screens
- ViewModels
- UI state management

**Domain Layer**
- Business logic
- Incident management
- Contact management
- Emergency alert logic

**Data Layer**
- Local database
- Encryption service
- Google Drive backup service

**Service Layer**
- Location service
- SMS service
- Audio recording service
- Encryption utilities
- Cloud backup manager

This separation allows the application to be maintainable and scalable.

---

## Application Screens

The application must include the following primary screens.

### 1. Home Dashboard

The Home screen acts as the main control center. It contains:

- Welcome header displaying the user name
- Profile avatar circle
- Quick action grid
- Shield protection toggle
- Bottom navigation bar

**Quick Actions**

Four main actions must be visible:

1. Emergency Alert
2. Log Incident
3. My Circle
4. Records

Each quick action opens its corresponding screen. The shield protection card indicates that:

- encryption is active
- trusted contacts are configured
- emergency alerts are ready

---

## Emergency Alert System

### Purpose

The emergency alert screen allows the user to quickly notify trusted contacts.

### Behavior

When the user activates the alert:

1. The app retrieves the current GPS location.
2. A timestamp is generated.
3. An SMS alert message is created.
4. The message is sent to all contacts in the support circle.

### Alert Information

Each alert must contain:

- GPS coordinates
- Time of alert
- Predefined emergency message

The alert system must function without requiring internet access.

---

## Incident Logging

The incident logging screen allows users to privately document events.

### Features

The screen should allow the user to:

- Record a voice note
- Enter a written description
- Select an incident type
- Choose a severity level
- Automatically attach GPS location
- Save the entry to a private journal

### Incident Types

Selectable categories should include:

- Physical
- Verbal
- Financial
- Sexual
- Neglect

### Severity Levels

The severity indicator must support:

- Low
- Medium
- High
- Crisis

Each incident entry becomes a secure record stored locally.

---

## Voice Evidence Recording

The logging screen includes the ability to record voice evidence.

### Behavior

- User taps a record button
- Recording begins
- Duration is displayed
- User taps again to stop

Voice files are stored locally and associated with the incident entry. The recordings must be treated as private encrypted evidence.

---

## Support Circle

The Support Circle screen allows the user to manage trusted contacts.

Each contact entry should include:

- Full name
- Phone number
- Relationship type

Examples of relationships include:

- Family
- Friend
- Counselor
- Doctor
- Legal Aid

### Contact Settings

Each contact must allow the user to enable:

- Emergency SMS alerts
- GPS inclusion in alerts

Contacts should only be notified when an emergency alert is triggered.

---

## Private Records

The records screen displays a chronological list of all logged incidents.

Each record should show:

- Incident title
- Description
- Timestamp
- Severity indicator
- GPS indicator
- Voice note indicator (if attached)

Records must be stored in a private encrypted journal.

---

## Legal Evidence Export

The application should support exporting incident records. Exported records must include:

- timestamps
- GPS coordinates
- severity classification
- recorded voice notes
- descriptive text

The exported data should be formatted as a secure encrypted report suitable for legal documentation.

---

## Local Data Storage

All incident data must first be stored locally. The database should store:

- incident records
- voice file references
- trusted contacts
- alert history

All stored information must be encrypted before being written to the database.

---

## End-to-End Encrypted Cloud Backup

To protect against device loss, the application must support secure Google Drive backups.

### Backup Behavior

When a record is created or updated:

1. Data is serialized into a structured format.
2. The data is encrypted locally using AES-256.
3. The encrypted file is uploaded to Google Drive.

### Privacy Requirement

The encryption must occur before uploading the data. This ensures:

- Google Drive cannot read the content
- Only the user's device can decrypt the data

---

## Backup Data Contents

The encrypted backup must include:

- Incident records
- Voice evidence
- Trusted contacts
- Alert history
- Metadata timestamps

---

## Backup Recovery

When the user installs the app on a new device:

1. The user authenticates with Google.
2. The encrypted backup file is downloaded.
3. The file is decrypted locally.
4. All records and contacts are restored.

---

## Navigation Structure

The application must include a bottom navigation bar. Navigation tabs include:

- Home
- Log
- Circle
- Records

Emergency alerts should remain accessible from the Home quick actions panel.

---

## User Interface Design Requirements

The generated UI must match the dark safety-oriented design style used in the prototype.

### Design Principles

- Discreet appearance
- High readability
- Minimal clutter
- Strong contrast
- Calm blue safety palette
- Red reserved only for emergency actions

---

## Color Scheme (Must Be Maintained)

The application must strictly maintain the following color palette.

| Purpose | Color | Hex |
|---------|-------|-----|
| Primary Background | Dark Navy | #0a0e14 |
| Secondary Background | Deep Blue | #0e1520 |
| Card Background | Steel Blue | #111c28 |
| Primary Accent | Shield Blue | #1a4a7a |
| Highlight Accent | Alert Blue | #4a9ade |
| Neutral Text | Muted Steel | #6a8aaa |
| Emergency Elements | Warning Red | #cc4444 |
| Border Elements | Dark Border | #1e2e42 |

The color scheme should be consistently applied across:

- buttons
- cards
- navigation
- toggles
- icons
- text

---

## Security Principles

The system must follow privacy-first architecture. Key rules include:

- All sensitive data encrypted locally
- Cloud backups encrypted before upload
- No plaintext storage of incident records
- Emergency alerts require minimal permissions
- No third-party access to personal records

---

## Minimum Android Requirements

The generated application should support:

- Android 8.0 or higher
- API Level 26+

---

## Intended Outcome

The generated application should function as a secure personal safety and documentation tool that allows users to:

- record incidents
- preserve evidence
- contact trusted people during emergencies
- securely back up their data

