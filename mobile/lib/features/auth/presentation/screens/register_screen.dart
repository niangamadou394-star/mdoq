import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:medoq/core/router/app_router.dart';
import 'package:medoq/core/theme/app_theme.dart';
import 'package:medoq/core/widgets/app_button.dart';
import 'package:medoq/features/auth/domain/auth_provider.dart';
import 'package:medoq/features/auth/presentation/widgets/phone_field.dart';

class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _formKey      = GlobalKey<FormState>();
  final _firstNameCt  = TextEditingController();
  final _lastNameCt   = TextEditingController();
  final _phoneCt      = TextEditingController();
  final _emailCt      = TextEditingController();
  final _passwdCt     = TextEditingController();
  final _confirmCt    = TextEditingController();
  bool  _showPasswd   = false;
  bool  _acceptTerms  = false;

  @override
  void dispose() {
    _firstNameCt.dispose();
    _lastNameCt.dispose();
    _phoneCt.dispose();
    _emailCt.dispose();
    _passwdCt.dispose();
    _confirmCt.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (!_acceptTerms) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Veuillez accepter les conditions.')),
      );
      return;
    }

    final ok = await ref.read(registerProvider.notifier).register(
      phone:     _phoneCt.text.trim(),
      firstName: _firstNameCt.text.trim(),
      lastName:  _lastNameCt.text.trim(),
      password:  _passwdCt.text,
      email:     _emailCt.text.trim(),
    );

    if (ok && mounted) {
      context.push(AppRoutes.otp, extra: {
        'phone':   _phoneCt.text.trim(),
        'context': 'register',
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final state     = ref.watch(registerProvider);
    final isLoading = state is AuthLoading;

    ref.listen(registerProvider, (_, next) {
      if (next is AuthError) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(next.message),
            backgroundColor: AppColors.error,
          ),
        );
      }
    });

    return Scaffold(
      appBar: AppBar(
        title: const Text('Créer un compte'),
        leading: BackButton(onPressed: () => context.pop()),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: AppSpacing.sm),

                // ── Name ───────────────────────────────────────────
                Row(
                  children: [
                    Expanded(
                      child: TextFormField(
                        controller: _firstNameCt,
                        textInputAction: TextInputAction.next,
                        textCapitalization: TextCapitalization.words,
                        decoration: const InputDecoration(labelText: 'Prénom'),
                        validator: (v) =>
                            (v == null || v.trim().isEmpty) ? 'Requis' : null,
                      ),
                    ),
                    const SizedBox(width: AppSpacing.md),
                    Expanded(
                      child: TextFormField(
                        controller: _lastNameCt,
                        textInputAction: TextInputAction.next,
                        textCapitalization: TextCapitalization.words,
                        decoration: const InputDecoration(labelText: 'Nom'),
                        validator: (v) =>
                            (v == null || v.trim().isEmpty) ? 'Requis' : null,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: AppSpacing.md),

                PhoneField(controller: _phoneCt),
                const SizedBox(height: AppSpacing.md),

                TextFormField(
                  controller: _emailCt,
                  keyboardType: TextInputType.emailAddress,
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(
                    labelText: 'Email (optionnel)',
                    hintText: 'vous@exemple.com',
                  ),
                  validator: (v) {
                    if (v == null || v.isEmpty) return null;
                    final emailRe = RegExp(r'^[\w.-]+@[\w.-]+\.\w{2,}$');
                    return emailRe.hasMatch(v) ? null : 'Email invalide';
                  },
                ),
                const SizedBox(height: AppSpacing.md),

                TextFormField(
                  controller:  _passwdCt,
                  obscureText: !_showPasswd,
                  textInputAction: TextInputAction.next,
                  decoration: InputDecoration(
                    labelText: 'Mot de passe',
                    hintText: '8 caractères minimum',
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
                  validator: (v) {
                    if (v == null || v.isEmpty) return 'Mot de passe requis';
                    if (v.length < 8) return '8 caractères minimum';
                    if (!RegExp(r'[A-Z]').hasMatch(v))
                      return 'Au moins 1 majuscule';
                    if (!RegExp(r'[0-9]').hasMatch(v))
                      return 'Au moins 1 chiffre';
                    return null;
                  },
                ),
                const SizedBox(height: AppSpacing.md),

                TextFormField(
                  controller: _confirmCt,
                  obscureText: true,
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _submit(),
                  decoration: const InputDecoration(
                    labelText: 'Confirmer le mot de passe',
                  ),
                  validator: (v) => v != _passwdCt.text
                      ? 'Les mots de passe ne correspondent pas'
                      : null,
                ),
                const SizedBox(height: AppSpacing.md),

                // ── Terms ──────────────────────────────────────────
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Checkbox(
                      value: _acceptTerms,
                      onChanged: (v) =>
                          setState(() => _acceptTerms = v ?? false),
                      activeColor: AppColors.primary,
                    ),
                    Expanded(
                      child: GestureDetector(
                        onTap: () =>
                            setState(() => _acceptTerms = !_acceptTerms),
                        child: Padding(
                          padding: const EdgeInsets.only(top: 12),
                          child: RichText(
                            text: TextSpan(
                              style: AppTextStyles.bodySmall,
                              children: [
                                const TextSpan(text: "J'accepte les "),
                                TextSpan(
                                  text: "conditions d'utilisation",
                                  style: AppTextStyles.bodySmall.copyWith(
                                    color: AppColors.accent,
                                    decoration: TextDecoration.underline,
                                  ),
                                ),
                                const TextSpan(text: ' de Medoq.'),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: AppSpacing.lg),

                AppButton(
                  label:     "S'inscrire",
                  isLoading: isLoading,
                  onPressed: _submit,
                ),
                const SizedBox(height: AppSpacing.md),

                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      'Déjà inscrit ? ',
                      style: AppTextStyles.bodyMedium.copyWith(
                        color: AppColors.textSecondary,
                      ),
                    ),
                    TextButton(
                      onPressed: () => context.pop(),
                      child: const Text('Se connecter'),
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
