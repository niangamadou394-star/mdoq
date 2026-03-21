import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:medoq/core/router/app_router.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/core/widgets/app_button.dart';
import 'package:medoq/features/payment/domain/payment_models.dart';
import 'package:medoq/features/payment/domain/payment_provider.dart';

class PaymentMethodScreen extends ConsumerStatefulWidget {
  final String reservationId;
  final double amount;

  const PaymentMethodScreen({
    super.key,
    required this.reservationId,
    required this.amount,
  });

  @override
  ConsumerState<PaymentMethodScreen> createState() =>
      _PaymentMethodScreenState();
}

class _PaymentMethodScreenState
    extends ConsumerState<PaymentMethodScreen> {
  PaymentMethod? _selected;

  Future<void> _pay() async {
    if (_selected == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Choisissez un moyen de paiement.')),
      );
      return;
    }

    final payment = await ref.read(initiatePaymentProvider.notifier).initiate(
      reservationId: widget.reservationId,
      method:        _selected!,
    );

    if (!mounted || payment == null) return;

    if (payment.checkoutUrl != null) {
      // Wave: open checkout URL in browser
      final uri = Uri.parse(payment.checkoutUrl!);
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      }
    }

    // Navigate to confirm screen to poll status
    if (mounted) {
      context.push(AppRoutes.paymentConfirm, extra: {
        'paymentId': payment.id,
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(initiatePaymentProvider);
    final isLoading = state is InitiateLoading;

    ref.listen(initiatePaymentProvider, (_, next) {
      if (next is InitiateError) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(next.message),
            backgroundColor: AppColors.error,
          ),
        );
      }
    });

    return Scaffold(
      appBar: AppBar(title: const Text('Choisir un paiement')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // ── Amount summary ─────────────────────────────────
              Container(
                padding: const EdgeInsets.all(AppSpacing.md),
                decoration: BoxDecoration(
                  color: AppColors.primary.withOpacity(0.06),
                  borderRadius: BorderRadius.circular(AppRadius.md),
                  border: Border.all(
                    color: AppColors.primary.withOpacity(0.2),
                  ),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.receipt_outlined,
                        color: AppColors.primary, size: 28),
                    const SizedBox(width: AppSpacing.md),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Montant à payer', style: AppTextStyles.caption),
                        Text(
                          '${widget.amount.toStringAsFixed(0)} FCFA',
                          style: AppTextStyles.h2.copyWith(
                            color: AppColors.primary,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.xl),

              Text('Méthode de paiement', style: AppTextStyles.h3),
              const SizedBox(height: AppSpacing.md),

              // ── Wave ───────────────────────────────────────────
              _MethodTile(
                method:      PaymentMethod.wave,
                selected:    _selected == PaymentMethod.wave,
                onTap:       () => setState(() => _selected = PaymentMethod.wave),
                icon:        '🌊',
                title:       'Wave',
                subtitle:    'Paiement rapide et sécurisé',
                accentColor: const Color(0xFF00A9DF),
                features: const [
                  'Aucuns frais supplémentaires',
                  'Confirmation instantanée',
                  'Application Wave requise',
                ],
              ),
              const SizedBox(height: AppSpacing.md),

              // ── Orange Money ───────────────────────────────────
              _MethodTile(
                method:      PaymentMethod.orangeMoney,
                selected:    _selected == PaymentMethod.orangeMoney,
                onTap:       () => setState(() => _selected = PaymentMethod.orangeMoney),
                icon:        '🟠',
                title:       'Orange Money',
                subtitle:    'Paiement par confirmation USSD',
                accentColor: const Color(0xFFFF6600),
                features: const [
                  'Confirmation par code USSD',
                  'Compatible tous réseaux Orange',
                  'Sûr et fiable',
                ],
              ),

              const Spacer(),

              // ── Security note ──────────────────────────────────
              Row(
                children: [
                  const Icon(Icons.lock_outlined,
                      size: 14, color: AppColors.textSecondary),
                  const SizedBox(width: AppSpacing.xs),
                  Expanded(
                    child: Text(
                      'Paiement sécurisé — vos données sont chiffrées.',
                      style: AppTextStyles.caption,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.md),

              AppButton(
                label:     'Continuer vers le paiement',
                isLoading: isLoading,
                onPressed: _pay,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── Method tile ───────────────────────────────────────────────────────────────

class _MethodTile extends StatelessWidget {
  final PaymentMethod method;
  final bool          selected;
  final VoidCallback  onTap;
  final String        icon;
  final String        title;
  final String        subtitle;
  final Color         accentColor;
  final List<String>  features;

  const _MethodTile({
    required this.method,
    required this.selected,
    required this.onTap,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.accentColor,
    required this.features,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      decoration: BoxDecoration(
        color: selected
            ? accentColor.withOpacity(0.06)
            : AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        border: Border.all(
          color: selected ? accentColor : AppColors.border,
          width: selected ? 2 : 1,
        ),
        boxShadow: selected
            ? [
                BoxShadow(
                  color: accentColor.withOpacity(0.15),
                  blurRadius: 12,
                  offset: const Offset(0, 4),
                ),
              ]
            : null,
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Text(icon, style: const TextStyle(fontSize: 32)),
                  const SizedBox(width: AppSpacing.md),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(title, style: AppTextStyles.h3),
                        Text(subtitle, style: AppTextStyles.bodySmall),
                      ],
                    ),
                  ),
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    width: 22,
                    height: 22,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color:  selected ? accentColor : Colors.transparent,
                      border: Border.all(
                        color: selected ? accentColor : AppColors.border,
                        width: 2,
                      ),
                    ),
                    child: selected
                        ? const Icon(Icons.check, size: 12, color: Colors.white)
                        : null,
                  ),
                ],
              ),
              if (selected) ...[
                const SizedBox(height: AppSpacing.md),
                const Divider(),
                const SizedBox(height: AppSpacing.sm),
                ...features.map(
                  (f) => Padding(
                    padding: const EdgeInsets.only(bottom: 4),
                    child: Row(
                      children: [
                        Icon(Icons.check_circle,
                            size: 14, color: accentColor),
                        const SizedBox(width: AppSpacing.xs),
                        Text(f, style: AppTextStyles.bodySmall),
                      ],
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
