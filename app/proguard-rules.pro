# Cyphr / Vault — release minify + shrinkResources (v0.4.31+)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# App entry points
-keep class app.vault.workspace.VaultApp { *; }
-keep class app.vault.workspace.MainActivity { *; }

# Room entities + DB
-keep class app.vault.workspace.data.VaultItemEntity { *; }
-keep class app.vault.workspace.data.VaultFolderEntity { *; }
-keep class app.vault.workspace.data.VaultCategory { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# Enums (Room / UI)
-keepclassmembers enum app.vault.workspace.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Crypto / lock type
-keep class app.vault.workspace.crypto.** { *; }
-keep class app.vault.workspace.auth.LockType { *; }

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Biometric / FragmentActivity
-keep class androidx.biometric.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
