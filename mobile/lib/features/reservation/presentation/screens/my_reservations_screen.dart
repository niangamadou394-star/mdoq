import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:shimmer/shimmer.dart';
import 'package:medoq/core/router/app_router.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/core/widgets/error_view.dart';
import 'package:medoq/features/reservation/domain/reservation_models.dart';
import 'package:medoq/features/reservation/domain/reservation_provider.dart';
import 'package:medoq/features/reservation/presentation/widgets/reservation_status_chip.dart';

class MyReservationsScreen extends ConsumerStatefulWidget {
  const MyReservationsScreen({super.key});

  @override
  ConsumerState<MyReservationsScreen> createState() =>
      _MyReservationsScreenState();
}

class _MyReservationsScreenState
    extends ConsumerState<MyReservationsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabCtrl;

  @override
  void initState() {
    super.initState();
    _tabCtrl = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final resAsync = ref.watch(myReservationsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Mes réservations'),
        bottom: TabBar(
          controller: _tabCtrl,
          labelStyle: AppTextStyles.labelLarge,
          tabs: const [
            Tab(text: 'En cours'),
            Tab(text: 'Historique'),
          ],
        ),
      ),
      body: resAsync.when(
        loading: () => _ShimmerList(),
        error: (_, __) => ErrorView(
          message: 'Impossible de charger vos réservations.',
          onRetry: () => ref.invalidate(myReservationsProvider),
        ),
        data: (all) {
          final active  = all.where((r) => r.status.isActive).toList()
            ..sort((a, b) => b.createdAt.compareTo(a.createdAt));
          final history = all.where((r) => r.status.isTerminal).toList()
            ..sort((a, b) => b.createdAt.compareTo(a.createdAt));

          return TabBarView(
            controller: _tabCtrl,
            children: [
              _ReservationList(
                reservations: active,
                emptyMessage: 'Aucune réservation en cours.',
                emptyIcon: Icons.receipt_long_outlined,
              ),
              _ReservationList(
                reservations: history,
                emptyMessage: 'Aucun historique de réservation.',
                emptyIcon: Icons.history,
              ),
            ],
          );
        },
      ),
    );
  }
}

// ── Reservation list ──────────────────────────────────────────────────────────

class _ReservationList extends StatelessWidget {
  final List<Reservation> reservations;
  final String  emptyMessage;
  final IconData emptyIcon;

  const _ReservationList({
    required this.reservations,
    required this.emptyMessage,
    required this.emptyIcon,
  });

  @override
  Widget build(BuildContext context) {
    if (reservations.isEmpty) {
      return _EmptyTab(message: emptyMessage, icon: emptyIcon);
    }

    return RefreshIndicator(
      onRefresh: () async {
        // Trigger refresh via provider invalidation is done in parent
      },
      child: ListView.separated(
        padding: const EdgeInsets.all(AppSpacing.lg),
        itemCount: reservations.length,
        separatorBuilder: (_, __) => const SizedBox(height: AppSpacing.md),
        itemBuilder: (_, i) => _ReservationCard(reservation: reservations[i]),
      ),
    );
  }
}

// ── Reservation card ──────────────────────────────────────────────────────────

class _ReservationCard extends StatelessWidget {
  final Reservation reservation;
  const _ReservationCard({required this.reservation});

  @override
  Widget build(BuildContext context) {
    final r = reservation;
    final timeLeft = r.timeLeft;

    return Card(
      child: InkWell(
        onTap: () => context.push(
          AppRoutes.reservationConfirm,
          extra: {'reservationId': r.id},
        ),
        borderRadius: BorderRadius.circular(AppRadius.lg),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // ── Top row ────────────────────────────────────
              Row(
                children: [
                  Expanded(
                    child: Text(
                      r.pharmacyName,
                      style: AppTextStyles.labelLarge,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  ReservationStatusChip(status: r.status, compact: true),
                ],
              ),
              const SizedBox(height: AppSpacing.xs),

              // ── Reference ──────────────────────────────────
              Text('Réf: ${r.reference}', style: AppTextStyles.bodySmall),
              const SizedBox(height: AppSpacing.sm),

              // ── Items summary ──────────────────────────────
              Text(
                r.items.map((i) =>
                    '${i.medicationName} ×${i.quantity}').join(', '),
                style: AppTextStyles.bodySmall,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: AppSpacing.sm),
              const Divider(height: 1),
              const SizedBox(height: AppSpacing.sm),

              // ── Footer ────────────────────────────────────
              Row(
                children: [
                  // Date
                  Icon(Icons.calendar_today_outlined,
                      size: 12, color: AppColors.textSecondary),
                  const SizedBox(width: 4),
                  Text(
                    _formatDate(r.createdAt),
                    style: AppTextStyles.caption,
                  ),
                  const Spacer(),

                  // Expiry warning
                  if (timeLeft != null &&
                      timeLeft.inMinutes < 30 &&
                      r.status.isActive) ...[
                    Icon(Icons.timer_outlined,
                        size: 12, color: AppColors.warning),
                    const SizedBox(width: 4),
                    Text(
                      _formatTimeLeft(timeLeft),
                      style: AppTextStyles.caption.copyWith(
                        color: AppColors.warning,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(width: AppSpacing.sm),
                  ],

                  // Total
                  Text(
                    '${r.totalAmount.toStringAsFixed(0)} FCFA',
                    style: AppTextStyles.labelMedium.copyWith(
                      color: AppColors.primary,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _formatDate(DateTime d) {
    final now = DateTime.now();
    final diff = now.difference(d);
    if (diff.inDays == 0) return "Aujourd'hui";
    if (diff.inDays == 1) return 'Hier';
    return '${d.day}/${d.month}/${d.year}';
  }

  String _formatTimeLeft(Duration d) {
    if (d <= Duration.zero) return 'Expirée';
    if (d.inMinutes < 60) return '${d.inMinutes}min';
    return '${d.inHours}h';
  }
}

// ── Empty tab ─────────────────────────────────────────────────────────────────

class _EmptyTab extends StatelessWidget {
  final String   message;
  final IconData icon;
  const _EmptyTab({required this.message, required this.icon});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 64, color: Colors.grey.shade300),
          const SizedBox(height: AppSpacing.md),
          Text(message, style: AppTextStyles.bodyMedium.copyWith(
            color: AppColors.textSecondary,
          )),
        ],
      ),
    );
  }
}

// ── Shimmer ───────────────────────────────────────────────────────────────────

class _ShimmerList extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Shimmer.fromColors(
      baseColor:      Colors.grey.shade200,
      highlightColor: Colors.grey.shade100,
      child: ListView.separated(
        padding: const EdgeInsets.all(AppSpacing.lg),
        itemCount: 4,
        separatorBuilder: (_, __) => const SizedBox(height: AppSpacing.md),
        itemBuilder: (_, __) => Container(
          height: 130,
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(AppRadius.lg),
          ),
        ),
      ),
    );
  }
}
