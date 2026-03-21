import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:medoq/features/payment/data/payment_repository.dart';
import 'package:medoq/features/payment/domain/payment_models.dart';

// ── Payment detail ────────────────────────────────────────────────────────────

final paymentDetailProvider =
    FutureProvider.family<Payment, String>((ref, id) {
  return ref.watch(paymentRepositoryProvider).getById(id);
});

// ── Initiate payment state ────────────────────────────────────────────────────

sealed class InitiatePaymentState { const InitiatePaymentState(); }
class InitiateIdle    extends InitiatePaymentState { const InitiateIdle(); }
class InitiateLoading extends InitiatePaymentState { const InitiateLoading(); }
class InitiateSuccess extends InitiatePaymentState {
  final Payment payment;
  const InitiateSuccess(this.payment);
}
class InitiateError extends InitiatePaymentState {
  final String message;
  const InitiateError(this.message);
}

class InitiatePaymentNotifier
    extends StateNotifier<InitiatePaymentState> {
  final PaymentRepository _repo;
  InitiatePaymentNotifier(this._repo) : super(const InitiateIdle());

  Future<Payment?> initiate({
    required String      reservationId,
    required PaymentMethod method,
  }) async {
    state = const InitiateLoading();
    try {
      final payment = method == PaymentMethod.wave
          ? await _repo.initiateWave(reservationId)
          : await _repo.initiateOrangeMoney(reservationId);
      state = InitiateSuccess(payment);
      return payment;
    } on Exception catch (e) {
      final msg = e.toString();
      state = InitiateError(
        msg.contains('409')
            ? 'Un paiement est déjà en cours pour cette réservation.'
            : 'Erreur lors de l\'initiation du paiement. Réessayez.',
      );
      return null;
    }
  }

  void reset() => state = const InitiateIdle();
}

final initiatePaymentProvider =
    StateNotifierProvider<InitiatePaymentNotifier, InitiatePaymentState>((ref) {
  return InitiatePaymentNotifier(ref.watch(paymentRepositoryProvider));
});
