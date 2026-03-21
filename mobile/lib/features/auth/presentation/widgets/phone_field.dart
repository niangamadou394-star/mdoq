import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:medoq/core/constants/app_constants.dart';
import 'package:medoq/core/theme/app_theme.dart';

class PhoneField extends StatelessWidget {
  final TextEditingController controller;
  final FocusNode? focusNode;
  final TextInputAction textInputAction;

  const PhoneField({
    super.key,
    required this.controller,
    this.focusNode,
    this.textInputAction = TextInputAction.next,
  });

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller:      controller,
      focusNode:       focusNode,
      keyboardType:    TextInputType.phone,
      textInputAction: textInputAction,
      inputFormatters: [
        FilteringTextInputFormatter.allow(RegExp(r'[0-9+]')),
        LengthLimitingTextInputFormatter(13),
      ],
      decoration: const InputDecoration(
        labelText: 'Numéro de téléphone',
        hintText:  '+221 7X XXX XX XX',
        prefixIcon: Padding(
          padding: EdgeInsets.symmetric(horizontal: AppSpacing.md),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('🇸🇳', style: TextStyle(fontSize: 20)),
              SizedBox(width: AppSpacing.xs),
              Text('+221', style: AppTextStyles.bodyMedium),
            ],
          ),
        ),
      ),
      validator: (val) {
        if (val == null || val.isEmpty) return 'Numéro requis';
        if (!AppConstants.phoneRegex.hasMatch(val)) {
          return 'Format invalide. Ex: +221771234567';
        }
        return null;
      },
    );
  }
}
