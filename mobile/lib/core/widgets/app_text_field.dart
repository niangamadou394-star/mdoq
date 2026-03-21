import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class AppTextField extends StatelessWidget {
  final String label;
  final String? hint;
  final TextEditingController? controller;
  final String? Function(String?)? validator;
  final TextInputType keyboardType;
  final bool obscureText;
  final Widget? suffixIcon;
  final Widget? prefixIcon;
  final List<TextInputFormatter>? inputFormatters;
  final void Function(String)? onChanged;
  final TextInputAction textInputAction;
  final FocusNode? focusNode;
  final bool autofocus;
  final int? maxLines;
  final bool readOnly;
  final VoidCallback? onTap;

  const AppTextField({
    super.key,
    required this.label,
    this.hint,
    this.controller,
    this.validator,
    this.keyboardType = TextInputType.text,
    this.obscureText = false,
    this.suffixIcon,
    this.prefixIcon,
    this.inputFormatters,
    this.onChanged,
    this.textInputAction = TextInputAction.next,
    this.focusNode,
    this.autofocus = false,
    this.maxLines = 1,
    this.readOnly = false,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller:         controller,
      validator:          validator,
      keyboardType:       keyboardType,
      obscureText:        obscureText,
      inputFormatters:    inputFormatters,
      onChanged:          onChanged,
      textInputAction:    textInputAction,
      focusNode:          focusNode,
      autofocus:          autofocus,
      maxLines:           obscureText ? 1 : maxLines,
      readOnly:           readOnly,
      onTap:              onTap,
      decoration: InputDecoration(
        labelText:  label,
        hintText:   hint,
        suffixIcon: suffixIcon,
        prefixIcon: prefixIcon,
      ),
    );
  }
}
