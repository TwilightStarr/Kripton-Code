-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Glance widget: eylem geri çağrıları ve widget sınıfları ada göre (yansıma ile) örneklenir.
-keep class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { <init>(); }

# ACRA yerel gönderici: ServiceLoader ile (META-INF/services) bulunur; ACRA ayrıca BuildConfig'i yansıtma ile okur.
-keep class com.kriptoncod.app.data.crash.LocalCrashSenderFactory { <init>(); }
-keep class com.kriptoncod.app.BuildConfig { *; }
