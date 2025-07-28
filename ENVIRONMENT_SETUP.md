# Environment Variables Setup Guide

## Overview

This guide explains how to use environment variables in your GRPest Control Android app. Environment variables are embedded into the APK during build time and can be used to configure different environments (production, development, staging).

## 🔧 How It Works

### 1. **BuildConfig Approach**
Environment variables are embedded into the APK using Android's `BuildConfig` class. This happens during the build process and the values become constants in your app.

### 2. **Configuration Files**
- `gradle.properties` - Contains environment variables
- `app/build.gradle.kts` - Defines build configurations
- `AppConfig.java` - Provides easy access to configuration values

## 📁 File Structure

```
grpc/
├── gradle.properties.template # Template for environment variables
├── gradle.properties         # Environment variables (ignored by git)
├── app/build.gradle.kts      # Build configuration
├── app/src/main/java/com/grpc/grpc/
│   ├── AppConfig.java        # Configuration helper
│   └── UserDatabaseHelper.java # Uses AppConfig
├── build-with-env.bat        # Windows build script
├── build-with-env.sh         # Linux/Mac build script
├── setup-env.bat             # Windows setup script
├── setup-env.sh              # Linux/Mac setup script
└── .gitignore                # Git ignore rules
```

## 🚀 Quick Start

### Option 1: Use Build Scripts (Recommended)

#### Windows:
```batch
# Build production APK
build-with-env.bat -e production -v 1.0.0

# Build development APK
build-with-env.bat -e development -f my-dev-project

# Build staging APK
build-with-env.bat -e staging -v 1.1.0 -f staging-project
```

#### Linux/Mac:
```bash
# Build production APK
./build-with-env.sh -e production -v 1.0.0

# Build development APK
./build-with-env.sh -e development -f my-dev-project

# Build staging APK
./build-with-env.sh -e staging -v 1.1.0 -f staging-project
```

### Option 2: Manual Configuration

1. **Set up environment (first time only):**
```bash
# Windows
setup-env.bat

# Linux/Mac
./setup-env.sh
```

2. **Edit `gradle.properties`:**
```properties
# Firebase Configuration
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_API_KEY=your-api-key
API_BASE_URL=https://your-api-url.com
APP_ENVIRONMENT=production
```

3. **Build the APK:**
```bash
# For release
./gradlew assembleRelease

# For debug
./gradlew assembleDebug
```

## 🔧 Available Environment Variables

### Firebase Configuration
```properties
FIREBASE_PROJECT_ID=grpc-app-12345
FIREBASE_API_KEY=your_firebase_api_key_here
FIREBASE_STORAGE_BUCKET=grpc-app-12345.appspot.com
```

### API Configuration
```properties
API_BASE_URL=https://api.grpcstaff.com
API_VERSION=v1
```

### App Configuration
```properties
APP_ENVIRONMENT=production
APP_VERSION=1.0.0
APP_NAME=GRPest Control
```

### Default User Credentials (Offline Mode)
```properties
DEFAULT_USER_EMAIL=admin@grpc.com
DEFAULT_USER_PASSWORD=admin123
```

### Database Configuration
```properties
DATABASE_NAME=UserDatabase
DATABASE_VERSION=1
```

### Security Configuration
```properties
PASSWORD_HASH_ALGORITHM=SHA-256
ENCRYPTION_ENABLED=true
```

## 💻 Using Environment Variables in Code

### Access Configuration Values

```java
// Get Firebase project ID
String projectId = AppConfig.FIREBASE_PROJECT_ID;

// Check environment
if (AppConfig.isProduction()) {
    // Production-specific code
}

// Get API URL
String apiUrl = AppConfig.getApiUrl();

// Log configuration
AppConfig.logConfiguration();
```

### Example Usage in Activities

```java
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Log configuration on startup
        AppConfig.logConfiguration();
        
        // Use environment-specific settings
        if (AppConfig.isDevelopment()) {
            // Enable debug features
            enableDebugMode();
        }
    }
}
```

## 🔄 Environment-Specific Builds

### Production Build
```batch
build-with-env.bat -e production -v 1.0.0 -f grpc-production
```

**Features:**
- Release APK
- Production Firebase project
- Production API endpoints
- Optimized for performance
- Debug mode disabled

### Development Build
```batch
build-with-env.bat -e development -f grpc-development
```

**Features:**
- Debug APK
- Development Firebase project
- Development API endpoints
- Debug mode enabled
- Additional logging

### Staging Build
```batch
build-with-env.bat -e staging -v 1.1.0 -f grpc-staging
```

**Features:**
- Release APK
- Staging Firebase project
- Staging API endpoints
- Production-like testing

## 🔐 Security Considerations

### Sensitive Data
- **API Keys**: Store in `gradle.properties` (not in version control)
- **Passwords**: Use hashed values in production
- **Firebase Config**: Keep project IDs separate for each environment

### Git Ignore Configuration
The `.gitignore` file is configured to prevent sensitive files from being committed:

```gitignore
# Environment Variables and Configuration
.env
.env.*
gradle.properties
local.gradle.properties
production.gradle.properties
development.gradle.properties
staging.gradle.properties
*.env
config.properties
secrets.properties

# Keystore and signing files
*.keystore
*.jks
*.pem
*.p12
*.key
*.crt
*.pfx
signing.properties
keystore.properties

# Database files
*.db
*.sqlite
*.sqlite3
UserDatabase
UserDatabase.*
```

### Best Practices
1. **Never commit sensitive data** to version control
2. **Use different Firebase projects** for each environment
3. **Rotate API keys** regularly
4. **Use environment-specific** configuration files
5. **Use the template file** (`gradle.properties.template`) as a starting point
6. **Run setup scripts** to configure your environment safely

## 📱 Testing Different Environments

### 1. Install Multiple APKs
```bash
# Install production APK
adb install app/build/outputs/apk/release/app-release.apk

# Install development APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Verify Configuration
```java
// In your app, check the configuration
Log.d("AppConfig", AppConfig.getConfigurationSummary());
```

## 🛠️ Troubleshooting

### Common Issues

1. **BuildConfig not found**
   - Clean and rebuild: `./gradlew clean build`
   - Check `build.gradle.kts` configuration

2. **Environment variables not updating**
   - Clean project: `./gradlew clean`
   - Rebuild: `./gradlew assembleRelease`

3. **Wrong environment detected**
   - Check `gradle.properties` values
   - Verify build script parameters

### Debug Commands

```bash
# Check current configuration
cat gradle.properties

# Clean and rebuild
./gradlew clean assembleRelease

# Check APK contents
aapt dump badging app-release.apk
```

## 📋 Environment Checklist

### Before Building Production APK
- [ ] Firebase project ID set correctly
- [ ] API endpoints point to production
- [ ] Debug mode disabled
- [ ] Version number updated
- [ ] Default credentials configured
- [ ] Security settings enabled

### Before Building Development APK
- [ ] Firebase project ID set to development
- [ ] API endpoints point to development
- [ ] Debug mode enabled
- [ ] Logging level set to DEBUG
- [ ] Test credentials configured

## 🎯 Advanced Configuration

### Custom Environment Variables

Add new variables to `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "CUSTOM_VARIABLE", "\"${project.findProperty("CUSTOM_VARIABLE") ?: "default"}\"")
```

Then use in `AppConfig.java`:

```java
public static final String CUSTOM_VARIABLE = BuildConfig.CUSTOM_VARIABLE;
```

### Conditional Configuration

```java
if (AppConfig.isProduction()) {
    // Production-specific configuration
    enableProductionFeatures();
} else if (AppConfig.isDevelopment()) {
    // Development-specific configuration
    enableDebugFeatures();
}
```

## 📞 Support

For issues with environment configuration:
1. Check the build logs
2. Verify `gradle.properties` values
3. Clean and rebuild the project
4. Check `AppConfig.java` for correct variable names

---

**Note**: Environment variables are embedded at build time and cannot be changed after the APK is created. To change configuration, you must rebuild the APK. 