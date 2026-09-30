# Project-specific R8 rules. Libraries (Hilt, Room, Navigation,
# kotlinx.serialization, Compose) ship their own consumer rules.

# ML Kit code scanner (barcode in "Lebensmittel hinzufügen"): its components are
# wired up via reflection; R8 full mode otherwise strips them and the scanner
# crashes with an NPE inside com.google.android.gms.internal.mlkit_code_scanner.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_code_scanner.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }

# Credential Manager (Google sign-in) loads its Play Services provider by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
