# Sora Editor / Monarch use reflection and service-style registries.
-keep class io.github.rosemoe.sora.** { *; }
-keep class io.github.dingyi222666.monarch.** { *; }
-keep class org.eclipse.tm4e.** { *; }
-dontwarn org.joni.**
-dontwarn org.jcodings.**
-dontwarn com.google.re2j.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Optional Monarch DSL codegen API references KotlinPoet; not used at runtime.
-dontwarn com.squareup.kotlinpoet.**
-dontwarn kotlin.Cloneable$DefaultImpls

# Called from Python by name through Chaquopy's Java bridge.
-keep class com.saikishore.kalicodepocket.PythonRunActivity { *; }
