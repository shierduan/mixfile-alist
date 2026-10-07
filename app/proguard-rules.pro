# Proguard rules for PureWriter
-keep class com.purewrite.writer.data.db.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-dontwarn org.jetbrains.annotations.**
