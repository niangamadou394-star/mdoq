import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:medoq/core/constants/api_constants.dart';
import 'package:medoq/core/network/api_client.dart';
import 'package:medoq/core/storage/secure_storage.dart';
import 'package:medoq/features/auth/domain/auth_models.dart';

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(
    ref.watch(apiClientProvider).dio,
    ref.watch(secureStorageProvider),
  );
});

class AuthRepository {
  final Dio _dio;
  final SecureStorageService _storage;

  AuthRepository(this._dio, this._storage);

  Future<AuthTokens> login({
    required String phone,
    required String password,
  }) async {
    final resp = await _dio.post(ApiConstants.login, data: {
      'phone':    phone,
      'password': password,
    });
    final tokens = AuthTokens.fromJson(resp.data as Map<String, dynamic>);
    await _persist(tokens);
    return tokens;
  }

  Future<void> register({
    required String phone,
    required String firstName,
    required String lastName,
    required String password,
    String? email,
  }) async {
    await _dio.post(ApiConstants.register, data: {
      'phone':     phone,
      'firstName': firstName,
      'lastName':  lastName,
      'password':  password,
      if (email != null && email.isNotEmpty) 'email': email,
    });
    // Registration sends an OTP — no tokens yet
  }

  Future<void> forgotPassword({required String phone}) async {
    await _dio.post(ApiConstants.forgotPassword, data: {'phone': phone});
  }

  Future<void> resetPassword({
    required String phone,
    required String otp,
    required String newPassword,
  }) async {
    await _dio.post(ApiConstants.resetPassword, data: {
      'phone':       phone,
      'otp':         otp,
      'newPassword': newPassword,
    });
  }

  Future<void> logout() async {
    try {
      await _dio.post(ApiConstants.logout);
    } catch (_) {
      // Best-effort: clear local tokens regardless
    } finally {
      await _storage.clearAll();
    }
  }

  Future<void> _persist(AuthTokens tokens) => _storage.saveTokens(
    accessToken:  tokens.accessToken,
    refreshToken: tokens.refreshToken,
    userId:       tokens.user.id,
    role:         tokens.user.role,
  );
}
