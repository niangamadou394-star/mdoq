import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:medoq/core/constants/api_constants.dart';
import 'package:medoq/core/storage/secure_storage.dart';

final apiClientProvider = Provider<ApiClient>((ref) {
  final storage = ref.watch(secureStorageProvider);
  return ApiClient(storage);
});

class ApiClient {
  final SecureStorageService _storage;
  late final Dio _dio;

  ApiClient(this._storage) {
    _dio = Dio(BaseOptions(
      baseUrl: ApiConstants.baseUrl,
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 30),
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    ));

    _dio.interceptors.addAll([
      _AuthInterceptor(_storage, _dio),
      LogInterceptor(
        requestBody: true,
        responseBody: true,
        logPrint: (obj) => debugPrint('[API] $obj'),
      ),
    ]);
  }

  Dio get dio => _dio;
}

// ── Auth Interceptor — attaches Bearer token + handles 401 refresh ────────────

class _AuthInterceptor extends Interceptor {
  final SecureStorageService _storage;
  final Dio _dio;
  bool _isRefreshing = false;

  _AuthInterceptor(this._storage, this._dio);

  @override
  void onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final token = await _storage.accessToken;
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    if (err.response?.statusCode == 401 && !_isRefreshing) {
      _isRefreshing = true;
      try {
        final refreshToken = await _storage.refreshToken;
        if (refreshToken == null) {
          await _storage.clearAll();
          handler.next(err);
          return;
        }

        final resp = await _dio.post(
          ApiConstants.refresh,
          data: {'refreshToken': refreshToken},
          options: Options(headers: {'Authorization': null}),
        );

        final data = resp.data as Map<String, dynamic>;
        await _storage.saveTokens(
          accessToken:  data['accessToken'],
          refreshToken: data['refreshToken'],
          userId:       data['user']['id'],
          role:         data['user']['role'],
        );

        // Retry original request with new token
        err.requestOptions.headers['Authorization'] =
            'Bearer ${data['accessToken']}';
        final retry = await _dio.fetch(err.requestOptions);
        handler.resolve(retry);
      } catch (_) {
        await _storage.clearAll();
        handler.next(err);
      } finally {
        _isRefreshing = false;
      }
    } else {
      handler.next(err);
    }
  }
}

// ignore: non_constant_identifier_names
void debugPrint(String msg) {
  // ignore: avoid_print
  print(msg);
}
