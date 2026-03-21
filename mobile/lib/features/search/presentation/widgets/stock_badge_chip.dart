import 'package:flutter/material.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/features/search/domain/search_models.dart';

class StockBadgeChip extends StatelessWidget {
  final StockBadge badge;
  final bool compact;

  const StockBadgeChip({super.key, required this.badge, this.compact = false});

  Color get _color => switch (badge) {
    StockBadge.available  => AppColors.available,
    StockBadge.limited    => AppColors.limited,
    StockBadge.outOfStock => AppColors.outOfStock,
  };

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: compact ? AppSpacing.sm : AppSpacing.md,
        vertical:   compact ? 2 : AppSpacing.xs,
      ),
      decoration: BoxDecoration(
        color: _color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(AppRadius.full),
        border: Border.all(color: _color.withOpacity(0.4)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: BoxDecoration(color: _color, shape: BoxShape.circle),
          ),
          const SizedBox(width: AppSpacing.xs),
          Text(
            badge.label,
            style: AppTextStyles.caption.copyWith(
              color: _color,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}
