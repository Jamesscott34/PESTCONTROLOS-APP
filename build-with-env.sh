#!/bin/bash

# ============================================================================
# GRPest Control App - Build Script with Environment Variables
# ============================================================================
# This script builds the APK with different environment configurations
# ============================================================================

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -e, --environment ENV    Build environment (production|development|staging)"
    echo "  -v, --version VERSION    App version (e.g., 1.0.0)"
    echo "  -f, --firebase-id ID     Firebase Project ID"
    echo "  -k, --api-key KEY        Firebase API Key"
    echo "  -u, --api-url URL        API Base URL"
    echo "  -h, --help               Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 -e production -v 1.0.0"
    echo "  $0 -e development -f my-dev-project"
    echo "  $0 --environment staging --version 1.1.0 --firebase-id staging-project"
}

# Default values
ENVIRONMENT="production"
VERSION="1.0.0"
FIREBASE_PROJECT_ID="grpc-app-12345"
FIREBASE_API_KEY=""
API_BASE_URL="https://api.grpcstaff.com"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        -v|--version)
            VERSION="$2"
            shift 2
            ;;
        -f|--firebase-id)
            FIREBASE_PROJECT_ID="$2"
            shift 2
            ;;
        -k|--api-key)
            FIREBASE_API_KEY="$2"
            shift 2
            ;;
        -u|--api-url)
            API_BASE_URL="$2"
            shift 2
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Validate environment
if [[ ! "$ENVIRONMENT" =~ ^(production|development|staging)$ ]]; then
    print_error "Invalid environment: $ENVIRONMENT"
    print_error "Valid environments: production, development, staging"
    exit 1
fi

# Set build type based on environment
if [[ "$ENVIRONMENT" == "production" ]]; then
    BUILD_TYPE="release"
    API_BASE_URL="https://api.grpcstaff.com"
elif [[ "$ENVIRONMENT" == "staging" ]]; then
    BUILD_TYPE="release"
    API_BASE_URL="https://staging-api.grpcstaff.com"
else
    BUILD_TYPE="debug"
    API_BASE_URL="https://dev-api.grpcstaff.com"
fi

print_status "Building GRPest Control App"
print_status "Environment: $ENVIRONMENT"
print_status "Version: $VERSION"
print_status "Build Type: $BUILD_TYPE"
print_status "Firebase Project: $FIREBASE_PROJECT_ID"
print_status "API Base URL: $API_BASE_URL"

# Update gradle.properties with environment variables
print_status "Updating gradle.properties with environment variables..."

cat > gradle.properties << EOF
# ============================================================================
# GRPest Control App - Gradle Properties
# ============================================================================
# Environment variables and build configuration
# ============================================================================

# Firebase Configuration
FIREBASE_PROJECT_ID=$FIREBASE_PROJECT_ID
FIREBASE_API_KEY=$FIREBASE_API_KEY
FIREBASE_STORAGE_BUCKET=${FIREBASE_PROJECT_ID}.appspot.com

# API Configuration
API_BASE_URL=$API_BASE_URL
API_VERSION=v1

# App Configuration
APP_ENVIRONMENT=$ENVIRONMENT
APP_VERSION=$VERSION
APP_NAME=GRPest Control

# Default User Credentials (for offline mode)
DEFAULT_USER_EMAIL=admin@grpc.com
DEFAULT_USER_PASSWORD=admin123

# Database Configuration
DATABASE_NAME=UserDatabase
DATABASE_VERSION=1

# Notification Configuration
NOTIFICATION_CHANNEL_ID=grpc_notifications
NOTIFICATION_CHANNEL_NAME=GRPest Control Notifications

# File Storage Configuration
STORAGE_DIRECTORY=GRPestControl
PDF_DIRECTORY=Reports
CONTRACT_DIRECTORY=Contracts

# Security Configuration
PASSWORD_HASH_ALGORITHM=SHA-256
ENCRYPTION_ENABLED=true

# Development Settings
DEBUG_MODE=$([[ "$ENVIRONMENT" == "development" ]] && echo "true" || echo "false")
LOG_LEVEL=INFO

# ============================================================================
# Build Configuration
# ============================================================================
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
EOF

print_success "gradle.properties updated"

# Clean previous builds
print_status "Cleaning previous builds..."
./gradlew clean

# Build the APK
print_status "Building APK..."
if [[ "$BUILD_TYPE" == "release" ]]; then
    ./gradlew assembleRelease
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
else
    ./gradlew assembleDebug
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

# Check if build was successful
if [[ -f "$APK_PATH" ]]; then
    print_success "Build completed successfully!"
    print_status "APK location: $APK_PATH"
    
    # Get APK size
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    print_status "APK size: $APK_SIZE"
    
    # Show build summary
    echo ""
    print_status "=== Build Summary ==="
    print_status "Environment: $ENVIRONMENT"
    print_status "Version: $VERSION"
    print_status "Firebase Project: $FIREBASE_PROJECT_ID"
    print_status "API Base URL: $API_BASE_URL"
    print_status "APK Path: $APK_PATH"
    print_status "APK Size: $APK_SIZE"
    print_status "===================="
    
else
    print_error "Build failed! APK not found at: $APK_PATH"
    exit 1
fi

print_success "Build process completed!" 