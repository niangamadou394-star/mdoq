import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:medoq/core/router/app_router.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/core/widgets/app_button.dart';
import 'package:medoq/core/widgets/error_view.dart';
import 'package:medoq/features/reservation/domain/reservation_models.dart';
import 'package:medoq/features/reservation/domain/reservation_provider.dart';
import 'package:medoq/features/reservation/presentation/widgets/reservation_status_chip.dart';

class ReservationConfirmScreen extends ConsumerStatefulWidget {
  final String reservationId;
  const ReservationConfirmScreen({super.key, required this.reservationId});

  @override
  ConsumerState<ReservationConfirmScreen> createState() =>
      _ReservationConfirmScreenState();
}

class _ReservationConfirmScreenState
    extends ConsumerState<ReservationConfirmScreen> {
  Timer? _timer;
  Duration _timeLeft = Duration.zero;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      setState(() {});
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  String _formatDuration(Duration d) {
    if (d <= Duration.zero) return 'Expirée';
    final h = d.inHours;
    final m = d.inMinutes.remainder(60);
    final s = d.inSeconds.remainder(60);
    if (h > 0) return '${h}h ${m.toString().padLeft(2, '0')}min';
    if (m > 0) return '${m}min ${s.toString().padLeft(2, '0')}s';
    return '${s}s';
  }

  Future<void> _cancel(Reservation r) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Annuler la réservation ?'),
        content: const Text(
          'Cette action est irréversible. Votre réservation sera annulée.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Retour'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: AppColors.error),
            child: const Text('Annuler la réservation'),
          ),
        ],
      ),
    );
    if (confirm == true && mounted) {
      await ref.read(cancelReservationProvider)(r.id);
      ref.invalidate(reservationDetailProvider(r.id));
      ref.invalidate(myReservationsProvider);
    }
  }

  @override
  Widget build(BuildContext context) {
    final resAsync = ref.watch(
      reservationDetailProvider(widget.reservationId),
    );

    return Scaffold(
      appBar: AppBar(title: const Text('Réservation')),
      body: resAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error:   (_, __) => const ErrorView(
          message: 'Impossible de charger la réservation.',
        ),
        data: (r) => Column(
          children: [
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(AppSpacing.lg),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // ── Status banner ────────────────────────
                    _StatusBanner(reservation: r, formatDuration: _formatDuration),
                    const SizedBox(height: AppSpacing.lg),

                    // ── Pharmacy info ────────────────────────
                    Text('Pharmacie', style: AppTextStyles.h3),
                    const SizedBox(height: AppSpacing.sm),
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(AppSpacing.md),
                        child: Row(
                          children: [
                            Container(
                              width: 44,
                              height: 44,
                              decoration: BoxDecoration(
                                color: AppColors.accent.withOpacity(0.1),
                                borderRadius: BorderRadius.circular(AppRadius.md),
                              ),
                              child: const Icon(
                                Icons.local_pharmacy,
                                color: AppColors.accent,
                              ),
                            ),
                            const SizedBox(width: AppSpacing.md),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(r.pharmacyName, style: AppTextStyles.labelLarge),
                                  const SizedBox(height: 2),
                                  Text(r.pharmacyAddress, style: AppTextStyles.bodySmall),
                                  Text(r.pharmacyPhone,   style: AppTextStyles.bodySmall),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: AppSpacing.lg),

                    // ── Items list ───────────────────────────
                    Text('Médicaments', style: AppTextStyles.h3),
                    const SizedBox(height: AppSpacing.sm),
                    Card(
                      child: Column(
                        children: [
                          ...r.items.asMap().entries.map((entry) {
                            final i    = entry.key;
                            final item = entry.value;
                            return Column(
                              children: [
                                Padding(
                                  padding: const EdgeInsets.all(AppSpacing.md),
                                  child: Row(
                                    children: [
                                      Expanded(
                                        child: Column(
                                          crossAxisAlignment: CrossAxisAlignment.start,
                                          children: [
                                            Text(item.medicationName,
                                                style: AppTextStyles.labelLarge),
                                            if (item.strength != null)
                                              Text(item.strength!,
                                                  style: AppTextStyles.bodySmall),
                                          ],
                                        ),
                                      ),
                                      Text(
                                        '${item.quantity} × ${item.unitPrice.toStringAsFixed(0)} FCFA',
                                        style: AppTextStyles.bodySmall,
                                      ),
                                      const SizedBox(width: AppSpacing.sm),
                                      Text(
                                        '${item.subtotal.toStringAsFixed(0)} FCFA',
                                        style: AppTextStyles.labelLarge.copyWith(
                                          color: AppColors.primary,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                if (i < r.items.length - 1) const Divider(height: 1),
                              ],
                            );
                          }),
                          const Divider(height: 1),
                          Padding(
                            padding: const EdgeInsets.all(AppSpacing.md),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Text('Total', style: AppTextStyles.labelLarge),
                                Text(
                                  '${r.totalAmount.toStringAsFixed(0)} FCFA',
                                  style: AppTextStyles.h3.copyWith(
                                    color: AppColors.primary,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: AppSpacing.lg),

                    // ── Reference ────────────────────────────
                    _RefCard(reference: r.reference),
                  ],
                ),
              ),
            ),

            // ── Actions ───────────────────────────────────────
            _ActionBar(
              reservation: r,
              onPay:    () => context.push(AppRoutes.paymentMethod, extra: {
                'reservationId': r.id,
                'amount':        r.totalAmount,
              }),
              onCancel: () => _cancel(r),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Status banner ─────────────────────────────────────────────────────────────

class _StatusBanner extends StatelessWidget {
  final Reservation reservation;
  final String Function(Duration) formatDuration;
  const _StatusBanner({required this.reservation, required this.formatDuration});

  Color get _bgColor => switch (reservation.status) {
    ReservationStatus.confirmed => AppColors.statusConfirmed.withOpacity(0.08),
    ReservationStatus.paid      => AppColors.statusPaid.withOpacity(0.08),
    ReservationStatus.ready     => AppColors.statusReady.withOpacity(0.08),
    ReservationStatus.completed => AppColors.statusCompleted.withOpacity(0.08),
    ReservationStatus.cancelled => AppColors.statusCancelled.withOpacity(0.08),
    ReservationStatus.expired   => AppColors.statusExpired.withOpacity(0.08),
    _                           => AppColors.statusPending.withOpacity(0.08),
  };

  @override
  Widget build(BuildContext context) {
    final timeLeft = reservation.timeLeft;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: _bgColor,
        borderRadius: BorderRadius.circular(AppRadius.md),
        border: Border.all(color: _bgColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ReservationStatusChip(status: reservation.status),
          const SizedBox(height: AppSpacing.sm),
          if (timeLeft != null && reservation.status.isActive) ...[
            Row(
              children: [
                const Icon(Icons.timer_outlined, size: 16,
                    color: AppColors.textSecondary),
                const SizedBox(width: 4),
                Text(
                  timeLeft <= Duration.zero
                      ? 'Réservation expirée'
                      : 'Expire dans ${formatDuration(timeLeft)}',
                  style: AppTextStyles.bodySmall.copyWith(
                    color: timeLeft.inMinutes < 30
                        ? AppColors.warning
                        : AppColors.textSecondary,
                    fontWeight: timeLeft.inMinutes < 30
                        ? FontWeight.w600
                        : FontWeight.w400,
                  ),
                ),
              ],
            ),
          ],
          if (reservation.status == ReservationStatus.ready) ...[
            const SizedBox(height: AppSpacing.xs),
            Text(
              '✅ Votre commande est prête ! Présentez-vous en pharmacie.',
              style: AppTextStyles.bodySmall.copyWith(
                color: AppColors.statusReady,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

// ── Reference card ────────────────────────────────────────────────────────────

class _RefCard extends StatelessWidget {
  final String reference;
  const _RefCard({required this.reference});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.inputFill,
        borderRadius: BorderRadius.circular(AppRadius.md),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          const Icon(Icons.tag, size: 20, color: AppColors.textSecondary),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Référence', style: AppTextStyles.caption),
                Text(reference, style: AppTextStyles.labelLarge),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.copy, size: 18),
            onPressed: () {},
            tooltip: 'Copier',
          ),
        ],
      ),
    );
  }
}

// ── Action bar ────────────────────────────────────────────────────────────────

class _ActionBar extends StatelessWidget {
  final Reservation  reservation;
  final VoidCallback onPay;
  final VoidCallback onCancel;
  const _ActionBar({required this.reservation, required this.onPay, required this.onCancel});

  @override
  Widget build(BuildContext context) {
    final r = reservation;
    if (r.status.isTerminal) return const SizedBox.shrink();

    return Container(
      padding: EdgeInsets.fromLTRB(
        AppSpacing.lg,
        AppSpacing.md,
        AppSpacing.lg,
        MediaQuery.of(context).padding.bottom + AppSpacing.md,
      ),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (r.status.canPay)
            AppButton(
              label:     'Payer maintenant',
              onPressed: onPay,
              prefixIcon: const Icon(Icons.payment, color: Colors.white),
            ),
          if (r.status == ReservationStatus.pending ||
              r.status == ReservationStatus.confirmed) ...[
            if (r.status.canPay) const SizedBox(height: AppSpacing.sm),
            AppButton(
              label:           'Annuler la réservation',
              onPressed:       onCancel,
              outlined:        true,
              backgroundColor: AppColors.error,
            ),
          ],
        ],
      ),
    );
  }
}
