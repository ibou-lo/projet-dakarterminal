import 'package:flutter/material.dart';

/// Couleurs de la maquette Dakar AutoTrack.
class DatColors {
  static const ink = Color(0xFF0F1D2B);
  static const ink2 = Color(0xFF1B2B3E);
  static const inkLine = Color(0xFF2F4A66);
  static const ground = Color(0xFFF3F1EB);
  static const line = Color(0xFFDCD7CB);
  static const muted = Color(0xFF56606B);
  static const text2 = Color(0xFF3B4652);
  static const orange = Color(0xFFD9601A);
  static const rust = Color(0xFFB94E12);
  static const peach = Color(0xFFF0A36E);
  static const ok = Color(0xFF17714A);
  static const okBg = Color(0xFFDDF0E5);
  static const okInk = Color(0xFF145C3C);
  static const warnBg = Color(0xFFFBEBCF);
  static const warnInk = Color(0xFF7A4A00);
  static const infoBg = Color(0xFFE1EAF6);
  static const infoInk = Color(0xFF1E3F6E);
  static const danger = Color(0xFFA3261B);
  static const dangerBg = Color(0xFFFCF1EF);
  static const onInkMuted = Color(0xFFB9C3CF);
}

ThemeData buildTheme() {
  final base = ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: DatColors.rust,
      primary: DatColors.rust,
      secondary: DatColors.ink,
      surface: Colors.white,
    ),
    scaffoldBackgroundColor: DatColors.ground,
  );
  return base.copyWith(
    appBarTheme: const AppBarTheme(
      backgroundColor: DatColors.ink,
      foregroundColor: Colors.white,
      elevation: 0,
      titleTextStyle: TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Colors.white),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: DatColors.rust,
        foregroundColor: Colors.white,
        minimumSize: const Size.fromHeight(52),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: DatColors.ink,
        minimumSize: const Size(44, 52),
        side: const BorderSide(color: DatColors.line, width: 1.5),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
    ),
  );
}

const monoStyle = TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.w600, letterSpacing: 0.5);
