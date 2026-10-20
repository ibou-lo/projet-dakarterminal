import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../api/api_client.dart';
import '../config.dart';
import '../theme.dart';
import '../widgets/nav.dart';
import '../widgets/common.dart';
import 'scanner_screen.dart';

/// Étape 3 : affectation (ou déplacement) confirmée par le scan du QR de la place.
class PlaceScreen extends StatefulWidget {
  const PlaceScreen({super.key, required this.vin, this.isMove = false});

  final String vin;
  final bool isMove;

  @override
  State<PlaceScreen> createState() => _PlaceScreenState();
}

class _PlaceScreenState extends State<PlaceScreen> {
  Map<String, dynamic>? _suggestion;
  Map<String, dynamic>? _passport;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final s = await ApiClient.instance.get('/api/park/suggest-place',
          {'zone': ApiClient.instance.session?.zone ?? AppConfig.defaultZone}) as Map<String, dynamic>;
      if (widget.isMove) {
        // Le déplacement démarre : la position passe « à confirmer » jusqu'au scan d'arrivée.
        await ApiClient.instance.post('/api/vehicles/${widget.vin}/move');
      }
      if (mounted) setState(() => _suggestion = s);
    } catch (e) {
      if (mounted) showError(context, e);
    }
  }

  Future<void> _scanPlace() async {
    final code = await Navigator.push<String>(
      context,
      MaterialPageRoute(builder: (_) => const ScannerScreen(title: 'Scanner le QR de la place', hint: 'B-3-18')),
    );
    if (code == null || !mounted) return;
    final k = scanKindOf(code);
    if (k != ScanKind.place && k != ScanKind.other) {
      showError(context, 'Ce QR n\'est pas celui d\'une place : scannez le QR « PL:… » fixé sur la place.');
      return;
    }
    setState(() => _busy = true);
    try {
      final p = await ApiClient.instance.post('/api/vehicles/${widget.vin}/park', {'placeScan': normalizeScan(code)}) as Map<String, dynamic>;
      if (mounted) setState(() => _passport = p);
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final vehicle = _passport?['vehicle'] as Map<String, dynamic>?;
    final placeCode = vehicle?['placeCode'] as String? ?? _suggestion?['placeCode'] as String?;
    final scan = vehicle?['lastScan'] as Map<String, dynamic>?;
    return Scaffold(
      appBar: AppBar(
        leading: const DatBackButton(),
        title: Text(widget.isMove ? 'Déplacement' : 'Réception · 3/3 · Stationnement'),
        actions: const [DatHomeButton()],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(widget.vin, style: monoStyle.copyWith(color: DatColors.muted)),
          const SizedBox(height: 12),
          if (_suggestion != null && _passport == null)
            Text('Place suggérée · ${_suggestion!['freePlacesInZone']} places libres dans la zone',
                style: const TextStyle(fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          PlaceBoxes(placeCode: placeCode),
          const SizedBox(height: 16),
          DatCard(
            color: DatColors.ink,
            borderColor: DatColors.ink,
            child: const Row(children: [
              Icon(Icons.qr_code_2, color: DatColors.peach, size: 40),
              SizedBox(width: 12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('Scannez le QR de la place', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                  Text('Le scan confirme la position réelle, avec l\'heure et l\'agent.', style: TextStyle(color: DatColors.onInkMuted)),
                ]),
              ),
            ]),
          ),
          const SizedBox(height: 16),
          if (_passport != null)
            StatusBanner(
              kind: 'ok',
              title: 'Place ${vehicle?['placeLabel']} confirmée',
              subtitle: scan == null
                  ? null
                  : '${DateFormat('dd/MM HH:mm', 'fr_FR').format(DateTime.parse(scan['at'] as String).toLocal())} · ${scan['by']}',
            ),
          const SizedBox(height: 16),
          if (_passport == null)
            FilledButton.icon(
              onPressed: _busy ? null : _scanPlace,
              icon: const Icon(Icons.qr_code_scanner),
              label: const Text('Scanner la place'),
            )
          else
            FilledButton(
              onPressed: () => Navigator.of(context).popUntil((r) => r.isFirst),
              child: Text(widget.isMove ? 'Terminer' : 'Passeport numérique créé · Terminer'),
            ),
        ],
      ),
    );
  }
}
