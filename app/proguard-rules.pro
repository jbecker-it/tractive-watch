# Nicht umbenennen: Crash-Logs (Crash-Info / Upload) bleiben lesbar.
# Shrinking + Optimierung (der eigentliche Performance-Gewinn) bleiben aktiv.
-dontobfuscate

# Moshi arbeitet per Reflexion (KotlinJsonAdapterFactory) auf den Datenklassen:
# Felder, Konstruktoren, @Json-Annotationen und Kotlin-Metadaten behalten.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class kotlin.Metadata { *; }
-keep class de.jbecker.tractivewatch.data.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Tink (security-crypto) referenziert optionale Annotationen
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
