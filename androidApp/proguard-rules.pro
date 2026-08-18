# R8 rules for the release build.
#
# Most libraries here ship their own consumer rules (Compose, coroutines, Firebase, okio), so this
# file only covers what R8 cannot work out on its own -- which is almost entirely reflection-shaped
# code: kotlinx.serialization's generated serializers and Ktor's service loading.
#
# If a release build crashes where debug works, suspect this file first. Run
# `./gradlew :androidApp:bundleRelease` and read androidApp/build/outputs/mapping/release/ to see
# what was removed or renamed.

# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# Serializers are resolved through synthetic Companion members that nothing references directly, so
# R8 sees them as dead code and strips them. These are the rules from the official README.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses

-if @kotlinx.serialization.Serializable class *
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class * {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class * {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# The Firestore documents are these classes. Their serial names are baked into the generated
# descriptors, but keeping the types outright removes any doubt that a rename could change the
# shape of what gets written to the database -- a corruption bug that would only appear in release.
-keep class com.openly.shared.data.model.** { *; }

# ---------------------------------------------------------------------------
# GitLive Firebase SDK
# ---------------------------------------------------------------------------
# The wrapper hands Kotlin objects to the native Firebase Android SDK, which inspects them
# reflectively on the way through.
-keep class dev.gitlive.firebase.** { *; }
-dontwarn dev.gitlive.firebase.**

# ---------------------------------------------------------------------------
# Ktor + Coil (image loading)
# ---------------------------------------------------------------------------
# Ktor's engines register through ServiceLoader and its atomics are patched by field name.
-keep class io.ktor.client.engine.okhttp.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**

# Optional slf4j binding Ktor references but this app never provides.
-dontwarn org.slf4j.**

# ---------------------------------------------------------------------------
# Kotlin internals
# ---------------------------------------------------------------------------
# Coroutine internals reached only from generated state machines.
-keepclassmembers class kotlin.coroutines.jvm.internal.** { *; }
-dontwarn kotlinx.coroutines.**

# Compose keeps its own rules, but the Material icons are referenced by property name from
# commonMain and the extended set is large enough that R8 aggressively prunes it.
-dontwarn androidx.compose.**
