import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:medoq/core/constants/api_constants.dart';
import 'package:medoq/core/network/api_client.dart';
import 'package:medoq/features/search/domain/search_models.dart';

final searchRepositoryProvider = Provider<SearchRepository>((ref) {
  return SearchRepository(ref.watch(apiClientProvider).dio);
});

class SearchRepository {
  final Dio _dio;
  SearchRepository(this._dio);

  Future<List<MedicationSearchResult>> search({
    required String query,
    double? lat,
    double? lng,
    double radius = 5.0,
  }) async {
    final resp = await _dio.get(
      ApiConstants.medicationsSearch,
      queryParameters: {
        'q':      query,
        if (lat != null) 'lat': lat,
        if (lng != null) 'lng': lng,
        'radius': radius,
      },
    );

    final data = resp.data as Map<String, dynamic>;
    final items = data['items'] as List<dynamic>;
    return items
        .map((e) => MedicationSearchResult.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<MedicationSearchResult> getMedicationDetail({
    required String id,
    double? lat,
    double? lng,
    double radius = 5.0,
  }) async {
    final resp = await _dio.get(
      ApiConstants.medicationDetail(id),
      queryParameters: {
        if (lat != null) 'lat': lat,
        if (lng != null) 'lng': lng,
        'radius': radius,
      },
    );
    return MedicationSearchResult.fromJson(resp.data as Map<String, dynamic>);
  }

  Future<List<PopularMedication>> getPopular() async {
    final resp = await _dio.get(ApiConstants.medicationsPopular);
    final items = resp.data as List<dynamic>;
    return items
        .map((e) => PopularMedication.fromJson(e as Map<String, dynamic>))
        .toList();
  }
}
