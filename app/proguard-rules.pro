# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\tools\adt-bundle-windows-x86_64-20131030\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

#-dontobfuscate
# CutCornerDrawable dipanggil CUMA dari nama tag di drawable XML, lewat refleksi
# (DrawableInflater.inflateFromClass) -> shrinker liatnya unused dan bakal buang kelas
# + no-arg constructor-nya. minifyEnabled masih false di kedua build type, jadi ini
# belum aktif; ditaruh sekarang supaya nyalain minify nanti ga bikin tiap dialog,
# tombol, field dan combo crash sekaligus.
-keep class com.winlator.cmod.widget.CutCornerDrawable { <init>(...); }
