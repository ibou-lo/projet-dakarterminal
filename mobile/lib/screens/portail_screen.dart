import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/common.dart';
import 'login_screen.dart';
import 'scanner_screen.dart';

/// Contrôle de sortie au portail : scan du ticket client (QR signé) puis contrôles.
/// Les contrôles automatiques sont faits par le serveur ; l'agent valide le reste.
/// Traduit le contenu scanné (ou saisi) en requête pour le portail :
/// - QR du ticket « TKT.… » ou numéro tapé « TR-… » → ticketQr ;
/// - étiquette RFID « E280-… » → rfidEpc ;
/// - étiquette véhicule « VH:<VIN> », VIN ou code court « DAT-… » → vin.
Map<String, String> gateRequestFor(String raw) {
  final code = normalizeScan(raw);
  switch (scanKindOf(code)) {
    case ScanKind.ticket:
      return {'ticketQr': code};
    case ScanKind.epc:
      return {'rfidEpc': code};
    case ScanKind.vehicle:
      return {'vin': code.substring(3)};
    case ScanKind.key:
      // QR du porte-clé : le serveur retrouve le véhicule ; le ticket reste à présenter
      return {'vin': code.substring(4)};
    case ScanKind.place:
    case ScanKind.other:
      return {'vin': code};
  }
}

class PortailScreen extends StatefulWidget {
  const PortailScreen({super.key});

  @override
  State<PortailScreen> createState() => _PortailScreenState();
}

class _PortailScreenState extends State<PortailScreen> {
  Map<String, dynamic>? _result;
  final Set<String> _manualDone = {};
  bool _busy = false;

  Future<void> _scan() async {
    final code = await Navigator.push<String>(
      context,
      MaterialPageRoute(builder: (_) => const ScannerScreen(title: 'Scanner le ticket de retrait', hint: 'N° de ticket (TR-…), VIN ou DAT-…')),
    );
    if (code == null) return;
    if (scanKindOf(code) == ScanKind.place) {
      if (mounted) showError(context, 'Ce QR est celui d\'une place : scannez le ticket du client.');
      return;
    }
    setState(() {
      _busy = true;
      _manualDone.clear();
    });
    try {
      final body = gateRequestFor(code);
      final r = await ApiClient.instance.post('/api/gate/scan', body) as Map<String, dynamic>;
      setState(() => _result = r);
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _decide(String decision) async {
    final id = _result!['passageId'];
    try {
      await ApiClient.instance.post('/api/gate/passages/$id/decision', {
        'decision': decision,
        if (decision == 'BLOCKED') 'reason': 'Bloqué par l\'agent',
      });
      setState(() => _result = {..._result!, 'decision': decision});
      if (mounted) showInfo(context, decision == 'EXIT_VALIDATED' ? 'Sortie validée · historique conservé' : 'Sortie bloquée · superviseur alerté');
    } catch (e) {
      if (mounted) showError(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final r = _result;
    final checks = ((r?['checks'] as List?) ?? const []).cast<Map<String, dynamic>>();
    final manual = checks.where((c) => c['result'] == 'MANUAL').map((c) => c['code'] as String).toSet();
    final pending = r?['decision'] == 'PENDING_AGENT';
    return Scaffold(
      appBar: AppBar(
        title: Text('Contrôle de sortie · ${ApiClient.instance.session?.fullName ?? ''}'),
        actions: [
          IconButton(
            tooltip: 'Déconnexion',
            icon: const Icon(Icons.logout),
            onPressed: () async {
              await ApiClient.instance.logout();
              if (context.mounted) Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const LoginScreen()));
            },
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          FilledButton.icon(
            style: FilledButton.styleFrom(backgroundColor: DatColors.ink),
            onPressed: _busy ? null : _scan,
            icon: const Icon(Icons.qr_code_scanner),
            label: const Text('Scanner un ticket'),
          ),
          const SizedBox(height: 16),
          if (r != null) ...[
            StatusBanner(
              kind: r['decision'] == 'BLOCKED' ? 'danger' : 'ok',
              title: r['decision'] == 'EXIT_VALIDATED' ? 'Sortie validée' : r['title'] as String,
              subtitle: '${r['ticketNumber'] ?? 'Sans ticket'} · ${r['vin']}',
            ),
            const SizedBox(height: 12),
            DatCard(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
              child: Column(children: [
                for (final c in checks)
                  Container(
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Color(0xFFEEEBE3)))),
                    child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      if (c['result'] == 'MANUAL')
                        Checkbox(
                          value: _manualDone.contains(c['code']),
                          onChanged: pending
                              ? (v) => setState(() => v == true ? _manualDone.add(c['code'] as String) : _manualDone.remove(c['code']))
                              : null,
                        )
                      else
                        Padding(
                          padding: const EdgeInsets.all(12),
                          child: Icon(c['result'] == 'OK' ? Icons.check : (c['result'] == 'KO' ? Icons.close : Icons.info_outline),
                              color: c['result'] == 'OK' ? DatColors.ok : (c['result'] == 'KO' ? DatColors.danger : DatColors.muted)),
                        ),
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.only(top: 10),
                          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text(c['label'] as String, style: const TextStyle(fontWeight: FontWeight.w600)),
                            Text(c['note'] as String, style: const TextStyle(fontSize: 12, color: DatColors.muted)),
                          ]),
                        ),
                      ),
                    ]),
                  ),
              ]),
            ),
            const SizedBox(height: 12),
            const StatusBanner(
              kind: 'info',
              title: 'Comparaison photo : aide à la vérification uniquement',
              subtitle: 'L\'agent confirme visuellement l\'état par rapport à la réception.',
            ),
            const SizedBox(height: 16),
            if (pending)
              Row(children: [
                OutlinedButton(
                  style: OutlinedButton.styleFrom(foregroundColor: DatColors.danger, side: const BorderSide(color: DatColors.danger)),
                  onPressed: () => _decide('BLOCKED'),
                  child: const Text('Bloquer'),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton(
                    style: FilledButton.styleFrom(backgroundColor: DatColors.ok),
                    onPressed: _manualDone.containsAll(manual) ? () => _decide('EXIT_VALIDATED') : null,
                    child: const Text('Valider la sortie'),
                  ),
                ),
              ]),
          ],
        ],
      ),
    );
  }
}
