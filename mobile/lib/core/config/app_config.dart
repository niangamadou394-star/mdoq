/// Centralise toutes les clés API et variables d'environnement de l'app.
///
/// Les valeurs sont injectées au build via --dart-define :
///   flutter run --dart-define=MAPS_API_KEY=<votre_clé>
///
/// En développement local la valeur par défaut ci-dessous est utilisée.
/// Ne jamais hard-coder une clé de production ici — utiliser les secrets CI/CD.
class AppConfig {
  AppConfig._();

  // ── Google Maps ──────────────────────────────────────────────
  static const String mapsApiKey = String.fromEnvironment(
    'MAPS_API_KEY',
    defaultValue: 'AIzaSyCygk1CcDErP45U8leZaVmEVevhnONIQw4',
  );

  // ── Backend API ──────────────────────────────────────────────
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080/api/v1', // 10.0.2.2 = localhost depuis l'émulateur Android
  );

  // ── Environnement ────────────────────────────────────────────
  static const bool isProduction = bool.fromEnvironment('PRODUCTION', defaultValue: false);
}
