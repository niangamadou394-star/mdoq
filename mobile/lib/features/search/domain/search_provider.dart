import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:medoq/features/search/data/search_repository.dart';
import 'package:medoq/features/search/domain/search_models.dart';

// ── Geolocation ───────────────────────────────────────────────────────────────

final locationProvider = FutureProvider<Position?>((ref) async {
  final perm = await Geolocator.checkPermission();
  if (perm == LocationPermission.denied) {
    final req = await Geolocator.requestPermission();
    if (req == LocationPermission.denied ||
        req == LocationPermission.deniedForever) return null;
  }
  if (perm == LocationPermission.deniedForever) return null;

  return Geolocator.getCurrentPosition(
    locationSettings: const LocationSettings(
      accuracy: LocationAccuracy.medium,
      timeLimit: Duration(seconds: 10),
    ),
  );
});

// ── Popular medications ───────────────────────────────────────────────────────

final popularMedicationsProvider =
    FutureProvider<List<PopularMedication>>((ref) {
  return ref.watch(searchRepositoryProvider).getPopular();
});

// ── Search ────────────────────────────────────────────────────────────────────

class SearchParams {
  final String query;
  final double? lat;
  final double? lng;
  const SearchParams({required this.query, this.lat, this.lng});
}

final searchParamsProvider = StateProvider<SearchParams?>((_) => null);

final searchResultsProvider =
    FutureProvider<List<MedicationSearchResult>>((ref) async {
  final params = ref.watch(searchParamsProvider);
  if (params == null || params.query.isEmpty) return [];

  return ref.watch(searchRepositoryProvider).search(
    query: params.query,
    lat:   params.lat,
    lng:   params.lng,
  );
});

// ── Medication detail ─────────────────────────────────────────────────────────

final medicationDetailProvider =
    FutureProvider.family<MedicationSearchResult, String>((ref, id) {
  final location = ref.watch(locationProvider).valueOrNull;
  return ref.watch(searchRepositoryProvider).getMedicationDetail(
    id:  id,
    lat: location?.latitude,
    lng: location?.longitude,
  );
});
