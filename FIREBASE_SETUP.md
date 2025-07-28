# Firebase Setup Guide

## 🔧 Fixing "Access Denied" Error

### **Problem:**
When trying to access View Contracts, you get an "access denied" error.

### **Root Cause:**
Firebase Firestore security rules are blocking access to the contract collections.

## 🚀 Quick Fix

### **Option 1: Update Firebase Security Rules (Recommended)**

1. **Go to Firebase Console:**
   - Visit: https://console.firebase.google.com
   - Select your project

2. **Navigate to Firestore:**
   - Click "Firestore Database" in the left sidebar
   - Click "Rules" tab

3. **Replace the rules with:**
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Allow access to all contract collections
    match /{collection} {
      allow read, write: if true;
    }
    
    // Specific rules for contract collections
    match /{contractCollection} {
      // Allow access to any collection that ends with " Contracts"
      allow read, write: if contractCollection.matches('.* Contracts');
    }
    
    // Allow access to all documents in any collection
    match /{collection}/{document=**} {
      allow read, write: if true;
    }
  }
}
```

4. **Click "Publish"**

### **Option 2: Create Collections Manually**

If the collections don't exist, create them:

1. **In Firebase Console:**
   - Go to Firestore Database
   - Click "Start collection"
   - Create collections:
     - `Ian Contracts`
     - `James Contracts`
     - `Kristine Contracts`

2. **Add a test document:**
   - Click "Add document"
   - Document ID: `test`
   - Add fields:
     - `name`: "Test Contract"
     - `address`: "Test Address"
     - `email`: "test@example.com"
     - `contact`: "1234567890"
     - `visits`: "4"
     - `lastVisit`: "01/01/2024"
     - `nextVisit`: "01/02/2024"

## 🔍 Debugging Steps

### **1. Check Logs**
Look for these log messages in Android Studio:
```
ViewContractActivity: Loading contracts for user: [username]
ViewContractActivity: Loading collection: [username] Contracts
ViewContractActivity: Successfully loaded X contracts
```

### **2. Test Firebase Connection**
The app now includes automatic Firebase authentication. Check logs for:
```
FirebaseHelper: Firebase services initialized successfully
FirebaseHelper: Anonymous authentication successful
```

### **3. Verify Collection Names**
Make sure the collections are named exactly:
- `Ian Contracts` (with space)
- `James Contracts` (with space)
- `Kristine Contracts` (with space)

## 🛠️ Manual Testing

### **Test with Sample Data:**

1. **Create test contracts in Firebase Console:**
```json
{
  "name": "Test Customer",
  "address": "123 Test Street, Dublin",
  "email": "test@example.com",
  "contact": "0871234567",
  "visits": "4",
  "lastVisit": "01/01/2024",
  "nextVisit": "01/02/2024"
}
```

2. **Test the app:**
   - Login with any user
   - Navigate to View Contracts
   - Should see the test contract

## 🔐 Security Considerations

### **For Production:**
Replace the permissive rules with proper authentication:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Allow authenticated users to access their contracts
    match /{userName} Contracts/{document} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## 📱 App Features

### **Enhanced Error Handling:**
- Better error messages
- Automatic Firebase authentication
- Graceful fallback for missing collections
- Detailed logging for debugging

### **User-Specific Collections:**
- `Ian Contracts` - Ian's contracts
- `James Contracts` - James's contracts  
- `Kristine Contracts` - Kristine's contracts
- Kristine can access both Ian and James collections

## 🚨 Troubleshooting

### **Still Getting Access Denied?**

1. **Check Firebase Project:**
   - Verify you're using the correct Firebase project
   - Check `google-services.json` is up to date

2. **Test Internet Connection:**
   - Ensure device has internet access
   - Check Firebase Console is accessible

3. **Clear App Data:**
   - Uninstall and reinstall the app
   - Clear app cache and data

4. **Check Firebase Console:**
   - Verify collections exist
   - Check security rules are published
   - Look for any error messages

### **Common Issues:**

1. **Collection doesn't exist:**
   - Create the collection manually in Firebase Console
   - Add at least one test document

2. **Wrong collection name:**
   - Must be exactly `[Username] Contracts`
   - Case sensitive
   - Space between username and "Contracts"

3. **Firebase project mismatch:**
   - Download latest `google-services.json`
   - Replace in `app/` directory

## 📞 Support

If issues persist:
1. Check Android Studio logs for detailed error messages
2. Verify Firebase Console settings
3. Test with a simple document first
4. Contact admin for Firebase project access

---

**Note:** The app now includes automatic Firebase authentication and better error handling to prevent access denied issues. 