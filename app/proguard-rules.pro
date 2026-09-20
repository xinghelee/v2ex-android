# Add project specific ProGuard rules here.

# 开源项目不需要混淆；保留类名和行号，用户从「崩溃日志」页分享出来的堆栈才能直接读。
# 仍然保留 R8 的裁剪与优化。
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

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
