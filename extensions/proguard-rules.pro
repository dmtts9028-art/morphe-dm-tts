-dontobfuscate
-dontoptimize
-keepattributes *
-keep class app.morphe.** {
  *;
}

# JNA resolves its loader and callback support classes reflectively, and its
# interfaces are implemented as dynamic proxies. Keep the whole runtime.
-keep class com.sun.jna.** {
  *;
}
-keep class * implements com.sun.jna.Library {
  *;
}
-keep class * implements com.sun.jna.Callback {
  *;
}
-keep class app.morphe.extension.instagram.tts.** {
  *;
}
-dontwarn java.awt.**
-dontwarn com.sun.jna.platform.**
