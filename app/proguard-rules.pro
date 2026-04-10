# YKFJ Inventory — R8 rules
# Keep Room entities (reflection via annotation processor output is fine,
# but we keep them for safety with kotlinx.serialization)
-keep class com.ykfj.inventory.data.local.db.** { *; }
-keep class com.ykfj.inventory.domain.model.** { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# Netty (Ktor server engine)
-keep class io.netty.** { *; }
-dontwarn io.netty.**

# Hilt — generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# iText
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# bcrypt
-keep class at.favre.lib.crypto.bcrypt.** { *; }
