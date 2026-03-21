import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:medoq/core/router/app_router.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/core/widgets/app_button.dart';
import 'package:medoq/features/auth/domain/auth_provider.dart';
import 'package:medoq/features/auth/presentation/widgets/phone_field.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey   = GlobalKey<FormState>();
  final _phoneCt   = TextEditingController();
  final _passwdCt  = TextEditingController();
  bool  _showPasswd = false;

  @override
  void dispose() {
    _phoneCt.dispose();
    _passwdCt.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    final ok = await ref.read(loginProvider.notifier).login(
      phone:    _phoneCt.text.trim(),
      password: _passwdCt.text,
    );
    if (ok && mounted) context.go(AppRoutes.home);
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(loginProvider);
    final isLoading = state is AuthLoading;

    ref.listen(loginProvider, (_, next) {
      if (next is AuthError) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(next.message), backgroundColor: AppColors.error),
        );
      }
    });

    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: AppSpacing.xxl),

                // ── Logo & tagline ─────────────────────────────────
                Text('💊', style: const TextStyle(fontSize: 48), textAlign: TextAlign.center),
                const SizedBox(height: AppSpacing.sm),
                Text(
                  'Medoq',
                  style: AppTextStyles.h1.copyWith(color: AppColors.primary),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: AppSpacing.xs),
                Text(
                  'Trouvez vos médicaments près de vous',
                  style: AppTextStyles.bodyMedium.copyWith(
                    color: AppColors.textSecondary,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: AppSpacing.xxl),

                // ── Form ───────────────────────────────────────────
                Text('Connexion', style: AppTextStyles.h2),
                const SizedBox(height: AppSpacing.lg),

                PhoneField(
                  controller: _phoneCt,
                  textInputAction: TextInputAction.next,
                ),
                const SizedBox(height: AppSpacing.md),

                TextFormField(
                  controller:   _passwdCt,
                  obscureText:  !_showPasswd,
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _submit(),
                  decoration: InputDecoration(
                    labelText: 'Mot de passe',
                    hintText:  '••••••••',
                    suffixIcon: IconButton(
                      icon: Icon(
                        _showPasswd
                            ? Icons.visibility_off_outlined
                            : Icons.visibility_outlined,
                        color: AppColors.textSecondary,
                      ),
                      onPressed: () =>
                          setState(() => _showPasswd = !_showPasswd),
                    ),
                  ),
                  validator: (val) {
                    if (val == null || val.isEmpty) return 'Mot de passe requis';
                    if (val.length < 6) return '6 caractères minimum';
                    return null;
                  },
                ),
                const SizedBox(height: AppSpacing.sm),

                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton(
                    onPressed: () {
                      context.push(AppRoutes.otp, extra: {
                        'phone':   _phoneCt.text.trim(),
                        'context': 'reset',
                      });
                    },
                    child: const Text('Mot de passe oublié ?'),
                  ),
                ),
                const SizedBox(height: AppSpacing.lg),

                AppButton(
                  label:     'Se connecter',
                  isLoading: isLoading,
                  onPressed: _submit,
                ),
                const SizedBox(height: AppSpacing.md),

                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      'Pas encore inscrit ? ',
                      style: AppTextStyles.bodyMedium.copyWith(
                        color: AppColors.textSecondary,
                      ),
                    ),
                    TextButton(
                      onPressed: () => context.push(AppRoutes.register),
                      child: const Text('Créer un compte'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
