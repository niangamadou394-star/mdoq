import 'package:flutter/material.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/features/reservation/domain/reservation_models.dart';

class ReservationStatusChip extends StatelessWidget {
  final ReservationStatus status;
  final bool compact;
  const ReservationStatusChip({super.key, required this.status, this.compact = false});

  Color get _color => switch (status) {
    ReservationStatus.pending   => AppColors.statusPending,
    ReservationStatus.confirmed => AppColors.statusConfirmed,
    ReservationStatus.paid      => AppColors.statusPaid,
    ReservationStatus.ready     => AppColors.statusReady,
    ReservationStatus.completed => AppColors.statusCompleted,
    ReservationStatus.cancelled => AppColors.statusCancelled,
    ReservationStatus.expired   => AppColors.statusExpired,
  };

  IconData get _icon => switch (status) {
    ReservationStatus.pending   => Icons.schedule,
    ReservationStatus.confirmed => Icons.check_circle_outline,
    ReservationStatus.paid      => Icons.payment_outlined,
    ReservationStatus.ready     => Icons.storefront_outlined,
    ReservationStatus.completed => Icons.task_alt,
    ReservationStatus.cancelled => Icons.cancel_outlined,
    ReservationStatus.expired   => Icons.timer_off_outlined,
  };

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: compact ? AppSpacing.sm : AppSpacing.md,
        vertical:   compact ? 3             : AppSpacing.xs,
      ),
      decoration: BoxDecoration(
        color: _color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(AppRadius.full),
        border: Border.all(color: _color.withOpacity(0.4)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(_icon, size: compact ? 12 : 14, color: _color),
          const SizedBox(width: AppSpacing.xs),
          Text(
            status.label,
            style: AppTextStyles.caption.copyWith(
              color: _color,
              fontWeight: FontWeight.w600,
              fontSize: compact ? 10 : 12,
            ),
          ),
        ],
      ),
    );
  }
}
