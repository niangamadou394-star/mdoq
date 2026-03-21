import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:medoq/features/reservation/data/reservation_repository.dart';
import 'package:medoq/features/reservation/domain/reservation_models.dart';

// ── My reservations list ──────────────────────────────────────────────────────

final myReservationsProvider =
    FutureProvider<List<Reservation>>((ref) {
  return ref.watch(reservationRepositoryProvider).getMyReservations();
});

// ── Single reservation ────────────────────────────────────────────────────────

final reservationDetailProvider =
    FutureProvider.family<Reservation, String>((ref, id) {
  return ref.watch(reservationRepositoryProvider).getById(id);
});

// ── Create reservation state ──────────────────────────────────────────────────

sealed class CreateReservationState { const CreateReservationState(); }
class CreateIdle    extends CreateReservationState { const CreateIdle(); }
class CreateLoading extends CreateReservationState { const CreateLoading(); }
class CreateSuccess extends CreateReservationState {
  final Reservation reservation;
  const CreateSuccess(this.reservation);
}
class CreateError extends CreateReservationState {
  final String message;
  const CreateError(this.message);
}

class CreateReservationNotifier
    extends StateNotifier<CreateReservationState> {
  final ReservationRepository _repo;
  CreateReservationNotifier(this._repo) : super(const CreateIdle());

  Future<Reservation?> create({
    required String pharmacyId,
    required String medicationId,
    required int    quantity,
  }) async {
    state = const CreateLoading();
    try {
      final r = await _repo.create(
        pharmacyId:   pharmacyId,
        medicationId: medicationId,
        quantity:     quantity,
      );
      state = CreateSuccess(r);
      return r;
    } on Exception catch (e) {
      final msg = e.toString();
      state = CreateError(
        msg.contains('409')
            ? 'Stock insuffisant pour cette quantité.'
            : 'Erreur lors de la réservation. Réessayez.',
      );
      return null;
    }
  }

  void reset() => state = const CreateIdle();
}

final createReservationProvider =
    StateNotifierProvider<CreateReservationNotifier, CreateReservationState>(
        (ref) {
  return CreateReservationNotifier(ref.watch(reservationRepositoryProvider));
});

// ── Cancel reservation ────────────────────────────────────────────────────────

final cancelReservationProvider =
    Provider<Future<void> Function(String, {String? reason})>((ref) {
  return (id, {reason}) =>
      ref.read(reservationRepositoryProvider).cancel(id, reason: reason);
});
