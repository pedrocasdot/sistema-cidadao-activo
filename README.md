# Sistema Cidadão Ativo (SCA) - Active Citizen System

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%2FJava-blue.svg)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Backend-Spring%20Boot-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A comprehensive mobile application for citizen engagement and incident reporting, featuring offline-first architecture, peer-to-peer communication, and real-time synchronization.

## 🌟 Features

### Core Functionality
- **📱 Incident Reporting**: Report incidents with photos, videos, GPS location, and detailed descriptions
- **🗺️ Interactive Maps**: View incidents on Google Maps with real-time location tracking
- **👥 User Management**: Secure authentication with JWT tokens and user profiles
- **🔄 Offline-First**: Work seamlessly without internet connection with automatic sync when online
- **📡 P2P Communication**: Share incidents directly between devices using Wi-Fi Direct
- **🔐 End-to-End Encryption**: Secure P2P communication with AES-256-GCM encryption

### Advanced Features
- **⚡ Background Sync**: Automatic synchronization using WorkManager
- **📊 Real-time Updates**: Live incident tracking and status updates
- **🎯 Location Services**: Precise GPS coordinates and symbolic location descriptions
- **📷 Media Support**: Photo and video capture with automatic compression
- **🔒 Security**: Robust encryption for data transmission and storage

## 🏗️ Architecture

### Mobile Application (Android)
- **Language**: Java 11 + Kotlin
- **Architecture**: Offline-First with Room Database
- **UI Framework**: Android Views with Material Design
- **Database**: Room (SQLite) with automatic migrations
- **Networking**: Retrofit 2.11.0 with OkHttp interceptors
- **Background Processing**: WorkManager 2.9.0
- **Maps**: Google Maps API 18.2.0
- **P2P Communication**: Termite Wi-Fi Direct API

### Backend API (Spring Boot)
- **Language**: Kotlin
- **Framework**: Spring Boot 3.4.2
- **Database**: PostgreSQL with JPA/Hibernate
- **Security**: JWT Authentication
- **API**: RESTful endpoints with JSON responses

### Key Components

#### Android App Structure
```
app/
├── src/main/java/ao/co/isptec/aplm/sca/
│   ├── activities/          # Main application screens
│   │   ├── MainActivity.java
│   │   ├── Login.java
│   │   ├── RegistarOcorrencia.java
│   │   ├── TelaMapaOcorrencias.java
│   │   ├── ListarOcorrencias.java
│   │   └── VisualizarOcorrencia.java
│   ├── base/               # Base classes and utilities
│   │   └── BaseP2PActivity.java
│   ├── database/           # Room database components
│   │   ├── SCADatabase.kt
│   │   ├── OcorrenciaEntity.kt
│   │   ├── OcorrenciaDao.kt
│   │   └── OcorrenciaRepository.kt
│   ├── models/             # Data models
│   │   └── Ocorrencia.java
│   ├── network/            # API and networking
│   │   ├── ApiService.java
│   │   └── responses/
│   ├── p2p/               # P2P communication
│   │   ├── P2PCommManager.java
│   │   └── SimWifiP2pBroadcastReceiver.java
│   ├── service/           # Background services
│   │   ├── SyncWorker.kt
│   │   └── SyncManager.kt
│   └── utils/             # Utility classes
│       ├── CryptoUtils.java
│       ├── SessionManager.java
│       └── OfflineFirstHelper.kt
```

#### Backend API Structure
```
api/
├── src/main/kotlin/ao/co/isptec/aplm/cidadaoactivo/
│   ├── controller/         # REST controllers
│   │   ├── AuthController.kt
│   │   └── IncidentController.kt
│   ├── model/             # JPA entities
│   │   ├── User.kt
│   │   └── Incident.kt
│   ├── service/           # Business logic
│   │   ├── UserService.kt
│   │   └── IncidentService.kt
│   └── repository/        # Data access layer
│       ├── UserRepository.kt
│       └── IncidentRepository.kt
```

## 🚀 Getting Started

### Prerequisites

#### For Android Development
- **Android Studio** Arctic Fox or later
- **JDK 11** or higher
- **Android SDK** API level 24+ (Android 7.0)
- **Google Maps API Key** (for maps functionality)

#### For Backend Development
- **JDK 21** or higher
- **PostgreSQL** 12+ (or H2 for development)
- **Gradle** 7.0+ (included in wrapper)

### Installation

#### 1. Clone the Repository
```bash
git clone https://github.com/your-username/sistema-cidadao-activo.git
cd sistema-cidadao-activo
```

#### 2. Backend Setup
```bash
cd api

# Configure database in application.properties
# For PostgreSQL:
spring.datasource.url=jdbc:postgresql://localhost:5432/sca_db
spring.datasource.username=your_username
spring.datasource.password=your_password

# Run the backend
./gradlew bootRun
```

The backend will start on `http://localhost:8080`

#### 3. Android App Setup
```bash
cd app

# Add your Google Maps API key to local.properties
echo "MAPS_API_KEY=your_google_maps_api_key" >> local.properties

# Update API base URL in ApiService.java if needed
# Default: http://10.0.2.2:8080 (for Android emulator)
```

#### 4. Build and Run
1. Open the project in Android Studio
2. Sync Gradle files
3. Run the app on device or emulator

## 📱 Usage

### Basic Workflow

1. **Registration/Login**
   - Create account or login with existing credentials
   - Automatic data synchronization after login

2. **Report Incident**
   - Tap "Register Incident" button
   - Fill in description and location details
   - Capture photos/videos (optional)
   - Submit - works offline, syncs when online

3. **View Incidents**
   - **Map View**: See all incidents on interactive map
   - **List View**: Browse incidents in chronological order
   - **Detail View**: View complete incident information

4. **P2P Sharing**
   - Enable Wi-Fi Direct in device settings
   - Open incident details
   - Tap "Share" to send to nearby devices
   - Encrypted transmission with password protection

### Key Features Explained

#### Offline-First Architecture
- All data is stored locally using Room database
- App works completely offline
- Automatic sync when internet connection is available
- Background sync using WorkManager

#### P2P Communication
- Uses Termite Wi-Fi Direct API for device discovery
- AES-256-GCM encryption for secure data transmission
- Password-protected sharing
- Works without internet connection

#### Security Features
- JWT token authentication
- Encrypted local storage
- Secure API communication (HTTPS)
- End-to-end encryption for P2P

## 🔧 Configuration

### Android App Configuration

#### API Configuration (`ApiService.java`)
```java
private static final String BASE_URL = "http://your-server:8080/";
```

#### Maps Configuration
Add Google Maps API key to `local.properties`:
```properties
MAPS_API_KEY=your_google_maps_api_key_here
```

### Backend Configuration

#### Database Configuration (`application.properties`)
```properties
# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/sca_db
spring.datasource.username=sca_user
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update

# H2 (Development)
spring.datasource.url=jdbc:h2:mem:testdb
spring.h2.console.enabled=true
```

## 🧪 Testing

### Running Tests

#### Android Tests
```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

#### Backend Tests
```bash
cd api
./gradlew test
```

### Testing P2P Functionality
1. Install app on two devices
2. Enable Wi-Fi Direct on both devices
3. Create incident on device A
4. Share incident from device A
5. Verify receipt on device B

## 📦 Dependencies

### Android Dependencies
- **AndroidX**: Core Android libraries
- **Room**: 2.6.1 - Local database
- **Retrofit**: 2.11.0 - HTTP client
- **WorkManager**: 2.9.0 - Background processing
- **Google Play Services**: Maps and Location
- **Termite Wi-Fi P2P**: P2P communication
- **Picasso**: Image loading
- **Gson**: JSON serialization

### Backend Dependencies
- **Spring Boot**: 3.4.2 - Application framework
- **Spring Data JPA**: Database access
- **PostgreSQL**: Production database
- **H2**: Development database
- **Jackson**: JSON processing
- **Kotlin**: Primary language

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

### Development Guidelines
- Follow Android development best practices
- Use meaningful commit messages
- Add tests for new features
- Update documentation as needed
- Ensure offline functionality works

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

##  Acknowledgments

- **Termite Project** - Wi-Fi Direct API
- **Google** - Maps and Location services
- **Spring Boot** - Backend framework
- **Android Jetpack** - Modern Android development


