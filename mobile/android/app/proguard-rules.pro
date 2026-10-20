# Règles R8 pour la version release de Dakar AutoTrack.

# ML Kit Text Recognition : le plugin référence les modèles chinois, devanagari,
# japonais et coréen, que l'application n'embarque pas (seul l'alphabet latin est utilisé
# pour lire les VIN). On ignore ces classes absentes.
-dontwarn com.google.mlkit.vision.text.chinese.**
-dontwarn com.google.mlkit.vision.text.devanagari.**
-dontwarn com.google.mlkit.vision.text.japanese.**
-dontwarn com.google.mlkit.vision.text.korean.**

# Conserver les classes ML Kit et les plugins Flutter appelés par réflexion.
-keep class com.google.mlkit.** { *; }
-keep class com.google_mlkit_text_recognition.** { *; }
-keep class com.google_mlkit_commons.** { *; }
-keep class io.flutter.plugins.** { *; }
