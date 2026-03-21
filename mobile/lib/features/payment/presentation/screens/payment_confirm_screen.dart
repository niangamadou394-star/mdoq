import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:medoq/core/router/app_router.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/core/widgets/app_button.dart';
import 'package:medoq/features/payment/domain/payment_models.dart';
import 'package:medoq/features/payment/domain/payment_provider.dart';

class PaymentConfirmScreen extends ConsumerStatefulWidget {
  final String paymentId;
  const PaymentConfirmScreen({super.key, required this.paymentId});

  @override
  ConsumerState<PaymentConfirmScreen> createState() =>
      _PaymentConfirmScreenState();
}

class _PaymentConfirmScreenState
    extends ConsumerState<PaymentConfirmScreen>
    with SingleTickerProviderStateMixin {
  Timer?              _pollTimer;
  int                 _pollCount = 0;
  static const _maxPoll = 24; // 2 min total (5s interval)
  late AnimationController _pulseCtrl;
  late Animation<double>   _pulseAnim;

  @override
  void initState() {
    super.initState();

    _pulseCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    )..repeat(reverse: true);

    _pulseAnim = Tween<double>(begin: 0.8, end: 1.0).animate(
      CurvedAnimation(parent: _pulseCtrl, curve: Curves.easeInOut),
    );

    _startPolling();
  }

  void _startPolling() {
    _pollTimer = Timer.periodic(const Duration(seconds: 5), (_) async {
      _pollCount++;

      // Refresh the payment
      ref.invalidate(paymentDetailProvider(widget.paymentId));

      // Check status
      final payment =
          ref.read(paymentDetailProvider(widget.paymentId)).valueOrNull;
      if (payment?.status == PaymentStatus.completed) {
        _pollTimer?.cancel();
        _pulseCtrl.stop();
      } else if (payment?.status == PaymentStatus.failed ||
                 _pollCount >= _maxPoll) {
        _pollTimer?.cancel();
        _pulseCtrl.stop();
      }
    });
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    _pulseCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final paymentAsync = ref.watch(paymentDetailProvider(widget.paymentId));

    return PopScope(
      canPop: false,
      child: Scaffold(
        body: SafeArea(
          child: paymentAsync.when(
            loading: () => _PendingView(pulseAnim: _pulseAnim),
            error: (_, __) => _ErrorView(
              onRetry: () => ref.invalidate(
                paymentDetailProvider(widget.paymentId),
              ),
            ),
            data: (payment) => switch (payment.status) {
              PaymentStatus.completed => _SuccessView(payment: payment),
              PaymentStatus.failed    => _FailedView(
                  onRetry: () => context.pop(),
                ),
              PaymentStatus.pending   => _PendingView(pulseAnim: _pulseAnim),
              _                       => _PendingView(pulseAnim: _pulseAnim),
            },
          ),
        ),
      ),
    );
  }
}

// ── Pending view ──────────────────────────────────────────────────────────────

class _PendingView extends StatelessWidget {
  final Animation<double> pulseAnim;
  const _PendingView({required this.pulseAnim});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          ScaleTransition(
            scale: pulseAnim,
            child: Container(
              width: 120,
              height: 120,
              decoration: BoxDecoration(
                color: AppColors.primary.withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.payment_outlined,
                size: 60,
                color: AppColors.primary,
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.xl),
          Text(
            'Paiement en cours...',
            style: AppTextStyles.h2,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            'Confirmez le paiement sur votre téléphone.\n'
            'Cette page se met à jour automatiquement.',
            style: AppTextStyles.bodyMedium.copyWith(
              color: AppColors.textSecondary,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.xxl),
          const LinearProgressIndicator(
            backgroundColor: AppColors.inputFill,
            color: AppColors.accent,
          ),
          const SizedBox(height: AppSpacing.xl),
          Text(
            'Ne fermez pas cette page pendant le paiement.',
            style: AppTextStyles.caption,
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}

// ── Success view ──────────────────────────────────────────────────────────────

class _SuccessView extends ConsumerWidget {
  final Payment payment;
  const _SuccessView({required this.payment});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // ── Success animation ────────────────────────────────
          TweenAnimationBuilder<double>(
            tween: Tween(begin: 0, end: 1),
            duration: const Duration(milliseconds: 600),
            curve: Curves.elasticOut,
            builder: (_, v, child) => Transform.scale(scale: v, child: child),
            child: Container(
              width: 120,
              height: 120,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: AppColors.success.withOpacity(0.12),
                shape: BoxShape.circle,
                border: Border.all(
                  color: AppColors.success.withOpacity(0.4),
                  width: 3,
                ),
              ),
              child: const Icon(
                Icons.check_circle,
                size: 72,
                color: AppColors.success,
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.xl),

          Text(
            'Paiement réussi !',
            style: AppTextStyles.h1.copyWith(color: AppColors.success),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            '${payment.amount.toStringAsFixed(0)} FCFA',
            style: AppTextStyles.h2.copyWith(color: AppColors.textSecondary),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.xl),

          // ── Receipt card ─────────────────────────────────────
          Card(
            child: Padding(
              padding: const EdgeInsets.all(AppSpacing.md),
              child: Column(
                children: [
                  _ReceiptRow(
                    label: 'Méthode',
                    value: payment.method == PaymentMethod.wave
                        ? '🌊 Wave'
                        : '🟠 Orange Money',
                  ),
                  const Divider(),
                  if (payment.transactionRef != null) ...[
                    _ReceiptRow(
                      label: 'Référence transaction',
                      value: payment.transactionRef!,
                    ),
                    const Divider(),
                  ],
                  if (payment.paidAt != null)
                    _ReceiptRow(
                      label: 'Date',
                      value: _formatDate(payment.paidAt!),
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            'Une facture a été envoyée à votre email.',
            style: AppTextStyles.bodySmall,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.xl),

          // ── CTA ──────────────────────────────────────────────
          AppButton(
            label:     'Voir ma réservation',
            onPressed: () {
              context.go(AppRoutes.myReservations);
            },
            prefixIcon: const Icon(Icons.receipt_long, color: Colors.white),
          ),
          const SizedBox(height: AppSpacing.md),
          AppButton(
            label:     'Retour à l\'accueil',
            onPressed: () => context.go(AppRoutes.home),
            outlined:  true,
          ),
        ],
      ),
    );
  }

  String _formatDate(DateTime d) =>
      '${d.day.toString().padLeft(2, '0')}/'
      '${d.month.toString().padLeft(2, '0')}/'
      '${d.year} '
      '${d.hour.toString().padLeft(2, '0')}:'
      '${d.minute.toString().padLeft(2, '0')}';
}

class _ReceiptRow extends StatelessWidget {
  final String label;
  final String value;
  const _ReceiptRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
      child: Row(
        children: [
          Text(label, style: AppTextStyles.bodySmall),
          const Spacer(),
          Text(value, style: AppTextStyles.labelMedium),
        ],
      ),
    );
  }
}

// ── Failed view ───────────────────────────────────────────────────────────────

class _FailedView extends StatelessWidget {
  final VoidCallback onRetry;
  const _FailedView({required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            width: 120,
            height: 120,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: AppColors.error.withOpacity(0.1),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.error_outline,
              size: 72,
              color: AppColors.error,
            ),
          ),
          const SizedBox(height: AppSpacing.xl),
          Text(
            'Paiement échoué',
            style: AppTextStyles.h2,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            'Le paiement n\'a pas abouti. Votre réservation reste active.',
            style: AppTextStyles.bodyMedium.copyWith(
              color: AppColors.textSecondary,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.xxl),
          AppButton(label: 'Réessayer', onPressed: onRetry),
          const SizedBox(height: AppSpacing.md),
          AppButton(
            label:    'Retour à l\'accueil',
            outlined: true,
            onPressed: () => context.go(AppRoutes.home),
          ),
        ],
      ),
    );
  }
}

// ── Error view ────────────────────────────────────────────────────────────────

class _ErrorView extends StatelessWidget {
  final VoidCallback onRetry;
  const _ErrorView({required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.wifi_off, size: 56, color: AppColors.textSecondary),
          const SizedBox(height: AppSpacing.md),
          const Text('Erreur de connexion'),
          const SizedBox(height: AppSpacing.lg),
          OutlinedButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: const Text('Réessayer'),
          ),
        ],
      ),
    );
  }
}
