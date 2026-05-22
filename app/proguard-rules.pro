# -- General optimization configurations --
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# -- Apache POI Rules --
# Keep POI classes and their members
-keep class org.apache.poi.** { *; }
-keep interface org.apache.poi.** { *; }

# Keep openxmlformats classes and members
-keep class org.openxmlformats.schemas.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }

# Ignore standard missing libraries on Android (Java Desktop/AWT elements, XML stream protocols, ETSI signatures)
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**
-dontwarn schemaorg_apache_xmlbeans.**
-dontwarn org.etsi.**
-dontwarn javax.xml.stream.**
-dontwarn javax.xml.namespace.**
-dontwarn org.w3c.dom.**
-dontwarn org.xmlpull.v1.**
-dontwarn com.sun.**
-dontwarn java.awt.**
-dontwarn javax.xml.crypto.**
-dontwarn javax.activation.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.jcp.**
-dontwarn org.apache.commons.**

# -- PDFBox Android Rules --
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# Keep standard assets & resources
-keepclassmembers class com.tom_roush.pdfbox.pdmodel.font.PDFont {
    static <fields>;
}

# -- XMLBeans Rules --
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.xmlbeans.**

# -- Room Database Rules --
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase$Callback
-dontwarn androidx.room.paging.**

# -- Hilt/Dagger Rules --
-keep class dagger.hilt.** { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManager { *; }
-keep class * implements dagger.hilt.internal.UnsafeCasts { *; }
