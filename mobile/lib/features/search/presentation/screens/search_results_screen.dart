import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:shimmer/shimmer.dart';
import 'package:medoq/core/router/app_router.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/core/widgets/error_view.dart';
import 'package:medoq/features/search/domain/search_models.dart';
import 'package:medoq/features/search/domain/search_provider.dart';
import 'package:medoq/features/search/presentation/widgets/medication_card.dart';
import 'package:medoq/features/search/presentation/widgets/stock_badge_chip.dart';

class SearchResultsScreen extends ConsumerStatefulWidget {
  final String query;
  const SearchResultsScreen({super.key, required this.query});

  @override
  ConsumerState<SearchResultsScreen> createState() =>
      _SearchResultsScreenState();
}

class _SearchResultsScreenState
    extends ConsumerState<SearchResultsScreen> {
  StockBadge? _filterBadge;
  String      _sortBy = 'distance'; // 'distance' | 'price'

  @override
  void initState() {
    super.initState();
    // Trigger search if not already set
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final current = ref.read(searchParamsProvider);
      if (current?.query != widget.query) {
        final location = ref.read(locationProvider).valueOrNull;
        ref.read(searchParamsProvider.notifier).state = SearchParams(
          query: widget.query,
          lat:   location?.latitude,
          lng:   location?.longitude,
        );
      }
    });
  }

  List<MedicationSearchResult> _apply(List<MedicationSearchResult> list) {
    var result = list.where((m) {
      if (_filterBadge == null) return true;
      return m.bestBadge == _filterBadge;
    }).toList();

    result.sort((a, b) {
      if (_sortBy == 'price') {
        final ap = a.minPrice ?? double.infinity;
        final bp = b.minPrice ?? double.infinity;
        return ap.compareTo(bp);
      }
      final ad = a.minDistanceKm ?? double.infinity;
      final bd = b.minDistanceKm ?? double.infinity;
      return ad.compareTo(bd);
    });

    return result;
  }

  @override
  Widget build(BuildContext context) {
    final resultsAsync = ref.watch(searchResultsProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text('"${widget.query}"', style: AppTextStyles.h3),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(56),
          child: _FilterBar(
            selected:  _filterBadge,
            sortBy:    _sortBy,
            onFilter:  (b) => setState(() => _filterBadge = b),
            onSort:    (s) => setState(() => _sortBy = s),
          ),
        ),
      ),
      body: resultsAsync.when(
        loading: () => _ShimmerList(),
        error:   (e, _) => ErrorView(
          message: 'Erreur de chargement. Vérifiez votre connexion.',
          onRetry: () => ref.invalidate(searchResultsProvider),
        ),
        data: (all) {
          final items = _apply(all);

          if (items.isEmpty) {
            return _EmptyResults(query: widget.query);
          }

          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  AppSpacing.lg, AppSpacing.md, AppSpacing.lg, 0,
                ),
                child: Text(
                  '${items.length} résultat${items.length > 1 ? 's' : ''}',
                  style: AppTextStyles.bodySmall,
                ),
              ),
              Expanded(
                child: ListView.separated(
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  itemCount: items.length,
                  separatorBuilder: (_, __) =>
                      const SizedBox(height: AppSpacing.md),
                  itemBuilder: (_, i) => MedicationCard(
                    medication: items[i],
                    onTap: () => context.push(
                      AppRoutes.medicationDetailPath(items[i].id),
                      extra: {
                        'pharmacyId': items[i].pharmacies.isNotEmpty
                            ? items[i].pharmacies.first.pharmacyId
                            : null,
                      },
                    ),
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

// ── Filter bar ────────────────────────────────────────────────────────────────

class _FilterBar extends StatelessWidget {
  final StockBadge? selected;
  final String      sortBy;
  final void Function(StockBadge?) onFilter;
  final void Function(String) onSort;

  const _FilterBar({
    required this.selected,
    required this.sortBy,
    required this.onFilter,
    required this.onSort,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      color: AppColors.surface,
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.md,
        vertical: AppSpacing.sm,
      ),
      child: Row(
        children: [
          // Stock filters
          _FilterChip(
            label: 'Tous',
            active: selected == null,
            onTap: () => onFilter(null),
          ),
          const SizedBox(width: AppSpacing.xs),
          _FilterChip(
            label: 'Disponible',
            active: selected == StockBadge.available,
            color: AppColors.available,
            onTap: () => onFilter(StockBadge.available),
          ),
          const SizedBox(width: AppSpacing.xs),
          _FilterChip(
            label: 'Limité',
            active: selected == StockBadge.limited,
            color: AppColors.limited,
            onTap: () => onFilter(StockBadge.limited),
          ),
          const Spacer(),

          // Sort
          PopupMenuButton<String>(
            initialValue: sortBy,
            onSelected: onSort,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(AppRadius.md),
            ),
            child: Row(
              children: [
                const Icon(Icons.sort, size: 18, color: AppColors.textSecondary),
                const SizedBox(width: 4),
                Text(
                  sortBy == 'price' ? 'Prix' : 'Distance',
                  style: AppTextStyles.labelMedium,
                ),
              ],
            ),
            itemBuilder: (_) => [
              const PopupMenuItem(value: 'distance', child: Text('Distance')),
              const PopupMenuItem(value: 'price',    child: Text('Prix')),
            ],
          ),
        ],
      ),
    );
  }
}

class _FilterChip extends StatelessWidget {
  final String  label;
  final bool    active;
  final Color?  color;
  final VoidCallback onTap;

  const _FilterChip({
    required this.label,
    required this.active,
    this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppColors.primary;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.sm, vertical: AppSpacing.xs,
        ),
        decoration: BoxDecoration(
          color: active ? c.withOpacity(0.12) : AppColors.inputFill,
          borderRadius: BorderRadius.circular(AppRadius.full),
          border: Border.all(
            color: active ? c : AppColors.border,
          ),
        ),
        child: Text(
          label,
          style: AppTextStyles.caption.copyWith(
            color:       active ? c : AppColors.textSecondary,
            fontWeight:  active ? FontWeight.w600 : FontWeight.w400,
          ),
        ),
      ),
    );
  }
}

// ── Empty state ───────────────────────────────────────────────────────────────

class _EmptyResults extends StatelessWidget {
  final String query;
  const _EmptyResults({required this.query});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.search_off, size: 64, color: Colors.grey.shade300),
            const SizedBox(height: AppSpacing.md),
            Text(
              'Aucun résultat pour\n"$query"',
              style: AppTextStyles.h3.copyWith(color: AppColors.textSecondary),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              'Essayez avec un autre nom\nou vérifiez l\'orthographe.',
              style: AppTextStyles.bodyMedium.copyWith(
                color: AppColors.textSecondary,
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}

// ── Shimmer loading ───────────────────────────────────────────────────────────

class _ShimmerList extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Shimmer.fromColors(
      baseColor:      Colors.grey.shade200,
      highlightColor: Colors.grey.shade100,
      child: ListView.separated(
        padding: const EdgeInsets.all(AppSpacing.lg),
        itemCount: 5,
        separatorBuilder: (_, __) =>
            const SizedBox(height: AppSpacing.md),
        itemBuilder: (_, __) => Container(
          height: 110,
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(AppRadius.lg),
          ),
        ),
      ),
    );
  }
}
