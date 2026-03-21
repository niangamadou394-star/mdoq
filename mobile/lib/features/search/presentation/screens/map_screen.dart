import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:medoq/core/constants/app_constants.dart';
import 'package:medoq/core/router/app_router.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/features/search/domain/search_models.dart';
import 'package:medoq/features/search/domain/search_provider.dart';
import 'package:medoq/features/search/presentation/widgets/stock_badge_chip.dart';

class MapScreen extends ConsumerStatefulWidget {
  const MapScreen({super.key});

  @override
  ConsumerState<MapScreen> createState() => _MapScreenState();
}

class _MapScreenState extends ConsumerState<MapScreen> {
  GoogleMapController? _mapCtrl;
  final _searchCt = TextEditingController();
  MedicationSearchResult? _selected;

  Set<Marker> _buildMarkers(List<MedicationSearchResult> results) {
    final markers = <Marker>{};

    for (final med in results) {
      for (final ph in med.pharmacies) {
        final color = switch (ph.badge) {
          StockBadge.available  => BitmapDescriptor.hueGreen,
          StockBadge.limited    => BitmapDescriptor.hueOrange,
          StockBadge.outOfStock => BitmapDescriptor.hueRed,
        };

        markers.add(
          Marker(
            markerId: MarkerId('${med.id}_${ph.pharmacyId}'),
            position: LatLng(ph.latitude, ph.longitude),
            icon:     BitmapDescriptor.defaultMarkerWithHue(color),
            infoWindow: InfoWindow(
              title:   ph.pharmacyName,
              snippet: '${ph.distanceKm.toStringAsFixed(1)} km — '
                       '${ph.unitPrice.toStringAsFixed(0)} FCFA',
              onTap: () => context.push(
                AppRoutes.medicationDetailPath(med.id),
                extra: {'pharmacyId': ph.pharmacyId},
              ),
            ),
          ),
        );
      }
    }

    return markers;
  }

  @override
  void dispose() {
    _searchCt.dispose();
    _mapCtrl?.dispose();
    super.dispose();
  }

  void _search(String q) {
    final trimmed = q.trim();
    if (trimmed.isEmpty) return;

    final location = ref.read(locationProvider).valueOrNull;
    ref.read(searchParamsProvider.notifier).state = SearchParams(
      query: trimmed,
      lat:   location?.latitude,
      lng:   location?.longitude,
    );
  }

  @override
  Widget build(BuildContext context) {
    final locationAsync = ref.watch(locationProvider);
    final resultsAsync  = ref.watch(searchResultsProvider);

    final initialCamera = locationAsync.when(
      data: (pos) => pos != null
          ? CameraPosition(
              target: LatLng(pos.latitude, pos.longitude),
              zoom:   AppConstants.defaultZoom,
            )
          : const CameraPosition(
              target: LatLng(AppConstants.defaultLat, AppConstants.defaultLng),
              zoom:   AppConstants.defaultZoom,
            ),
      loading: () => const CameraPosition(
        target: LatLng(AppConstants.defaultLat, AppConstants.defaultLng),
        zoom:   AppConstants.defaultZoom,
      ),
      error: (_, __) => const CameraPosition(
        target: LatLng(AppConstants.defaultLat, AppConstants.defaultLng),
        zoom:   AppConstants.defaultZoom,
      ),
    );

    final markers = resultsAsync.when(
      data:    (r) => _buildMarkers(r),
      loading: () => <Marker>{},
      error:   (_, __) => <Marker>{},
    );

    return Scaffold(
      body: Stack(
        children: [
          // ── Map ─────────────────────────────────────────────────
          GoogleMap(
            initialCameraPosition: initialCamera,
            markers:               markers,
            myLocationEnabled:     true,
            myLocationButtonEnabled: false,
            mapToolbarEnabled:     false,
            zoomControlsEnabled:   false,
            onMapCreated: (ctrl) {
              _mapCtrl = ctrl;
              // Apply custom style (dark roads, blue water)
              ctrl.setMapStyle(_mapStyle);
            },
            onTap: (_) => setState(() => _selected = null),
          ),

          // ── Search overlay ───────────────────────────────────────
          Positioned(
            top: MediaQuery.of(context).padding.top + AppSpacing.sm,
            left: AppSpacing.md,
            right: AppSpacing.md,
            child: Hero(
              tag: 'search_bar',
              child: Material(
                elevation: 4,
                borderRadius: BorderRadius.circular(AppRadius.lg),
                child: TextField(
                  controller:      _searchCt,
                  textInputAction: TextInputAction.search,
                  onSubmitted:     _search,
                  decoration: InputDecoration(
                    hintText:  'Chercher un médicament...',
                    fillColor: AppColors.surface,
                    prefixIcon: const Icon(Icons.search),
                    suffixIcon: resultsAsync.isLoading
                        ? const Padding(
                            padding: EdgeInsets.all(12),
                            child: SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                          )
                        : null,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(AppRadius.lg),
                      borderSide: BorderSide.none,
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(AppRadius.lg),
                      borderSide: BorderSide.none,
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(AppRadius.lg),
                      borderSide: const BorderSide(color: AppColors.accent, width: 2),
                    ),
                  ),
                ),
              ),
            ),
          ),

          // ── Legend ───────────────────────────────────────────────
          Positioned(
            bottom: AppSpacing.xxl + AppSpacing.lg,
            right:  AppSpacing.md,
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(AppSpacing.sm),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    _LegendRow(color: AppColors.available,  label: 'Disponible'),
                    const SizedBox(height: AppSpacing.xs),
                    _LegendRow(color: AppColors.limited,    label: 'Limité'),
                    const SizedBox(height: AppSpacing.xs),
                    _LegendRow(color: AppColors.outOfStock, label: 'Rupture'),
                  ],
                ),
              ),
            ),
          ),

          // ── Center on me button ───────────────────────────────────
          Positioned(
            bottom: AppSpacing.xxl + AppSpacing.lg,
            left:   AppSpacing.md,
            child: FloatingActionButton.small(
              heroTag: 'center_fab',
              backgroundColor: AppColors.surface,
              foregroundColor: AppColors.primary,
              onPressed: () {
                final pos = ref.read(locationProvider).valueOrNull;
                if (pos != null) {
                  _mapCtrl?.animateCamera(
                    CameraUpdate.newLatLng(
                      LatLng(pos.latitude, pos.longitude),
                    ),
                  );
                }
              },
              child: const Icon(Icons.my_location),
            ),
          ),

          // ── Result count badge ────────────────────────────────────
          if (resultsAsync.hasValue && resultsAsync.value!.isNotEmpty)
            Positioned(
              top: MediaQuery.of(context).padding.top + 70,
              left: 0,
              right: 0,
              child: Center(
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.md,
                    vertical: AppSpacing.xs,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.primary,
                    borderRadius: BorderRadius.circular(AppRadius.full),
                  ),
                  child: Text(
                    '${resultsAsync.value!.length} résultat'
                    '${resultsAsync.value!.length > 1 ? 's' : ''}',
                    style: AppTextStyles.caption.copyWith(color: Colors.white),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

// ── Legend row ────────────────────────────────────────────────────────────────

class _LegendRow extends StatelessWidget {
  final Color  color;
  final String label;
  const _LegendRow({required this.color, required this.label});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(Icons.location_on, color: color, size: 16),
        const SizedBox(width: AppSpacing.xs),
        Text(label, style: AppTextStyles.caption),
      ],
    );
  }
}

// ── Minimal map style — subtle, clean ─────────────────────────────────────────

const _mapStyle = '''
[
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#a0d6e8"}]},
  {"featureType":"landscape","elementType":"geometry","stylers":[{"color":"#f5f5f5"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#ffffff"}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#f3f3f3"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#d5e8c4"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]},
  {"featureType":"poi","stylers":[{"visibility":"off"}]},
  {"featureType":"administrative","elementType":"labels.text.fill","stylers":[{"color":"#1A3C5E"}]}
]
''';
