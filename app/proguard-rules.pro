# Keep Room entities and Gson-free data classes used for CSV/GeoJSON serialization
-keep class com.ltemapper.app.data.entities.** { *; }
-keep class com.ltemapper.app.domain.model.** { *; }

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Hilt / Dagger generated code
-dontwarn com.google.errorprone.annotations.**
