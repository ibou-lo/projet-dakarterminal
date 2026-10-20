import 'package:flutter/material.dart';

/// Bouton « Retour » affiché en haut à gauche de tous les écrans secondaires.
/// Utilise maybePop : les écrans qui protègent une saisie en cours (PopScope) demandent confirmation.
class DatBackButton extends StatelessWidget {
  const DatBackButton({super.key});

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: 'Retour',
      icon: const Icon(Icons.arrow_back),
      onPressed: () => Navigator.of(context).maybePop(),
    );
  }
}

/// Bouton « Accueil » : revient au menu principal depuis un parcours en plusieurs étapes.
class DatHomeButton extends StatelessWidget {
  const DatHomeButton({super.key});

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: 'Retour à l\'accueil',
      icon: const Icon(Icons.home_outlined),
      onPressed: () => goHome(context),
    );
  }
}

void goHome(BuildContext context) => Navigator.of(context).popUntil((r) => r.isFirst);

/// Demande confirmation avant de quitter un écran avec une saisie non envoyée.
Future<bool> confirmLeave(BuildContext context, {String message = 'Les lectures en cours seront perdues.'}) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: const Text('Quitter cet écran ?'),
      content: Text(message),
      actions: [
        TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Rester')),
        TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Quitter')),
      ],
    ),
  );
  return ok ?? false;
}
