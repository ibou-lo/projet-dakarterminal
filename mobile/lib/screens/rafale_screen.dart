import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../api/api_client.dart';
import '../config.dart';
import '../theme.dart';
import '../widgets/nav.dart';
import '../widgets/common.dart';

/// Inventaire en rafale : l'agent filme une allée, l'application lit plusieurs QR
/// à la fois (places « PL:B-3-18 » et véhicules « VH:<VIN> »), puis compare aux fiches.
class RafaleScreen extends StatefulWidget {
  const RafaleScreen({super.key});

  @override
  State<RafaleScreen> createState() => _RafaleScreenState();
}

class _RafaleScreenState extends State<RafaleScreen> {
  final _controller = MobileScannerController(detectionSpeed: DetectionSpeed.normal);
  String _zone = ApiClient.instance.session?.zone ?? AppConfig.defaultZone;
  int _allee = 3;

  /// Place → VIN lu à côté (null = place lue vide).
  final Map<String, String?> _reads = {};
  String? _lastPlace;
  Map<String, dynamic>? _result;
  bool _busy = false;

  /// Zones du plan (code → nombre d'allées), chargées depuis l'API (zones ajoutées par le superviseur incluses).
  Map<String, int> _zones = const {'A': 4, 'B': 4, 'C': 4, 'D': 4};

  @override
  void initState() {
    super.initState();
    _loadZones();
  }

  Future<void> _loadZones() async {
    try {
      final list = await ApiClient.instance.get('/api/zones') as List;
      final z = {for (final e in list.cast<Map<String, dynamic>>()) e['code'] as String: (e['allees'] as num).toInt()};
      if (z.isEmpty || !mounted) return;
      setState(() {
        _zones = z;
        if (!_zones.containsKey(_zone)) _zone = _zones.keys.first;
        if (_allee > (_zones[_zone] ?? 1)) _allee = 1;
      });
    } catch (_) {
      // hors ligne : on garde la liste par défaut
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onDetect(BarcodeCapture capture) {
    // Dans une même image : on associe chaque véhicule à la place détectée la plus proche.
    final places = <String, Offset>{};
    final vehicles = <String, Offset>{};
    for (final b in capture.barcodes) {
      final rawValue = b.rawValue;
      if (rawValue == null) continue;
      final raw = normalizeScan(rawValue);
      final c = _center(b);
      if (raw.startsWith('PL:')) places[raw.substring(3)] = c;
      if (raw.startsWith('VH:')) vehicles[raw.substring(3)] = c;
      // Les QR de clés (KEY:…) et de tickets sont ignorés : ils ne localisent pas un véhicule.
    }
    if (places.isEmpty && vehicles.isEmpty) return;
    setState(() {
      for (final p in places.keys) {
        if (!p.startsWith('$_zone-$_allee-')) continue;
        _reads.putIfAbsent(p, () => null);
        _lastPlace = p;
      }
      for (final v in vehicles.entries) {
        String? best = _lastPlace;
        double bestDist = double.infinity;
        for (final p in places.entries) {
          final d = (p.value - v.value).distance;
          if (d < bestDist) {
            bestDist = d;
            best = p.key;
          }
        }
        if (best != null && best.startsWith('$_zone-$_allee-')) _reads[best] = v.key;
      }
    });
  }

  Offset _center(Barcode b) {
    final corners = b.corners;
    if (corners.isEmpty) return Offset.zero;
    final dx = corners.map((o) => o.dx).reduce((a, c) => a + c) / corners.length;
    final dy = corners.map((o) => o.dy).reduce((a, c) => a + c) / corners.length;
    return Offset(dx, dy);
  }

  Future<void> _finish() async {
    setState(() => _busy = true);
    try {
      final res = await ApiClient.instance.post('/api/park/sweep', {
        'zone': _zone,
        'allee': _allee,
        'reads': _reads.entries.map((e) => {'placeCode': e.key, 'vin': e.value}).toList(),
      }) as Map<String, dynamic>;
      if (mounted) setState(() => _result = res);
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _fix(Map<String, dynamic> gap) async {
    try {
      await ApiClient.instance.post('/api/vehicles/${gap['vin']}/park', {'placeScan': gap['placeCode']});
      if (mounted) showInfo(context, 'Position corrigée en ${gap['placeCode']}');
    } catch (e) {
      if (mounted) showError(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final total = 24;
    final gaps = ((_result?['gaps'] as List?) ?? const []).cast<Map<String, dynamic>>();
    return PopScope(
      // Lectures non envoyées : confirmation avant de quitter
      canPop: _reads.isEmpty || _result != null,
      onPopInvoked: (didPop) async {
        if (didPop) return;
        final leave = await confirmLeave(context, message: '${_reads.length} lecture(s) de l\'allée ne sont pas envoyées.');
        if (leave && context.mounted) Navigator.of(context).pop();
      },
      child: Scaffold(
      appBar: AppBar(leading: const DatBackButton(), title: Text('Rafale · allée $_zone·$_allee')),
      body: Column(children: [
        SizedBox(
          height: 280,
          child: Stack(children: [
            MobileScanner(controller: _controller, onDetect: _onDetect),
            Positioned(
              left: 12,
              top: 12,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(color: DatColors.ink.withOpacity(0.7), borderRadius: BorderRadius.circular(6)),
                child: Text('${_reads.length} places lues', style: const TextStyle(color: Colors.white, fontSize: 12)),
              ),
            ),
          ]),
        ),
        Expanded(
          child: ListView(padding: const EdgeInsets.all(16), children: [
            Row(children: [
              DropdownButton<String>(
                value: _zone,
                items: _zones.keys.map((z) => DropdownMenuItem(value: z, child: Text('Zone $z'))).toList(),
                onChanged: (v) => setState(() {
                  _zone = v ?? _zone;
                  if (_allee > (_zones[_zone] ?? 1)) _allee = 1;
                  _reads.clear();
                  _result = null;
                }),
              ),
              const SizedBox(width: 12),
              DropdownButton<int>(
                value: _allee,
                items: List.generate(_zones[_zone] ?? 4, (i) => i + 1).map((a) => DropdownMenuItem(value: a, child: Text('Allée $a'))).toList(),
                onChanged: (v) => setState(() {
                  _allee = v ?? _allee;
                  _reads.clear();
                  _result = null;
                }),
              ),
            ]),
            Text('Places lues : ${_reads.length} / $total', style: const TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 6),
            LinearProgressIndicator(value: _reads.length / total, minHeight: 8, color: DatColors.ink, backgroundColor: DatColors.line),
            const SizedBox(height: 12),
            if (_result != null) ...[
              Row(children: [
                _Stat(value: '${_result!['conform']}', label: 'Conformes', color: DatColors.ok),
                _Stat(value: '${_result!['emptyConfirmed']}', label: 'Places vides', color: DatColors.ink),
                _Stat(value: '${gaps.length}', label: 'Écarts', color: DatColors.warnInk),
              ]),
              const SizedBox(height: 12),
              for (final g in gaps)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: DatCard(
                    borderColor: const Color(0xFFD98E1F),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      const Text('ÉCART À CONFIRMER', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: DatColors.warnInk)),
                      const SizedBox(height: 4),
                      Text(g['message'] as String),
                      if (g['kind'] == 'VEHICULE_INATTENDU') ...[
                        const SizedBox(height: 8),
                        FilledButton(onPressed: () => _fix(g), child: const Text('Corriger la position')),
                      ],
                    ]),
                  ),
                ),
              if (((_result!['notRead'] as List?) ?? const []).isNotEmpty)
                Text('Non lues : ${(_result!['notRead'] as List).join(', ')} — scanner à la main si besoin',
                    style: const TextStyle(color: DatColors.muted, fontSize: 13)),
            ],
            const SizedBox(height: 12),
            FilledButton(
              onPressed: _busy || _reads.isEmpty ? null : _finish,
              child: Text(_result == null ? 'Terminer l\'allée $_zone·$_allee' : 'Renvoyer les lectures'),
            ),
            const SizedBox(height: 8),
            const Text('Aide à la lecture : chaque écart est confirmé par l\'agent.',
                style: TextStyle(fontSize: 12, color: DatColors.muted)),
          ]),
        ),
      ]),
    ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.value, required this.label, required this.color});

  final String value;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) => Expanded(
        child: Container(
          margin: const EdgeInsets.only(right: 8),
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: DatColors.line)),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(value, style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: color)),
            Text(label, style: const TextStyle(fontSize: 12, color: DatColors.muted)),
          ]),
        ),
      );
}
