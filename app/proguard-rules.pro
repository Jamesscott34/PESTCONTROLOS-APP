# ============================================================
# Line number info for crash stack traces
# ============================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# Firebase — Firestore, Auth, Storage, App Check
# ============================================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepnames class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ============================================================
# iText7 PDF library
# ============================================================
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# ============================================================
# Apache POI (PDF to Word)
# ============================================================
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**

# ============================================================
# Google ML Kit (OCR)
# ============================================================
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ============================================================
# OkHttp (AI API calls)
# ============================================================
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ============================================================
# WorkManager
# ============================================================
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ============================================================
# Material Components
# ============================================================
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ============================================================
# App model/data classes written to or read from Firestore
# (prevent field name obfuscation breaking serialisation)
# ============================================================
-keepclassmembers class com.grpc.grpc.** {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
-keep class com.grpc.grpc.messaging.model.** { *; }
-keep class com.grpc.grpc.workview.model.** { *; }
-keep class com.grpc.grpc.routes.model.** { *; }
-keep class com.grpc.grpc.quotations.model.** { *; }
-keep class com.grpc.grpc.reports.model.** { *; }
-keep class com.grpc.grpc.safety.model.** { *; }
-keep class com.grpc.grpc.email.model.** { *; }

# ============================================================
# Parcelable and Serializable
# ============================================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================
# Enum classes
# ============================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# Reflection — keep class names used with Class.forName()
# ============================================================
-keepnames class com.grpc.grpc.** { *; }

# ============================================================
# Suppress warnings for optional dependencies
# ============================================================
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**
-dontwarn org.xml.sax.**
-dontwarn java.awt.**
-dontwarn sun.misc.**
