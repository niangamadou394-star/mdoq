class ApiConstants {
  // Base URL — override via --dart-define=API_BASE_URL=https://...
  static const baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080/api',  // Android emulator → localhost
  );

  // Auth
  static const register         = '/auth/register';
  static const login            = '/auth/login';
  static const refresh          = '/auth/refresh';
  static const logout           = '/auth/logout';
  static const forgotPassword   = '/auth/forgot-password';
  static const resetPassword    = '/auth/reset-password';

  // Medications
  static const medicationsSearch  = '/medications/search';
  static const medicationsPopular = '/medications/popular';
  static String medicationDetail(String id) => '/medications/$id';

  // Pharmacies
  static const pharmaciesNearby = '/pharmacies/nearby';

  // Reservations
  static const reservations          = '/reservations';
  static String reservationById(String id)  => '/reservations/$id';
  static String reservationConfirm(String id) => '/reservations/$id/confirm';
  static String reservationCancel(String id)  => '/reservations/$id/cancel';
  static String patientReservations(String id) => '/reservations/patient/$id';

  // Payments
  static const initiateWave   = '/payments/wave/initiate';
  static const initiateOrange = '/payments/orange/initiate';
  static String paymentById(String id) => '/payments/$id';
  static String paymentByReservation(String id) => '/payments/reservation/$id';
}
