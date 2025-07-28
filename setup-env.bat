@echo off
REM ============================================================================
REM GRPest Control App - Environment Setup Script (Windows)
REM ============================================================================
REM This script helps developers set up their environment configuration
REM ============================================================================

echo [INFO] Setting up GRPest Control App environment...

REM Check if gradle.properties already exists
if exist "gradle.properties" (
    echo [WARNING] gradle.properties already exists!
    echo [INFO] Backing up existing file to gradle.properties.backup
    copy gradle.properties gradle.properties.backup
)

REM Copy template to gradle.properties
if exist "gradle.properties.template" (
    copy gradle.properties.template gradle.properties
    echo [SUCCESS] Created gradle.properties from template
    echo.
    echo [INFO] Please edit gradle.properties with your actual values:
    echo [INFO] - FIREBASE_PROJECT_ID: Your Firebase project ID
    echo [INFO] - FIREBASE_API_KEY: Your Firebase API key
    echo [INFO] - API_BASE_URL: Your API base URL
    echo [INFO] - APP_ENVIRONMENT: production, development, or staging
    echo.
    echo [INFO] You can also use the build scripts:
    echo [INFO] - build-with-env.bat -e production -v 1.0.0
    echo [INFO] - build-with-env.bat -e development -f your-dev-project
) else (
    echo [ERROR] gradle.properties.template not found!
    echo [INFO] Please create gradle.properties manually
)

echo [INFO] Environment setup completed! 