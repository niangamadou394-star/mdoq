class AppConstants {
  // Phone validation
  static final phoneRegex = RegExp(r'^\+221(70|75|76|77|78)\d{7}$');

  // Default search radius (km)
  static const defaultRadius = 5.0;

  // Map
  static const defaultLat = 14.6928;  // Dakar, Sénégal
  static const defaultLng = -17.4467;
  static const defaultZoom = 13.0;

  // Pagination
  static const pageSize = 20;

  // Cache TTL
  static const searchCacheDuration = Duration(minutes: 5);

  // Reservation expiry warning threshold (minutes)
  static const expiryWarningMinutes = 30;
}
