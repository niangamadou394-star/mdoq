import 'package:flutter/foundation.dart';

enum PaymentMethod { wave, orangeMoney }

enum PaymentStatus {
  pending, completed, failed, refunded;

  static PaymentStatus fromString(String s) => switch (s) {
    'PENDING'   => PaymentStatus.pending,
    'COMPLETED' => PaymentStatus.completed,
    'FAILED'    => PaymentStatus.failed,
    'REFUNDED'  => PaymentStatus.refunded,
    _           => PaymentStatus.pending,
  };

  String get label => switch (this) {
    PaymentStatus.pending   => 'En attente',
    PaymentStatus.completed => 'Payé',
    PaymentStatus.failed    => 'Échoué',
    PaymentStatus.refunded  => 'Remboursé',
  };
}

@immutable
class Payment {
  final String        id;
  final String        reservationId;
  final PaymentStatus status;
  final PaymentMethod method;
  final double        amount;
  final double?       commissionAmount;
  final double?       netAmount;
  final String?       checkoutUrl;
  final String?       transactionRef;
  final DateTime?     paidAt;

  const Payment({
    required this.id,
    required this.reservationId,
    required this.status,
    required this.method,
    required this.amount,
    this.commissionAmount,
    this.netAmount,
    this.checkoutUrl,
    this.transactionRef,
    this.paidAt,
  });

  factory Payment.fromJson(Map<String, dynamic> j) => Payment(
    id:               j['id']             as String,
    reservationId:    j['reservationId']  as String,
    status:           PaymentStatus.fromString(j['status'] as String),
    method:           j['method'] == 'WAVE'
                          ? PaymentMethod.wave
                          : PaymentMethod.orangeMoney,
    amount:           (j['amount']        as num).toDouble(),
    commissionAmount: (j['commissionAmount'] as num?)?.toDouble(),
    netAmount:        (j['netAmount']     as num?)?.toDouble(),
    checkoutUrl:      j['checkoutUrl']    as String?,
    transactionRef:   j['transactionRef'] as String?,
    paidAt:           j['paidAt'] != null
                          ? DateTime.parse(j['paidAt'] as String)
                          : null,
  );
}
