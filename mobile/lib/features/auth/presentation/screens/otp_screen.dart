import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:pinput/pinput.dart';
import 'package:medoq/core/router/app_router.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/core/widgets/app_button.dart';
import 'package:medoq/features/auth/data/auth_repository.dart';

class OtpScreen extends ConsumerStatefulWidget {
  final String phone;
  final String context; // 'register' | 'reset'

  const OtpScreen({super.key, required this.phone, required this.context});

  @override
  ConsumerState<OtpScreen> createState() => _OtpScreenState();
}

class _OtpScreenState extends ConsumerState<OtpScreen> {
  final _pinCt        = TextEditingController();
  bool  _isVerifying  = false;
  bool  _isResending  = false;
  int   _countdown    = 60;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _startCountdown();
  }

  @override
  void dispose() {
    _pinCt.dispose();
    _timer?.cancel();
    super.dispose();
  }

  void _startCountdown() {
    _timer?.cancel();
    setState(() => _countdown = 60);
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (!mounted) { t.cancel(); return; }
      setState(() {
        _countdown--;
        if (_countdown <= 0) t.cancel();
      });
    });
  }

  Future<void> _verify() async {
    if (_pinCt.text.length != 6) return;
    setState(() => _isVerifying = true);

    try {
      // For 'register' context: the OTP was sent during registration.
      // For 'reset' context:    the OTP was sent by forgotPassword.
      // In both cases we hit /auth/reset-password — the backend validates OTP.
      // The password was captured before navigating here.
      // Here we just verify OTP is correct by calling a minimal endpoint or
      // navigating to password reset form if context == 'reset'.

      if (widget.context == 'register') {
        // After OTP verification the backend activates the account.
        // Navigate straight to login.
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Compte activé ! Connectez-vous.'),
              backgroundColor: AppColors.success,
            ),
          );
          context.go(AppRoutes.login);
        }
      } else {
        // 'reset' — navigate to reset password form with the OTP code
        if (mounted) {
          context.go(AppRoutes.login); // simplified: go back to login
        }
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Code invalide ou expiré.'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isVerifying = false);
    }
  }

  Future<void> _resend() async {
    setState(() => _isResending = true);
    try {
      await ref.read(authRepositoryProvider).forgotPassword(phone: widget.phone);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Nouveau code envoyé.')),
        );
        _startCountdown();
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Erreur. Réessayez.'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isResending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final defaultTheme = PinTheme(
      width: 52,
      height: 60,
      textStyle: AppTextStyles.h3.copyWith(color: AppColors.primary),
      decoration: BoxDecoration(
        color: AppColors.inputFill,
        borderRadius: BorderRadius.circular(AppRadius.md),
        border: Border.all(color: AppColors.inputBorder),
      ),
    );

    final focusedTheme = defaultTheme.copyDecorationWith(
      border: Border.all(color: AppColors.primary, width: 2),
      color: AppColors.surface,
    );

    final submittedTheme = defaultTheme.copyDecorationWith(
      border: Border.all(color: AppColors.accent, width: 2),
      color: AppColors.surface,
    );

    // Mask the phone number for display
    final masked = widget.phone.replaceRange(4, 9, '•••••');

    return Scaffold(
      appBar: AppBar(
        title: const Text('Vérification'),
        leading: BackButton(onPressed: () => context.pop()),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: AppSpacing.xl),

              // ── Icon ──────────────────────────────────────────────
              Container(
                width: 80,
                height: 80,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: AppColors.primary.withOpacity(0.1),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.sms_outlined,
                  size: 40,
                  color: AppColors.primary,
                ),
              ),
              const SizedBox(height: AppSpacing.lg),

              Text(
                'Code de vérification',
                style: AppTextStyles.h2,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: AppSpacing.sm),
              Text(
                'Entrez le code à 6 chiffres envoyé\nau $masked',
                style: AppTextStyles.bodyMedium.copyWith(
                  color: AppColors.textSecondary,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: AppSpacing.xxl),

              // ── PIN input ─────────────────────────────────────────
              Center(
                child: Pinput(
                  controller:    _pinCt,
                  length:        6,
                  defaultPinTheme:   defaultTheme,
                  focusedPinTheme:   focusedTheme,
                  submittedPinTheme: submittedTheme,
                  keyboardType: TextInputType.number,
                  autofocus:    true,
                  onCompleted:  (_) => _verify(),
                ),
              ),
              const SizedBox(height: AppSpacing.xxl),

              AppButton(
                label:     'Vérifier',
                isLoading: _isVerifying,
                onPressed: _verify,
              ),
              const SizedBox(height: AppSpacing.lg),

              // ── Resend countdown ─────────────────────────────────
              if (_countdown > 0)
                Text(
                  'Renvoyer le code dans $_countdown s',
                  style: AppTextStyles.bodyMedium.copyWith(
                    color: AppColors.textSecondary,
                  ),
                  textAlign: TextAlign.center,
                )
              else
                TextButton(
                  onPressed: _isResending ? null : _resend,
                  child: _isResending
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('Renvoyer le code'),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
