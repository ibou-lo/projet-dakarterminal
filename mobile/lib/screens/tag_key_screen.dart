import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/nav.dart';
import '../widgets/common.dart';
import 'scanner_screen.dart';

/// Étiquette hybride QR + RFID et armoire à clés, côté agent :
/// scanner le véhicule (VH:<VIN>) ou sa clé (KEY:<VIN>), associer la puce RFID,
/// enregistrer la sortie / le dépôt / la remise de la clé.
class TagKeyScreen extends StatefulWidget {
  const TagKeyScreen({super.key, this.initialCode});

  /// VIN déjà connu (ouverture depuis le scanner universel).
  final String? initialCode;

  @override
  State<TagKeyScreen> createState() => _TagKeyScreenState();
}

class _TagKeyScreenState extends State<TagKeyScreen> {
  Map<String, dynamic>? _tag;
  Map<String, dynamic>? _key;
  final _epc = TextEditingController();
  bool _reusable = false;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    final c = widget.initialCode;
    if (c != null) WidgetsBinding.instance.addPostFrameCallback((_) => _load(_vinOf(c)));
  }

  String _vinOf(String code) {
    final c = normalizeScan(code);
    if (c.startsWith('VH:')) return c.substring(3);
    if (c.startsWith('KEY:')) return c.substring(4);
    return c;
  }

  Future<void> _scanVehicle() async {
    final code = await Navigator.push<String>(context,
        MaterialPageRoute(builder: (_) => const ScannerScreen(title: 'Scanner l\'étiquette ou la clé', hint: 'VH:…, KEY:… ou DAT-…')));
    if (code == null || !mounted) return;
    final k = scanKindOf(code);
    if (k == ScanKind.place || k == ScanKind.ticket) {
      showError(context, 'Scannez l\'étiquette du véhicule (VH:…) ou le QR de sa clé (KEY:…).');
      return;
    }
    await _load(_vinOf(code));
  }

  Future<void> _load(String vin) async {
    setState(() => _busy = true);
    try {
      final list = await ApiClient.instance.get('/api/tags', {'q': vin}) as List;
      if (list.isEmpty) throw ApiError(404, 'Véhicule introuvable sur le parc');
      final tag = list.first as Map<String, dynamic>;
      final keys = await ApiClient.instance.get('/api/keys') as List;
      final key = keys.cast<Map<String, dynamic>>().where((k) => k['vin'] == tag['vin']).firstOrNull;
      setState(() {
        _tag = tag;
        _key = key;
        _epc.text = (tag['epc'] as String?) ?? '';
        _reusable = tag['reusable'] == true;
      });
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _scanEpc() async {
    final code = await Navigator.push<String>(
        context, MaterialPageRoute(builder: (_) => const ScannerScreen(title: 'Lire la puce / le code EPC', hint: 'E280-…')));
    if (code == null || !mounted) return;
    final k = scanKindOf(code);
    if (k == ScanKind.vehicle || k == ScanKind.key || k == ScanKind.place || k == ScanKind.ticket) {
      showError(context, 'Ce QR n\'est pas une puce RFID : lisez la puce avec le lecteur ou saisissez son code EPC.');
      return;
    }
    setState(() => _epc.text = normalizeScan(code));
  }

  Future<void> _associate() async {
    try {
      final t = await ApiClient.instance.post('/api/vehicles/${_tag!['vin']}/tag', {'epc': _epc.text.trim(), 'reusable': _reusable})
          as Map<String, dynamic>;
      setState(() => _tag = t);
      if (mounted) showInfo(context, 'Puce ${t['epc']} associée à ${t['shortCode']}');
    } catch (e) {
      if (mounted) showError(context, e);
    }
  }

  Future<void> _keyAction(String action) async {
    try {
      final k = await ApiClient.instance.post('/api/keys/${_tag!['vin']}', {'action': action}) as Map<String, dynamic>;
      setState(() => _key = k);
      if (mounted) showInfo(context, 'Clé enregistrée : ${_stateLabel(k['state'] as String)}');
    } catch (e) {
      if (mounted) showError(context, e);
    }
  }

  String _stateLabel(String s) => switch (s) {
        'ARMOIRE' => 'dans l\'armoire',
        'SORTIE' => 'sortie',
        'REMISE' => 'remise au client',
        _ => 'non enregistrée',
      };

  @override
  Widget build(BuildContext context) {
    final t = _tag;
    final k = _key;
    return Scaffold(
      appBar: AppBar(leading: const DatBackButton(), title: const Text('Étiquette & clés')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        FilledButton.icon(
          style: FilledButton.styleFrom(backgroundColor: DatColors.ink),
          onPressed: _busy ? null : _scanVehicle,
          icon: const Icon(Icons.qr_code_scanner),
          label: const Text('Scanner l\'étiquette ou la clé'),
        ),
        const SizedBox(height: 16),
        if (t != null) ...[
          DatCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(t['shortCode'] as String, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
              Text(t['vin'] as String, style: monoStyle.copyWith(color: DatColors.muted)),
              Text('${t['model'] ?? ''} · ${t['placeLabel']}', style: const TextStyle(color: DatColors.text2)),
            ]),
          ),
          const SizedBox(height: 12),
          DatCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('Puce RFID de l\'étiquette hybride', style: TextStyle(fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              Row(children: [
                Expanded(child: TextField(controller: _epc, style: monoStyle, decoration: const InputDecoration(labelText: 'EPC (E280-…)'))),
                const SizedBox(width: 8),
                IconButton.outlined(onPressed: _scanEpc, icon: const Icon(Icons.nfc), tooltip: 'Lire la puce'),
              ]),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                value: _reusable,
                onChanged: (v) => setState(() => _reusable = v),
                title: const Text('Étiquette suspendue réutilisable'),
                subtitle: const Text('Récupérée au portail de sortie'),
              ),
              FilledButton(onPressed: _epc.text.trim().isEmpty ? null : _associate, child: const Text('Associer au passeport')),
            ]),
          ),
          const SizedBox(height: 12),
          DatCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('Armoire à clés', style: TextStyle(fontWeight: FontWeight.w700)),
              const SizedBox(height: 6),
              StatusBanner(
                kind: k?['state'] == 'ARMOIRE' ? 'ok' : (k?['state'] == 'SORTIE' ? 'warn' : 'info'),
                title: 'Clé ${_stateLabel((k?['state'] as String?) ?? 'INCONNU')}',
                subtitle: k == null ? null : (k['state'] == 'ARMOIRE' ? k['slot'] as String? : k['holder'] as String?),
              ),
              const SizedBox(height: 10),
              Row(children: [
                Expanded(child: OutlinedButton(onPressed: () => _keyAction('SORTIE'), child: const Text('Sortir'))),
                const SizedBox(width: 8),
                Expanded(child: OutlinedButton(onPressed: () => _keyAction('DEPOSEE'), child: const Text('Déposer'))),
              ]),
              const SizedBox(height: 8),
              FilledButton(onPressed: () => _keyAction('REMISE_CLIENT'), child: const Text('Remise au client')),
            ]),
          ),
        ],
      ]),
    );
  }
}
