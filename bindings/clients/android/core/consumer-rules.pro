# JNA and the UniFFI bindings are looked up reflectively from native code.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class com.fserver.core.ffi.** { *; }
-dontwarn java.awt.**
