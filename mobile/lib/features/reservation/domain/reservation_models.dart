import 'package:flutter/foundation.dart';

// ── Reservation status ────────────────────────────────────────────────────────

enum ReservationStatus {
  pending, confirmed, paid, ready, completed, cancelled, expired;

  static ReservationStatus fromString(String s) => switch (s) {
    'PENDING'   => ReservationStatus.pending,
    'CONFIRMED' => ReservationStatus.confirmed,
    'PAID'      => ReservationStatus.paid,
    'READY'     => ReservationStatus.ready,
    'COMPLETED' => ReservationStatus.completed,
    'CANCELLED' => ReservationStatus.cancelled,
    'EXPIRED'   => ReservationStatus.expired,
    _           => ReservationStatus.pending,
  };

  String get label => switch (this) {
    ReservationStatus.pending   => 'En attente',
    ReservationStatus.confirmed => 'Confirmée',
    ReservationStatus.paid      => 'Payée',
    ReservationStatus.ready     => 'Prête',
    ReservationStatus.completed => 'Complétée',
    ReservationStatus.cancelled => 'Annulée',
    ReservationStatus.expired   => 'Expirée',
  };

  bool get isActive =>
      this == ReservationStatus.pending   ||
      this == ReservationStatus.confirmed ||
      this == ReservationStatus.paid      ||
      this == ReservationStatus.ready;

  bool get isTerminal =>
      this == ReservationStatus.completed ||
      this == ReservationStatus.cancelled ||
      this == ReservationStatus.expired;

  bool get canPay =>
      this == ReservationStatus.confirmed;
}

// ── Reservation item ──────────────────────────────────────────────────────────

@immutable
class ReservationItem {
  final String medicationId;
  final String medicationName;
  final String? strength;
  final int     quantity;
  final double  unitPrice;

  const ReservationItem({
    required this.medicationId,
    required this.medicationName,
    this.strength,
    required this.quantity,
    required this.unitPrice,
  });

  double get subtotal => quantity * unitPrice;

  factory ReservationItem.fromJson(Map<String, dynamic> j) => ReservationItem(
    medicationId:   j['medicationId']   as String,
    medicationName: j['medicationName'] as String,
    strength:       j['strength']       as String?,
    quantity:       j['quantity']       as int,
    unitPrice:      (j['unitPrice']     as num).toDouble(),
  );
}

// ── Reservation ───────────────────────────────────────────────────────────────

@immutable
class Reservation {
  final String            id;
  final String            reference;
  final ReservationStatus status;
  final String            pharmacyId;
  final String            pharmacyName;
  final String            pharmacyAddress;
  final String            pharmacyPhone;
  final double            totalAmount;
  final DateTime?         expiresAt;
  final DateTime          createdAt;
  final List<ReservationItem> items;

  const Reservation({
    required this.id,
    required this.reference,
    required this.status,
    required this.pharmacyId,
    required this.pharmacyName,
    required this.pharmacyAddress,
    required this.pharmacyPhone,
    required this.totalAmount,
    this.expiresAt,
    required this.createdAt,
    required this.items,
  });

  factory Reservation.fromJson(Map<String, dynamic> j) => Reservation(
    id:              j['id']             as String,
    reference:       j['reference']      as String,
    status:          ReservationStatus.fromString(j['status'] as String),
    pharmacyId:      j['pharmacyId']     as String,
    pharmacyName:    j['pharmacyName']   as String,
    pharmacyAddress: j['pharmacyAddress'] as String,
    pharmacyPhone:   j['pharmacyPhone']  as String,
    totalAmount:     (j['totalAmount']   as num).toDouble(),
    expiresAt:       j['expiresAt'] != null
        ? DateTime.parse(j['expiresAt'] as String)
        : null,
    createdAt: DateTime.parse(j['createdAt'] as String),
    items: (j['items'] as List<dynamic>)
        .map((e) => ReservationItem.fromJson(e as Map<String, dynamic>))
        .toList(),
  );

  Duration? get timeLeft {
    if (expiresAt == null) return null;
    final d = expiresAt!.difference(DateTime.now());
    return d.isNegative ? Duration.zero : d;
  }
}
