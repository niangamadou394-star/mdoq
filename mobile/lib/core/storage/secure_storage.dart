import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

final secureStorageProvider = Provider<SecureStorageService>(
  (_) => SecureStorageService(),
);

class SecureStorageService {
  final _storage = const FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
    iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
  );

  static const _keyAccessToken  = 'access_token';
  static const _keyRefreshToken = 'refresh_token';
  static const _keyUserId       = 'user_id';
  static const _keyUserRole     = 'user_role';

  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
    required String userId,
    required String role,
  }) async {
    await Future.wait([
      _storage.write(key: _keyAccessToken,  value: accessToken),
      _storage.write(key: _keyRefreshToken, value: refreshToken),
      _storage.write(key: _keyUserId,       value: userId),
      _storage.write(key: _keyUserRole,     value: role),
    ]);
  }

  Future<String?> get accessToken  => _storage.read(key: _keyAccessToken);
  Future<String?> get refreshToken => _storage.read(key: _keyRefreshToken);
  Future<String?> get userId       => _storage.read(key: _keyUserId);
  Future<String?> get userRole     => _storage.read(key: _keyUserRole);

  Future<bool> get isLoggedIn async =>
      (await _storage.read(key: _keyAccessToken)) != null;

  Future<void> clearAll() => _storage.deleteAll();
}
