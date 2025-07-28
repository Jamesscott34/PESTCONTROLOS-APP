#!/bin/bash

# ============================================================================
# GRPest Control App - Environment Setup Script (Linux/Mac)
# ============================================================================
# This script helps developers set up their environment configuration
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

print_status "Setting up GRPest Control App environment..."

# Check if gradle.properties already exists
if [ -f "gradle.properties" ]; then
    print_warning "gradle.properties already exists!"
    print_status "Backing up existing file to gradle.properties.backup"
    cp gradle.properties gradle.properties.backup
fi

# Copy template to gradle.properties
if [ -f "gradle.properties.template" ]; then
    cp gradle.properties.template gradle.properties
    print_success "Created gradle.properties from template"
    echo
    print_status "Please edit gradle.properties with your actual values:"
    print_status "- FIREBASE_PROJECT_ID: Your Firebase project ID"
    print_status "- FIREBASE_API_KEY: Your Firebase API key"
    print_status "- API_BASE_URL: Your API base URL"
    print_status "- APP_ENVIRONMENT: production, development, or staging"
    echo
    print_status "You can also use the build scripts:"
    print_status "- ./build-with-env.sh -e production -v 1.0.0"
    print_status "- ./build-with-env.sh -e development -f your-dev-project"
else
    print_error "gradle.properties.template not found!"
    print_status "Please create gradle.properties manually"
fi

print_success "Environment setup completed!" 