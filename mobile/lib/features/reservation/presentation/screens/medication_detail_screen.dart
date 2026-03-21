import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:medoq/core/router/app_router.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/core/widgets/app_button.dart';
import 'package:medoq/core/widgets/error_view.dart';
import 'package:medoq/features/reservation/domain/reservation_models.dart';
import 'package:medoq/features/reservation/domain/reservation_provider.dart';
import 'package:medoq/features/search/domain/search_models.dart';
import 'package:medoq/features/search/domain/search_provider.dart';
import 'package:medoq/features/search/presentation/widgets/stock_badge_chip.dart';

class MedicationDetailScreen extends ConsumerStatefulWidget {
  final String  medicationId;
  final String? pharmacyId;
  const MedicationDetailScreen({
    super.key,
    required this.medicationId,
    this.pharmacyId,
  });

  @override
  ConsumerState<MedicationDetailScreen> createState() =>
      _MedicationDetailScreenState();
}

class _MedicationDetailScreenState
    extends ConsumerState<MedicationDetailScreen> {
  PharmacyStockDto? _selectedPharmacy;
  int _quantity = 1;

  Future<void> _reserve() async {
    final ph = _selectedPharmacy;
    if (ph == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Sélectionnez une pharmacie.')),
      );
      return;
    }

    final r = await ref.read(createReservationProvider.notifier).create(
      pharmacyId:   ph.pharmacyId,
      medicationId: widget.medicationId,
      quantity:     _quantity,
    );

    if (r != null && mounted) {
      context.push(AppRoutes.reservationConfirm, extra: {
        'reservationId': r.id,
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final medAsync   = ref.watch(medicationDetailProvider(widget.medicationId));
    final createState = ref.watch(createReservationProvider);
    final isLoading  = createState is CreateLoading;

    ref.listen(createReservationProvider, (_, next) {
      if (next is CreateError) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(next.message),
            backgroundColor: AppColors.error,
          ),
        );
      }
    });

    return Scaffold(
      appBar: AppBar(
        title: medAsync.when(
          data:    (m) => Text(m.name),
          loading: () => const Text('Chargement...'),
          error:   (_, __) => const Text('Erreur'),
        ),
      ),
      body: medAsync.when(
        loading: () =>
            const Center(child: CircularProgressIndicator()),
        error: (e, _) => ErrorView(
          message: 'Impossible de charger ce médicament.',
          onRetry: () => ref.invalidate(
            medicationDetailProvider(widget.medicationId),
          ),
        ),
        data: (med) {
          // Auto-select if a specific pharmacy was pre-set
          if (_selectedPharmacy == null && widget.pharmacyId != null) {
            try {
              _selectedPharmacy = med.pharmacies.firstWhere(
                (p) => p.pharmacyId == widget.pharmacyId,
              );
            } catch (_) {}
          }

          return Column(
            children: [
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // ── Med info card ────────────────────────
                      _MedInfoCard(medication: med),
                      const SizedBox(height: AppSpacing.lg),

                      // ── Quantity selector ────────────────────
                      Text('Quantité', style: AppTextStyles.h3),
                      const SizedBox(height: AppSpacing.sm),
                      _QuantitySelector(
                        quantity: _quantity,
                        max: _selectedPharmacy?.quantity ?? 10,
                        onChanged: (q) => setState(() => _quantity = q),
                      ),
                      const SizedBox(height: AppSpacing.lg),

                      // ── Pharmacies list ──────────────────────
                      Text(
                        '${med.pharmacies.length} pharmacie'
                        '${med.pharmacies.length > 1 ? 's' : ''} disponibles',
                        style: AppTextStyles.h3,
                      ),
                      const SizedBox(height: AppSpacing.sm),

                      ...med.pharmacies.map((ph) => _PharmacyTile(
                        pharmacy: ph,
                        selected: _selectedPharmacy?.pharmacyId == ph.pharmacyId,
                        onTap: () => setState(() {
                          _selectedPharmacy = ph;
                          // Reset quantity if new pharmacy has less stock
                          if (_quantity > ph.quantity) {
                            _quantity = ph.quantity.clamp(1, ph.quantity);
                          }
                        }),
                      )),
                    ],
                  ),
                ),
              ),

              // ── Bottom CTA ────────────────────────────────────
              _BottomBar(
                selectedPharmacy: _selectedPharmacy,
                quantity:         _quantity,
                isLoading:        isLoading,
                onReserve:        _reserve,
              ),
            ],
          );
        },
      ),
    );
  }
}

// ── Med info card ─────────────────────────────────────────────────────────────

class _MedInfoCard extends StatelessWidget {
  final MedicationSearchResult medication;
  const _MedInfoCard({required this.medication});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 56,
                  height: 56,
                  decoration: BoxDecoration(
                    color: AppColors.primary.withOpacity(0.08),
                    borderRadius: BorderRadius.circular(AppRadius.md),
                  ),
                  child: const Icon(
                    Icons.medication,
                    color: AppColors.primary,
                    size: 32,
                  ),
                ),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(medication.name, style: AppTextStyles.h3),
                      if (medication.strength != null) ...[
                        const SizedBox(height: 2),
                        Text(
                          medication.strength!,
                          style: AppTextStyles.bodySmall,
                        ),
                      ],
                    ],
                  ),
                ),
                StockBadgeChip(badge: medication.bestBadge),
              ],
            ),
            if (medication.genericName != null ||
                medication.category != null ||
                medication.form != null) ...[
              const SizedBox(height: AppSpacing.md),
              const Divider(),
              const SizedBox(height: AppSpacing.sm),
              Wrap(
                spacing: AppSpacing.md,
                runSpacing: AppSpacing.sm,
                children: [
                  if (medication.genericName != null)
                    _InfoChip(
                      icon: Icons.science_outlined,
                      label: medication.genericName!,
                    ),
                  if (medication.category != null)
                    _InfoChip(
                      icon: Icons.category_outlined,
                      label: medication.category!,
                    ),
                  if (medication.form != null)
                    _InfoChip(
                      icon: Icons.medical_services_outlined,
                      label: medication.form!,
                    ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _InfoChip extends StatelessWidget {
  final IconData icon;
  final String   label;
  const _InfoChip({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: AppColors.textSecondary),
        const SizedBox(width: 4),
        Text(label, style: AppTextStyles.bodySmall),
      ],
    );
  }
}

// ── Quantity selector ─────────────────────────────────────────────────────────

class _QuantitySelector extends StatelessWidget {
  final int quantity;
  final int max;
  final void Function(int) onChanged;
  const _QuantitySelector({required this.quantity, required this.max, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        _QButton(
          icon: Icons.remove,
          onTap: quantity > 1 ? () => onChanged(quantity - 1) : null,
        ),
        const SizedBox(width: AppSpacing.md),
        Text('$quantity', style: AppTextStyles.h2),
        const SizedBox(width: AppSpacing.md),
        _QButton(
          icon: Icons.add,
          onTap: quantity < max ? () => onChanged(quantity + 1) : null,
        ),
        const SizedBox(width: AppSpacing.md),
        Text(
          '/ $max disponibles',
          style: AppTextStyles.bodySmall,
        ),
      ],
    );
  }
}

class _QButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback? onTap;
  const _QButton({required this.icon, this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AppRadius.full),
      child: Container(
        width: 36,
        height: 36,
        decoration: BoxDecoration(
          color: onTap != null
              ? AppColors.primary.withOpacity(0.1)
              : AppColors.inputFill,
          shape: BoxShape.circle,
          border: Border.all(
            color: onTap != null ? AppColors.primary : AppColors.border,
          ),
        ),
        child: Icon(
          icon,
          size: 18,
          color: onTap != null ? AppColors.primary : AppColors.textHint,
        ),
      ),
    );
  }
}

// ── Pharmacy tile ─────────────────────────────────────────────────────────────

class _PharmacyTile extends StatelessWidget {
  final PharmacyStockDto pharmacy;
  final bool             selected;
  final VoidCallback     onTap;
  const _PharmacyTile({required this.pharmacy, required this.selected, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: InkWell(
        onTap: pharmacy.badge == StockBadge.outOfStock ? null : onTap,
        borderRadius: BorderRadius.circular(AppRadius.md),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          padding: const EdgeInsets.all(AppSpacing.md),
          decoration: BoxDecoration(
            color: selected
                ? AppColors.primary.withOpacity(0.05)
                : AppColors.surface,
            borderRadius: BorderRadius.circular(AppRadius.md),
            border: Border.all(
              color: selected ? AppColors.primary : AppColors.border,
              width: selected ? 2 : 1,
            ),
          ),
          child: Row(
            children: [
              // Selection indicator
              AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                width: 20,
                height: 20,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color:  selected ? AppColors.primary : Colors.transparent,
                  border: Border.all(
                    color: selected ? AppColors.primary : AppColors.border,
                    width: 2,
                  ),
                ),
                child: selected
                    ? const Icon(Icons.check, size: 12, color: Colors.white)
                    : null,
              ),
              const SizedBox(width: AppSpacing.md),

              // Info
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(pharmacy.pharmacyName, style: AppTextStyles.labelLarge),
                    const SizedBox(height: 2),
                    Text(
                      '${pharmacy.address} — '
                      '${pharmacy.distanceKm < 1 ? "${(pharmacy.distanceKm * 1000).round()} m" : "${pharmacy.distanceKm.toStringAsFixed(1)} km"}',
                      style: AppTextStyles.bodySmall,
                    ),
                  ],
                ),
              ),

              // Price + badge
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    '${pharmacy.unitPrice.toStringAsFixed(0)} FCFA',
                    style: AppTextStyles.labelLarge.copyWith(
                      color: AppColors.primary,
                    ),
                  ),
                  const SizedBox(height: 4),
                  StockBadgeChip(badge: pharmacy.badge, compact: true),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── Bottom bar ────────────────────────────────────────────────────────────────

class _BottomBar extends StatelessWidget {
  final PharmacyStockDto? selectedPharmacy;
  final int               quantity;
  final bool              isLoading;
  final VoidCallback      onReserve;
  const _BottomBar({
    required this.selectedPharmacy,
    required this.quantity,
    required this.isLoading,
    required this.onReserve,
  });

  @override
  Widget build(BuildContext context) {
    final total = selectedPharmacy != null
        ? selectedPharmacy!.unitPrice * quantity
        : null;

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
      child: Row(
        children: [
          if (total != null) ...[
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('Total', style: AppTextStyles.caption),
                Text(
                  '${total.toStringAsFixed(0)} FCFA',
                  style: AppTextStyles.h3.copyWith(color: AppColors.primary),
                ),
              ],
            ),
            const SizedBox(width: AppSpacing.md),
          ],
          Expanded(
            child: AppButton(
              label:     'Réserver',
              isLoading: isLoading,
              onPressed: onReserve,
            ),
          ),
        ],
      ),
    );
  }
}
