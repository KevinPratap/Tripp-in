# kotlinx.serialization: keep generated serializers for the Retrofit DTOs.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.trippin.ai.data.remote.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.trippin.ai.data.remote.**$$serializer { *; }
