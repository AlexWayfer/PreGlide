# Room creates a database through its generated class's no-argument constructor, by reflection. Room 2.5.0, which
# WorkManager brings in through Play's asset delivery for LiteRT, keeps the class but not the constructor, so R8
# removed it and the optimized build crashed at start, creating WorkManager's database.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}

# LiteRT's native code finds its Kotlin API's classes and members by name through JNI, as
# TensorType$ElementType; its own rules keep only what is marked @UsedByReflection, so R8 renamed the rest and
# the prediction model aborted the process as it loaded. The API is small, so all of it stays as it is.
-keep class com.google.ai.edge.litert.** { *; }
