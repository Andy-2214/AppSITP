# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Reglas R8 del proyecto ---
# Conservar metadatos para Firebase y stack traces legibles.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,AnnotationDefault
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room genera el código con KSP (no usa reflection sobre modelos propios),
# Firestore se mapea a mano (DocumentSnapshot.get*/getString), así que no se
# requieren keeps de modelos. Los SDKs de Google/Firebase aportan sus propias
# consumer rules en sus AAR.

# Iconos de Material (material-icons-extended) no referenciados se eliminan solos.