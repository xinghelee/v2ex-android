# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# kotlinx.serialization
-keepclasseswithmembers class com.vibe.v2ex.**.*$$serializer {
    *** Companion;
}
-keepclassmembers class com.vibe.v2ex.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.vibe.v2ex.**$$serializer { *; }
-keepclassmembers class com.vibe.v2ex.** implements kotlinx.serialization.internal.GeneratedSerializer {
    <fields>;
}

# Tink (EncryptedSharedPreferences) 引用的编译期注解，运行时不存在 — 安全忽略。
-dontwarn com.google.errorprone.annotations.**
