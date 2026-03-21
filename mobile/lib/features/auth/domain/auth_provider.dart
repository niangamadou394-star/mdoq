import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:medoq/features/auth/data/auth_repository.dart';
import 'package:medoq/features/auth/domain/auth_models.dart';

// ── Auth state ────────────────────────────────────────────────────────────────

sealed class AuthState {
  const AuthState();
}
class AuthIdle     extends AuthState { const AuthIdle(); }
class AuthLoading  extends AuthState { const AuthLoading(); }
class AuthSuccess  extends AuthState {
  final AuthTokens tokens;
  const AuthSuccess(this.tokens);
}
class AuthError    extends AuthState {
  final String message;
  const AuthError(this.message);
}

// ── Login provider ────────────────────────────────────────────────────────────

class LoginNotifier extends StateNotifier<AuthState> {
  final AuthRepository _repo;
  LoginNotifier(this._repo) : super(const AuthIdle());

  Future<bool> login({required String phone, required String password}) async {
    state = const AuthLoading();
    try {
      final tokens = await _repo.login(phone: phone, password: password);
      state = AuthSuccess(tokens);
      return true;
    } on Exception catch (e) {
      state = AuthError(_parseError(e));
      return false;
    }
  }

  void reset() => state = const AuthIdle();
}

final loginProvider = StateNotifierProvider<LoginNotifier, AuthState>((ref) {
  return LoginNotifier(ref.watch(authRepositoryProvider));
});

// ── Register provider ─────────────────────────────────────────────────────────

class RegisterNotifier extends StateNotifier<AuthState> {
  final AuthRepository _repo;
  RegisterNotifier(this._repo) : super(const AuthIdle());

  Future<bool> register({
    required String phone,
    required String firstName,
    required String lastName,
    required String password,
    String? email,
  }) async {
    state = const AuthLoading();
    try {
      await _repo.register(
        phone:     phone,
        firstName: firstName,
        lastName:  lastName,
        password:  password,
        email:     email,
      );
      state = const AuthIdle();
      return true;
    } on Exception catch (e) {
      state = AuthError(_parseError(e));
      return false;
    }
  }

  void reset() => state = const AuthIdle();
}

final registerProvider =
    StateNotifierProvider<RegisterNotifier, AuthState>((ref) {
  return RegisterNotifier(ref.watch(authRepositoryProvider));
});

// ── Logout provider ───────────────────────────────────────────────────────────

final logoutProvider = Provider<Future<void> Function()>((ref) {
  return () => ref.read(authRepositoryProvider).logout();
});

// ── Helpers ───────────────────────────────────────────────────────────────────

String _parseError(Exception e) {
  final msg = e.toString();
  if (msg.contains('401')) return 'Numéro ou mot de passe incorrect.';
  if (msg.contains('409')) return 'Ce numéro est déjà enregistré.';
  if (msg.contains('423')) return 'Compte bloqué. Réessayez dans 30 minutes.';
  if (msg.contains('429')) return 'Trop de tentatives. Réessayez dans 1 minute.';
  if (msg.contains('SocketException') || msg.contains('connection'))
    return 'Vérifiez votre connexion internet.';
  return 'Une erreur est survenue. Réessayez.';
}
