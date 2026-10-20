import 'package:flutter/material.dart';

import '../theme.dart';

/// Carte blanche bordée (style de la maquette).
class DatCard extends StatelessWidget {
  const DatCard({super.key, required this.child, this.padding = const EdgeInsets.all(14), this.color, this.borderColor});

  final Widget child;
  final EdgeInsets padding;
  final Color? color;
  final Color? borderColor;

  @override
  Widget build(BuildContext context) => Container(
        padding: padding,
        decoration: BoxDecoration(
          color: color ?? Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: borderColor ?? DatColors.line),
        ),
        child: child,
      );
}

/// Bandeau de statut coloré.
class StatusBanner extends StatelessWidget {
  const StatusBanner({super.key, required this.title, this.subtitle, required this.kind});

  final String title;
  final String? subtitle;
  final String kind; // ok | warn | info | danger

  @override
  Widget build(BuildContext context) {
    final (bg, fg, icon) = switch (kind) {
      'ok' => (DatColors.okBg, DatColors.okInk, Icons.check_circle_outline),
      'warn' => (DatColors.warnBg, DatColors.warnInk, Icons.schedule),
      'danger' => (const Color(0xFFF8DEDA), const Color(0xFF7E1D14), Icons.warning_amber_rounded),
      _ => (DatColors.infoBg, DatColors.infoInk, Icons.info_outline),
    };
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(12)),
      child: Row(children: [
        Icon(icon, color: fg),
        const SizedBox(width: 10),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: TextStyle(color: fg, fontWeight: FontWeight.w700)),
            if (subtitle != null) Text(subtitle!, style: TextStyle(color: fg, fontSize: 13)),
          ]),
        ),
      ]),
    );
  }
}

/// Trois cases Zone / Allée / Place.
class PlaceBoxes extends StatelessWidget {
  const PlaceBoxes({super.key, required this.placeCode});

  final String? placeCode;

  @override
  Widget build(BuildContext context) {
    final parts = (placeCode != null && placeCode!.contains('-')) ? placeCode!.split('-') : ['—', '—', '—'];
    const labels = ['Zone', 'Allée', 'Place'];
    return Row(
      children: List.generate(3, (i) {
        return Expanded(
          child: Container(
            margin: EdgeInsets.only(right: i < 2 ? 8 : 0),
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: i == 2 ? DatColors.orange : DatColors.line, width: i == 2 ? 2 : 1),
            ),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(labels[i], style: const TextStyle(fontSize: 12, color: DatColors.muted)),
              Text(parts[i], style: const TextStyle(fontSize: 30, fontWeight: FontWeight.w800)),
            ]),
          ),
        );
      }),
    );
  }
}

void showError(BuildContext context, Object e) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString()), backgroundColor: DatColors.danger));
}

void showInfo(BuildContext context, String msg) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg), backgroundColor: DatColors.ok));
}

/// Nettoie un code scanné ou saisi : espaces, retours à la ligne, casse (sauf ticket signé).
String normalizeScan(String raw) {
  final t = raw.trim().replaceAll(RegExp(r'\s+'), '');
  if (t.toUpperCase().startsWith('TKT.')) return 'TKT.${t.substring(4)}';
  return t.toUpperCase();
}

/// Type de QR reconnu : place, véhicule, clé, ticket, puce RFID ou autre (VIN / code DAT saisi).
enum ScanKind { place, vehicle, key, ticket, epc, other }

ScanKind scanKindOf(String code) {
  final c = normalizeScan(code);
  if (c.startsWith('PL:')) return ScanKind.place;
  if (c.startsWith('VH:')) return ScanKind.vehicle;
  if (c.startsWith('KEY:')) return ScanKind.key;
  if (c.startsWith('TKT.') || c.startsWith('TR-')) return ScanKind.ticket;
  if (RegExp(r'^E2[0-9A-Z]{2}-[0-9A-Z-]+$').hasMatch(c)) return ScanKind.epc;
  return ScanKind.other;
}
