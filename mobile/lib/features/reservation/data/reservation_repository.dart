import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:medoq/core/constants/api_constants.dart';
import 'package:medoq/core/network/api_client.dart';
import 'package:medoq/core/storage/secure_storage.dart';
import 'package:medoq/features/reservation/domain/reservation_models.dart';

final reservationRepositoryProvider = Provider<ReservationRepository>((ref) {
  return ReservationRepository(
    ref.watch(apiClientProvider).dio,
    ref.watch(secureStorageProvider),
  );
});

class ReservationRepository {
  final Dio _dio;
  final SecureStorageService _storage;
  ReservationRepository(this._dio, this._storage);

  Future<Reservation> create({
    required String pharmacyId,
    required String medicationId,
    required int    quantity,
  }) async {
    final userId = await _storage.userId;
    final resp = await _dio.post(ApiConstants.reservations, data: {
      'customerId': userId,
      'pharmacyId': pharmacyId,
      'items': [
        {'medicationId': medicationId, 'quantity': quantity},
      ],
    });
    return Reservation.fromJson(resp.data as Map<String, dynamic>);
  }

  Future<Reservation> getById(String id) async {
    final resp = await _dio.get(ApiConstants.reservationById(id));
    return Reservation.fromJson(resp.data as Map<String, dynamic>);
  }

  Future<List<Reservation>> getMyReservations() async {
    final userId = await _storage.userId;
    if (userId == null) return [];
    final resp = await _dio.get(ApiConstants.patientReservations(userId));
    final items = resp.data as List<dynamic>;
    return items
        .map((e) => Reservation.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> cancel(String id, {String? reason}) async {
    await _dio.patch(
      ApiConstants.reservationCancel(id),
      data: if (reason != null) {'reason': reason},
    );
  }
}
