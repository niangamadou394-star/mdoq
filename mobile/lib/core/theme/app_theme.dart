import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

// ── Design system colors (section 7) ─────────────────────────────────────────

class AppColors {
  // Brand
  static const primary    = Color(0xFF1A3C5E);
  static const accent     = Color(0xFF00B4D8);
  static const primaryLight = Color(0xFF2E5B8A);

  // Stock badges
  static const available  = Color(0xFF2DC653);
  static const limited    = Color(0xFFF4A261);
  static const outOfStock = Color(0xFFE24B4A);

  // Neutrals
  static const background = Color(0xFFF8FAFC);
  static const surface    = Color(0xFFFFFFFF);
  static const border     = Color(0xFFE2E8F0);
  static const textPrimary   = Color(0xFF0F172A);
  static const textSecondary = Color(0xFF64748B);
  static const textHint      = Color(0xFFADB5BD);

  // Status chips
  static const statusPending   = Color(0xFFFBBF24);
  static const statusConfirmed = Color(0xFF3B82F6);
  static const statusPaid      = Color(0xFF8B5CF6);
  static const statusReady     = Color(0xFF10B981);
  static const statusCompleted = Color(0xFF2DC653);
  static const statusCancelled = Color(0xFF94A3B8);
  static const statusExpired   = Color(0xFFE24B4A);

  // Input
  static const inputFill   = Color(0xFFF1F5F9);
  static const inputBorder = Color(0xFFCBD5E1);

  // Error / Success
  static const error   = Color(0xFFE24B4A);
  static const success = Color(0xFF2DC653);
  static const warning = Color(0xFFF4A261);
  static const info    = Color(0xFF00B4D8);
}

// ── Typography ────────────────────────────────────────────────────────────────

class AppTextStyles {
  static const _family = 'Inter';

  static const h1 = TextStyle(
    fontFamily: _family, fontSize: 28, fontWeight: FontWeight.w700,
    color: AppColors.textPrimary, height: 1.25,
  );
  static const h2 = TextStyle(
    fontFamily: _family, fontSize: 22, fontWeight: FontWeight.w700,
    color: AppColors.textPrimary, height: 1.3,
  );
  static const h3 = TextStyle(
    fontFamily: _family, fontSize: 18, fontWeight: FontWeight.w600,
    color: AppColors.textPrimary, height: 1.35,
  );
  static const bodyLarge = TextStyle(
    fontFamily: _family, fontSize: 16, fontWeight: FontWeight.w400,
    color: AppColors.textPrimary, height: 1.5,
  );
  static const bodyMedium = TextStyle(
    fontFamily: _family, fontSize: 14, fontWeight: FontWeight.w400,
    color: AppColors.textPrimary, height: 1.5,
  );
  static const bodySmall = TextStyle(
    fontFamily: _family, fontSize: 12, fontWeight: FontWeight.w400,
    color: AppColors.textSecondary, height: 1.5,
  );
  static const labelLarge = TextStyle(
    fontFamily: _family, fontSize: 14, fontWeight: FontWeight.w600,
    color: AppColors.textPrimary, height: 1.4, letterSpacing: 0.1,
  );
  static const labelMedium = TextStyle(
    fontFamily: _family, fontSize: 12, fontWeight: FontWeight.w500,
    color: AppColors.textSecondary, height: 1.4,
  );
  static const caption = TextStyle(
    fontFamily: _family, fontSize: 11, fontWeight: FontWeight.w400,
    color: AppColors.textSecondary, height: 1.4,
  );
  static const button = TextStyle(
    fontFamily: _family, fontSize: 16, fontWeight: FontWeight.w600,
    color: Colors.white, letterSpacing: 0.3,
  );
}

// ── Spacing & radius ──────────────────────────────────────────────────────────

class AppSpacing {
  static const xs  = 4.0;
  static const sm  = 8.0;
  static const md  = 16.0;
  static const lg  = 24.0;
  static const xl  = 32.0;
  static const xxl = 48.0;
}

class AppRadius {
  static const sm  = 8.0;
  static const md  = 12.0;
  static const lg  = 16.0;
  static const xl  = 24.0;
  static const full = 999.0;
}

// ── Theme ─────────────────────────────────────────────────────────────────────

class AppTheme {
  static ThemeData get light {
    const colorScheme = ColorScheme(
      brightness: Brightness.light,
      primary:         AppColors.primary,
      onPrimary:       Colors.white,
      secondary:       AppColors.accent,
      onSecondary:     Colors.white,
      surface:         AppColors.surface,
      onSurface:       AppColors.textPrimary,
      error:           AppColors.error,
      onError:         Colors.white,
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: AppColors.background,
      fontFamily: 'Inter',

      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.surface,
        foregroundColor: AppColors.textPrimary,
        elevation: 0,
        scrolledUnderElevation: 1,
        shadowColor: AppColors.border,
        systemOverlayStyle: SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness: Brightness.dark,
        ),
        titleTextStyle: AppTextStyles.h3,
        centerTitle: false,
      ),

      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.primary,
          foregroundColor: Colors.white,
          minimumSize: const Size.fromHeight(52),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppRadius.md),
          ),
          elevation: 0,
          textStyle: AppTextStyles.button,
        ),
      ),

      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.primary,
          minimumSize: const Size.fromHeight(52),
          side: const BorderSide(color: AppColors.primary, width: 1.5),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppRadius.md),
          ),
          textStyle: AppTextStyles.button.copyWith(color: AppColors.primary),
        ),
      ),

      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: AppColors.accent,
          textStyle: AppTextStyles.labelLarge.copyWith(color: AppColors.accent),
        ),
      ),

      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.inputFill,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md,
          vertical: AppSpacing.md,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
          borderSide: const BorderSide(color: AppColors.inputBorder),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
          borderSide: const BorderSide(color: AppColors.inputBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
          borderSide: const BorderSide(color: AppColors.primary, width: 2),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
          borderSide: const BorderSide(color: AppColors.error),
        ),
        labelStyle: AppTextStyles.bodyMedium.copyWith(
          color: AppColors.textSecondary,
        ),
        hintStyle: AppTextStyles.bodyMedium.copyWith(
          color: AppColors.textHint,
        ),
      ),

      cardTheme: CardThemeData(
        color: AppColors.surface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.lg),
          side: const BorderSide(color: AppColors.border),
        ),
        margin: EdgeInsets.zero,
      ),

      chipTheme: ChipThemeData(
        backgroundColor: AppColors.inputFill,
        labelStyle: AppTextStyles.labelMedium,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.full),
        ),
        side: BorderSide.none,
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.sm,
          vertical: AppSpacing.xs,
        ),
      ),

      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: AppColors.surface,
        selectedItemColor: AppColors.primary,
        unselectedItemColor: AppColors.textSecondary,
        type: BottomNavigationBarType.fixed,
        elevation: 8,
        selectedLabelStyle: TextStyle(
          fontFamily: 'Inter', fontSize: 11, fontWeight: FontWeight.w600,
        ),
        unselectedLabelStyle: TextStyle(
          fontFamily: 'Inter', fontSize: 11,
        ),
      ),

      dividerTheme: const DividerThemeData(
        color: AppColors.border,
        thickness: 1,
        space: 0,
      ),

      snackBarTheme: SnackBarThemeData(
        backgroundColor: AppColors.textPrimary,
        contentTextStyle: AppTextStyles.bodyMedium.copyWith(color: Colors.white),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
        ),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }
}
