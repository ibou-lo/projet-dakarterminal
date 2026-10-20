import 'package:flutter/foundation.dart';

/// Configuration de l'application.
///
/// L'URL de l'API peut être forcée au lancement :
///   flutter run --dart-define=API_BASE_URL=http://192.168.1.20:8080
/// Sinon, valeur par défaut selon la cible :
///   - navigateur (flutter run -d chrome) : http://localhost:8080
///   - émulateur Android : http://10.0.2.2:8080 (10.0.2.2 = le PC hôte)
///   - Windows / iOS simulateur : http://127.0.0.1:8080
/// Sur un TÉLÉPHONE RÉEL, 127.0.0.1 désigne le téléphone lui-même :
/// utiliser l'adresse IP du PC sur le Wi-Fi (ipconfig).
class AppConfig {
  static const String _override = String.fromEnvironment('API_BASE_URL');

  static String get apiBaseUrl {
    if (_override.isNotEmpty) return _override;
    if (kIsWeb) return 'http://localhost:8080';
    if (defaultTargetPlatform == TargetPlatform.android) return 'http://10.0.2.2:8080';
    return 'http://127.0.0.1:8080';
  }

  /// Zone pilote par défaut pour les suggestions de place.
  static const String defaultZone = 'B';
}
