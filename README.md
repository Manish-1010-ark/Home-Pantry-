# Home Pantry+ 🏠🍎

A modern Android application for managing household inventory, tracking grocery items, and
organizing shopping needs. Built with **Jetpack Compose** and **Firebase**.

## 🚀 Key Features

* **Real-time Inventory:** Instant synchronization across devices using **Firebase Firestore**.
* **Offline-First:** Works perfectly without internet; syncs automatically when online.
* **Smart Excel Import:** Bulk import items via `.xlsx` with a "Conflict Resolution" system to
  handle duplicates safely.
* **Organized UI:** Categorized lists with sticky headers, powerful search, and sorting (by name,
  quantity, date added).
* **Modern Design:** Material 3 "Berry Fresh" theme.

## 🛠️ Tech Stack

* **Language:** Kotlin
* **UI:** Jetpack Compose (Material 3)
* **Architecture:** MVVM (Model-View-ViewModel)
* **Dependency Injection:** Hilt
* **Backend / Database:** Google Firebase Firestore
* **Excel Parsing:** Apache POI
* **Local Storage:** DataStore & Room

## 🚧 Status

* **Current State:** Database migrated to Firebase; Core inventory features active.
* **Next Steps:** UI polish for Auth screens, Barcode Scanner integration.