import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:medoq/core/constants/api_constants.dart';
import 'package:medoq/core/network/api_client.dart';
import 'package:medoq/features/payment/domain/payment_models.dart';

final paymentRepositoryProvider = Provider<PaymentRepository>((ref) {
  return PaymentRepository(ref.watch(apiClientProvider).dio);
});

class PaymentRepository {
  final Dio _dio;
  PaymentRepository(this._dio);

  Future<Payment> initiateWave(String reservationId) async {
    final resp = await _dio.post(ApiConstants.initiateWave, data: {
      'reservationId': reservationId,
    });
    return Payment.fromJson(resp.data as Map<String, dynamic>);
  }

  Future<Payment> initiateOrangeMoney(String reservationId) async {
    final resp = await _dio.post(ApiConstants.initiateOrange, data: {
      'reservationId': reservationId,
    });
    return Payment.fromJson(resp.data as Map<String, dynamic>);
  }

  Future<Payment> getById(String id) async {
    final resp = await _dio.get(ApiConstants.paymentById(id));
    return Payment.fromJson(resp.data as Map<String, dynamic>);
  }

  Future<Payment?> getByReservation(String reservationId) async {
    try {
      final resp = await _dio.get(
        ApiConstants.paymentByReservation(reservationId),
      );
      return Payment.fromJson(resp.data as Map<String, dynamic>);
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) return null;
      rethrow;
    }
  }
}
