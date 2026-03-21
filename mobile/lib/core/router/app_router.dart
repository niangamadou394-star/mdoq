import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:medoq/core/storage/secure_storage.dart';
import 'package:medoq/features/auth/presentation/screens/login_screen.dart';
import 'package:medoq/features/auth/presentation/screens/register_screen.dart';
import 'package:medoq/features/auth/presentation/screens/otp_screen.dart';
import 'package:medoq/features/search/presentation/screens/home_screen.dart';
import 'package:medoq/features/search/presentation/screens/search_results_screen.dart';
import 'package:medoq/features/search/presentation/screens/map_screen.dart';
import 'package:medoq/features/reservation/presentation/screens/medication_detail_screen.dart';
import 'package:medoq/features/reservation/presentation/screens/reservation_confirm_screen.dart';
import 'package:medoq/features/reservation/presentation/screens/my_reservations_screen.dart';
import 'package:medoq/features/payment/presentation/screens/payment_method_screen.dart';
import 'package:medoq/features/payment/presentation/screens/payment_confirm_screen.dart';

// ── Route names ───────────────────────────────────────────────────────────────

class AppRoutes {
  // Auth
  static const login    = '/login';
  static const register = '/register';
  static const otp      = '/otp';

  // Main
  static const home              = '/';
  static const searchResults     = '/search';
  static const map               = '/map';
  static const medicationDetail  = '/medications/:id';
  static const reservationConfirm = '/reservation/confirm';
  static const myReservations    = '/reservations';
  static const paymentMethod     = '/payment/method';
  static const paymentConfirm    = '/payment/confirm';

  static String medicationDetailPath(String id) => '/medications/$id';
}

// ── Router provider ───────────────────────────────────────────────────────────

final routerProvider = Provider<GoRouter>((ref) {
  final storage = ref.read(secureStorageProvider);

  return GoRouter(
    initialLocation: AppRoutes.home,
    redirect: (context, state) async {
      final isLoggedIn  = await storage.isLoggedIn;
      final isAuthRoute = state.matchedLocation.startsWith('/login') ||
                          state.matchedLocation.startsWith('/register') ||
                          state.matchedLocation.startsWith('/otp');

      if (!isLoggedIn && !isAuthRoute) return AppRoutes.login;
      if (isLoggedIn && isAuthRoute) return AppRoutes.home;
      return null;
    },
    routes: [
      // ── Auth ──────────────────────────────────────────────────────
      GoRoute(path: AppRoutes.login,    builder: (_, __) => const LoginScreen()),
      GoRoute(path: AppRoutes.register, builder: (_, __) => const RegisterScreen()),
      GoRoute(
        path: AppRoutes.otp,
        builder: (_, state) {
          final extra = state.extra as Map<String, dynamic>;
          return OtpScreen(
            phone:   extra['phone'] as String,
            context: extra['context'] as String, // 'register' or 'reset'
          );
        },
      ),

      // ── Main scaffold with bottom nav ─────────────────────────────
      StatefulShellRoute.indexedStack(
        builder: (_, __, shell) => MainScaffold(shell: shell),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(path: AppRoutes.home, builder: (_, __) => const HomeScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: AppRoutes.map,
              builder: (_, __) => const MapScreen(),
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: AppRoutes.myReservations,
              builder: (_, __) => const MyReservationsScreen(),
            ),
          ]),
        ],
      ),

      // ── Standalone routes (push over shell) ───────────────────────
      GoRoute(
        path: AppRoutes.searchResults,
        builder: (_, state) {
          final q = state.uri.queryParameters['q'] ?? '';
          return SearchResultsScreen(query: q);
        },
      ),
      GoRoute(
        path: AppRoutes.medicationDetail,
        builder: (_, state) {
          final id    = state.pathParameters['id']!;
          final extra = state.extra as Map<String, dynamic>?;
          return MedicationDetailScreen(
            medicationId: id,
            pharmacyId: extra?['pharmacyId'] as String?,
          );
        },
      ),
      GoRoute(
        path: AppRoutes.reservationConfirm,
        builder: (_, state) {
          final extra = state.extra as Map<String, dynamic>;
          return ReservationConfirmScreen(reservationId: extra['reservationId']);
        },
      ),
      GoRoute(
        path: AppRoutes.paymentMethod,
        builder: (_, state) {
          final extra = state.extra as Map<String, dynamic>;
          return PaymentMethodScreen(
            reservationId: extra['reservationId'],
            amount:        (extra['amount'] as num).toDouble(),
          );
        },
      ),
      GoRoute(
        path: AppRoutes.paymentConfirm,
        builder: (_, state) {
          final extra = state.extra as Map<String, dynamic>;
          return PaymentConfirmScreen(paymentId: extra['paymentId']);
        },
      ),
    ],
    errorBuilder: (_, state) => Scaffold(
      body: Center(child: Text('Route introuvable: ${state.uri}')),
    ),
  );
});

// ── Main scaffold with bottom navigation bar ──────────────────────────────────

class MainScaffold extends StatelessWidget {
  final StatefulNavigationShell shell;
  const MainScaffold({super.key, required this.shell});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: shell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: shell.currentIndex,
        onDestinationSelected: (i) => shell.goBranch(
          i,
          initialLocation: i == shell.currentIndex,
        ),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.search_outlined),
            selectedIcon: Icon(Icons.search),
            label: 'Recherche',
          ),
          NavigationDestination(
            icon: Icon(Icons.map_outlined),
            selectedIcon: Icon(Icons.map),
            label: 'Carte',
          ),
          NavigationDestination(
            icon: Icon(Icons.receipt_long_outlined),
            selectedIcon: Icon(Icons.receipt_long),
            label: 'Réservations',
          ),
        ],
      ),
    );
  }
}
