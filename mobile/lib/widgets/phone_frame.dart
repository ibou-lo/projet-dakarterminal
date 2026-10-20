import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme.dart';

/// Garde le format téléphone (390 × 844, comme la maquette) quand l'application
/// tourne sur un grand écran : navigateur (flutter run -d chrome), Windows, tablette.
/// Sur un vrai téléphone, l'écran est utilisé tel quel.
class PhoneFrame extends StatelessWidget {
  const PhoneFrame({super.key, required this.child});

  final Widget child;

  static const double phoneWidth = 390;
  static const double phoneHeight = 844;

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final screen = media.size;
    if (screen.width <= 600) return child; // vrai téléphone

    final height = math.min(phoneHeight, screen.height - 48);
    final width = math.min(phoneWidth, screen.width - 48);
    return ColoredBox(
      color: const Color(0xFFE9E5DC),
      child: Center(
        child: Container(
          width: width,
          height: height,
          decoration: BoxDecoration(
            color: DatColors.ground,
            borderRadius: BorderRadius.circular(28),
            border: Border.all(color: DatColors.ink, width: 6),
            boxShadow: const [BoxShadow(color: Color(0x33000000), blurRadius: 30, offset: Offset(0, 12))],
          ),
          clipBehavior: Clip.antiAlias,
          child: MediaQuery(
            // L'application « voit » un écran de téléphone : mises en page identiques au mobile.
            data: media.copyWith(
              size: Size(width - 12, height - 12),
              padding: EdgeInsets.zero,
              viewPadding: EdgeInsets.zero,
              viewInsets: EdgeInsets.zero,
            ),
            child: child,
          ),
        ),
      ),
    );
  }
}
