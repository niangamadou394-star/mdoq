import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:shimmer/shimmer.dart';
import 'package:medoq/core/router/app_router.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/features/search/domain/search_provider.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  final _searchCt = TextEditingController();

  @override
  void dispose() {
    _searchCt.dispose();
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
    context.push('${AppRoutes.searchResults}?q=${Uri.encodeComponent(trimmed)}');
  }

  @override
  Widget build(BuildContext context) {
    final popularAsync = ref.watch(popularMedicationsProvider);
    final location     = ref.watch(locationProvider);

    return Scaffold(
      body: CustomScrollView(
        slivers: [
          // ── Header ──────────────────────────────────────────────
          SliverToBoxAdapter(
            child: Container(
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [AppColors.primary, AppColors.primaryLight],
                ),
              ),
              padding: EdgeInsets.fromLTRB(
                AppSpacing.lg,
                MediaQuery.of(context).padding.top + AppSpacing.lg,
                AppSpacing.lg,
                AppSpacing.xl,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Bonjour 👋',
                            style: AppTextStyles.bodyMedium.copyWith(
                              color: Colors.white70,
                            ),
                          ),
                          Text(
                            'Trouvez vos médicaments',
                            style: AppTextStyles.h2.copyWith(color: Colors.white),
                          ),
                        ],
                      ),
                      IconButton(
                        icon: const Icon(Icons.account_circle_outlined,
                            color: Colors.white, size: 32),
                        onPressed: () {},
                      ),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.lg),

                  // ── Search bar ─────────────────────────────────
                  Hero(
                    tag: 'search_bar',
                    child: Material(
                      color: Colors.transparent,
                      child: TextField(
                        controller:      _searchCt,
                        textInputAction: TextInputAction.search,
                        onSubmitted:     _search,
                        decoration: InputDecoration(
                          filled:    true,
                          fillColor: AppColors.surface,
                          hintText:  'Paracétamol, Amoxicilline...',
                          prefixIcon: const Icon(Icons.search, color: AppColors.textSecondary),
                          suffixIcon: IconButton(
                            icon: const Icon(Icons.tune, color: AppColors.textSecondary),
                            onPressed: () {},
                          ),
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
                            borderSide: const BorderSide(
                              color: AppColors.accent,
                              width: 2,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),

                  // ── Location indicator ─────────────────────────
                  const SizedBox(height: AppSpacing.sm),
                  location.when(
                    data: (pos) => Row(
                      children: [
                        Icon(
                          pos != null
                              ? Icons.location_on
                              : Icons.location_off_outlined,
                          size: 14,
                          color: pos != null
                              ? AppColors.accent
                              : Colors.white54,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          pos != null
                              ? 'Dakar, Sénégal — ${pos.latitude.toStringAsFixed(2)}°N'
                              : 'Localisation désactivée',
                          style: AppTextStyles.caption.copyWith(
                            color: pos != null
                                ? Colors.white70
                                : Colors.white54,
                          ),
                        ),
                      ],
                    ),
                    loading: () => const SizedBox.shrink(),
                    error: (_, __) => const SizedBox.shrink(),
                  ),
                ],
              ),
            ),
          ),

          // ── Quick actions ────────────────────────────────────────
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(AppSpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Accès rapide', style: AppTextStyles.h3),
                  const SizedBox(height: AppSpacing.md),
                  Row(
                    children: [
                      _QuickAction(
                        icon:  Icons.map_outlined,
                        label: 'Carte',
                        color: AppColors.accent,
                        onTap: () => context.go(AppRoutes.map),
                      ),
                      const SizedBox(width: AppSpacing.md),
                      _QuickAction(
                        icon:  Icons.receipt_long_outlined,
                        label: 'Réservations',
                        color: AppColors.primary,
                        onTap: () => context.go(AppRoutes.myReservations),
                      ),
                      const SizedBox(width: AppSpacing.md),
                      _QuickAction(
                        icon:  Icons.local_pharmacy_outlined,
                        label: 'Pharmacies',
                        color: AppColors.available,
                        onTap: () {},
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          // ── Popular medications ──────────────────────────────────
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(
                AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.sm,
              ),
              child: Text('Médicaments populaires', style: AppTextStyles.h3),
            ),
          ),

          popularAsync.when(
            data: (meds) => SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
              sliver: SliverGrid.builder(
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2,
                  mainAxisSpacing: AppSpacing.sm,
                  crossAxisSpacing: AppSpacing.sm,
                  childAspectRatio: 2.2,
                ),
                itemCount: meds.length,
                itemBuilder: (_, i) {
                  final m = meds[i];
                  return _PopularChip(
                    name: m.name,
                    onTap: () => _search(m.name),
                  );
                },
              ),
            ),
            loading: () => SliverToBoxAdapter(
              child: _buildPopularShimmer(),
            ),
            error: (_, __) => const SliverToBoxAdapter(child: SizedBox.shrink()),
          ),

          const SliverToBoxAdapter(child: SizedBox(height: AppSpacing.xxl)),
        ],
      ),
    );
  }

  Widget _buildPopularShimmer() {
    return Shimmer.fromColors(
      baseColor:  Colors.grey.shade200,
      highlightColor: Colors.grey.shade100,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
        child: Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.sm,
          children: List.generate(
            8,
            (_) => Container(
              width: 120,
              height: 36,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(AppRadius.full),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// ── Quick action widget ───────────────────────────────────────────────────────

class _QuickAction extends StatelessWidget {
  final IconData icon;
  final String   label;
  final Color    color;
  final VoidCallback onTap;

  const _QuickAction({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppRadius.md),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
          decoration: BoxDecoration(
            color: color.withOpacity(0.08),
            borderRadius: BorderRadius.circular(AppRadius.md),
            border: Border.all(color: color.withOpacity(0.2)),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, color: color, size: 28),
              const SizedBox(height: AppSpacing.xs),
              Text(
                label,
                style: AppTextStyles.caption.copyWith(
                  color: color,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── Popular chip ──────────────────────────────────────────────────────────────

class _PopularChip extends StatelessWidget {
  final String label;
  final VoidCallback onTap;
  const _PopularChip({required this.label, required this.onTap, String? name})
      : label = name ?? label;

  @override
  Widget build(BuildContext context) {
    return ActionChip(
      label: Text(label, overflow: TextOverflow.ellipsis),
      avatar: const Icon(Icons.medication, size: 16),
      onPressed: onTap,
      backgroundColor: AppColors.inputFill,
      side: const BorderSide(color: AppColors.border),
      labelStyle: AppTextStyles.labelMedium,
    );
  }
}
