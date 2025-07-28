@echo off
REM ============================================================================
REM GRPest Control App - Build Script with Environment Variables (Windows)
REM ============================================================================
REM This script builds the APK with different environment configurations
REM ============================================================================

setlocal enabledelayedexpansion

REM Default values
set ENVIRONMENT=production
set VERSION=1.0.0
set FIREBASE_PROJECT_ID=grpc-app-12345
set FIREBASE_API_KEY=
set API_BASE_URL=https://api.grpcstaff.com

REM Parse command line arguments
:parse_args
if "%1"=="" goto :end_parse
if "%1"=="-e" (
    set ENVIRONMENT=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="--environment" (
    set ENVIRONMENT=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="-v" (
    set VERSION=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="--version" (
    set VERSION=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="-f" (
    set FIREBASE_PROJECT_ID=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="--firebase-id" (
    set FIREBASE_PROJECT_ID=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="-k" (
    set FIREBASE_API_KEY=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="--api-key" (
    set FIREBASE_API_KEY=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="-u" (
    set API_BASE_URL=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="--api-url" (
    set API_BASE_URL=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="-h" (
    goto :show_help
)
if "%1"=="--help" (
    goto :show_help
)
echo [ERROR] Unknown option: %1
goto :show_help

:show_help
echo Usage: %0 [OPTIONS]
echo.
echo Options:
echo   -e, --environment ENV    Build environment (production^|development^|staging)
echo   -v, --version VERSION    App version (e.g., 1.0.0)
echo   -f, --firebase-id ID     Firebase Project ID
echo   -k, --api-key KEY        Firebase API Key
echo   -u, --api-url URL        API Base URL
echo   -h, --help               Show this help message
echo.
echo Examples:
echo   %0 -e production -v 1.0.0
echo   %0 -e development -f my-dev-project
echo   %0 --environment staging --version 1.1.0 --firebase-id staging-project
exit /b 1

:end_parse

REM Validate environment
if not "%ENVIRONMENT%"=="production" if not "%ENVIRONMENT%"=="development" if not "%ENVIRONMENT%"=="staging" (
    echo [ERROR] Invalid environment: %ENVIRONMENT%
    echo [ERROR] Valid environments: production, development, staging
    exit /b 1
)

REM Set build type based on environment
if "%ENVIRONMENT%"=="production" (
    set BUILD_TYPE=release
    set API_BASE_URL=https://api.grpcstaff.com
) else if "%ENVIRONMENT%"=="staging" (
    set BUILD_TYPE=release
    set API_BASE_URL=https://staging-api.grpcstaff.com
) else (
    set BUILD_TYPE=debug
    set API_BASE_URL=https://dev-api.grpcstaff.com
)

echo [INFO] Building GRPest Control App
echo [INFO] Environment: %ENVIRONMENT%
echo [INFO] Version: %VERSION%
echo [INFO] Build Type: %BUILD_TYPE%
echo [INFO] Firebase Project: %FIREBASE_PROJECT_ID%
echo [INFO] API Base URL: %API_BASE_URL%

REM Update gradle.properties with environment variables
echo [INFO] Updating gradle.properties with environment variables...

(
echo # ============================================================================
echo # GRPest Control App - Gradle Properties
echo # ============================================================================
echo # Environment variables and build configuration
echo # ============================================================================
echo.
echo # Firebase Configuration
echo FIREBASE_PROJECT_ID=%FIREBASE_PROJECT_ID%
echo FIREBASE_API_KEY=%FIREBASE_API_KEY%
echo FIREBASE_STORAGE_BUCKET=%FIREBASE_PROJECT_ID%.appspot.com
echo.
echo # API Configuration
echo API_BASE_URL=%API_BASE_URL%
echo API_VERSION=v1
echo.
echo # App Configuration
echo APP_ENVIRONMENT=%ENVIRONMENT%
echo APP_VERSION=%VERSION%
echo APP_NAME=GRPest Control
echo.
echo # Default User Credentials (for offline mode)
echo DEFAULT_USER_EMAIL=admin@grpc.com
echo DEFAULT_USER_PASSWORD=admin123
echo.
echo # Database Configuration
echo DATABASE_NAME=UserDatabase
echo DATABASE_VERSION=1
echo.
echo # Notification Configuration
echo NOTIFICATION_CHANNEL_ID=grpc_notifications
echo NOTIFICATION_CHANNEL_NAME=GRPest Control Notifications
echo.
echo # File Storage Configuration
echo STORAGE_DIRECTORY=GRPestControl
echo PDF_DIRECTORY=Reports
echo CONTRACT_DIRECTORY=Contracts
echo.
echo # Security Configuration
echo PASSWORD_HASH_ALGORITHM=SHA-256
echo ENCRYPTION_ENABLED=true
echo.
echo # Development Settings
if "%ENVIRONMENT%"=="development" (
    echo DEBUG_MODE=true
) else (
    echo DEBUG_MODE=false
)
echo LOG_LEVEL=INFO
echo.
echo # ============================================================================
echo # Build Configuration
echo # ============================================================================
echo org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
echo android.useAndroidX=true
echo android.enableJetifier=true
) > gradle.properties

echo [SUCCESS] gradle.properties updated

REM Clean previous builds
echo [INFO] Cleaning previous builds...
call gradlew.bat clean

REM Build the APK
echo [INFO] Building APK...
if "%BUILD_TYPE%"=="release" (
    call gradlew.bat assembleRelease
    set APK_PATH=app\build\outputs\apk\release\app-release.apk
) else (
    call gradlew.bat assembleDebug
    set APK_PATH=app\build\outputs\apk\debug\app-debug.apk
)

REM Check if build was successful
if exist "%APK_PATH%" (
    echo [SUCCESS] Build completed successfully!
    echo [INFO] APK location: %APK_PATH%
    
    REM Get APK size
    for %%A in ("%APK_PATH%") do set APK_SIZE=%%~zA
    echo [INFO] APK size: %APK_SIZE% bytes
    
    REM Show build summary
    echo.
    echo [INFO] === Build Summary ===
    echo [INFO] Environment: %ENVIRONMENT%
    echo [INFO] Version: %VERSION%
    echo [INFO] Firebase Project: %FIREBASE_PROJECT_ID%
    echo [INFO] API Base URL: %API_BASE_URL%
    echo [INFO] APK Path: %APK_PATH%
    echo [INFO] APK Size: %APK_SIZE% bytes
    echo [INFO] ====================
    
) else (
    echo [ERROR] Build failed! APK not found at: %APK_PATH%
    exit /b 1
)

echo [SUCCESS] Build process completed! 