import 'package:flutter/material.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/features/search/domain/search_models.dart';
import 'package:medoq/features/search/presentation/widgets/stock_badge_chip.dart';

class MedicationCard extends StatelessWidget {
  final MedicationSearchResult medication;
  final VoidCallback? onTap;

  const MedicationCard({super.key, required this.medication, this.onTap});

  @override
  Widget build(BuildContext context) {
    final minPrice = medication.minPrice;
    final minDist  = medication.minDistanceKm;

    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // ── Header row ────────────────────────────────────
              Row(
                children: [
                  // Pill icon
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: AppColors.primary.withOpacity(0.08),
                      borderRadius: BorderRadius.circular(AppRadius.md),
                    ),
                    child: const Icon(
                      Icons.medication_outlined,
                      color: AppColors.primary,
                      size: 24,
                    ),
                  ),
                  const SizedBox(width: AppSpacing.md),

                  // Name + category
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          medication.name,
                          style: AppTextStyles.labelLarge,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        if (medication.genericName != null) ...[
                          const SizedBox(height: 2),
                          Text(
                            medication.genericName!,
                            style: AppTextStyles.bodySmall,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  StockBadgeChip(badge: medication.bestBadge, compact: true),
                ],
              ),
              const SizedBox(height: AppSpacing.md),
              const Divider(),
              const SizedBox(height: AppSpacing.sm),

              // ── Footer row ────────────────────────────────────
              Row(
                children: [
                  // Pharmacies count
                  Icon(Icons.local_pharmacy_outlined,
                      size: 14, color: AppColors.textSecondary),
                  const SizedBox(width: 4),
                  Text(
                    '${medication.pharmacies.length} pharmacie'
                    '${medication.pharmacies.length > 1 ? 's' : ''}',
                    style: AppTextStyles.bodySmall,
                  ),

                  const Spacer(),

                  // Distance
                  if (minDist != null) ...[
                    Icon(Icons.near_me_outlined,
                        size: 14, color: AppColors.textSecondary),
                    const SizedBox(width: 4),
                    Text(
                      minDist < 1
                          ? '${(minDist * 1000).round()} m'
                          : '${minDist.toStringAsFixed(1)} km',
                      style: AppTextStyles.bodySmall,
                    ),
                    const SizedBox(width: AppSpacing.md),
                  ],

                  // Price
                  if (minPrice != null)
                    Text(
                      'dès ${minPrice.toStringAsFixed(0)} FCFA',
                      style: AppTextStyles.labelMedium.copyWith(
                        color: AppColors.primary,
                        fontWeight: FontWeight.w600,
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
}
