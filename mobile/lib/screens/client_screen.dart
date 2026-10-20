import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/common.dart';
import 'login_screen.dart';

/// Espace client : suivi du véhicule, alerte SMS et ticket de retrait (QR signé).
class ClientScreen extends StatefulWidget {
  const ClientScreen({super.key});

  @override
  State<ClientScreen> createState() => _ClientScreenState();
}

class _ClientScreenState extends State<ClientScreen> {
  Map<String, dynamic>? _status;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final s = await ApiClient.instance.get('/api/client/me') as Map<String, dynamic>;
      if (mounted) setState(() => _status = s);
    } catch (e) {
      if (mounted) showError(context, e);
    }
  }

  Future<void> _notify() async {
    try {
      final s = await ApiClient.instance.post('/api/client/notify', {'enabled': true}) as Map<String, dynamic>;
      setState(() => _status = s);
      if (mounted) showInfo(context, 'Vous serez prévenu par SMS dès que le véhicule est prêt');
    } catch (e) {
      if (mounted) showError(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = _status;
    final ticket = s?['ticket'] as Map<String, dynamic>?;
    final fmt = DateFormat('dd/MM · HH:mm', 'fr_FR');
    return Scaffold(
      backgroundColor: DatColors.orange,
      appBar: AppBar(
        backgroundColor: DatColors.orange,
        foregroundColor: DatColors.ink,
        titleTextStyle: const TextStyle(color: DatColors.ink, fontSize: 17, fontWeight: FontWeight.w800),
        title: const Text('Suivi de mon véhicule'),
        actions: [
          IconButton(onPressed: _load, icon: const Icon(Icons.refresh), tooltip: 'Actualiser'),
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
      body: s == null
          ? const Center(child: CircularProgressIndicator(color: Colors.white))
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(padding: const EdgeInsets.all(16), children: [
                DatCard(
                  padding: const EdgeInsets.all(18),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('${s['model'] ?? ''} · ${s['color'] ?? ''}', style: const TextStyle(color: DatColors.muted)),
                    Text(s['shortCode'] as String, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
                    const SizedBox(height: 10),
                    StatusBanner(
                      kind: s['ready'] == true ? 'ok' : 'warn',
                      title: s['statusLabel'] as String,
                      subtitle: s['ready'] == true
                          ? 'Présentez votre ticket et votre pièce d\'identité au portail'
                          : 'Il manque : ${(s['missing'] as List).join(', ')}',
                    ),
                    if (s['ready'] != true) ...[
                      const SizedBox(height: 12),
                      if (s['notifyBySms'] == true)
                        const Text('✓ Vous serez prévenu par SMS', style: TextStyle(color: DatColors.okInk, fontWeight: FontWeight.w600))
                      else
                        OutlinedButton(onPressed: _notify, child: const Text('Me prévenir par SMS')),
                    ],
                  ]),
                ),
                const SizedBox(height: 12),
                // QR d'accueil : file d'attente et horaires du portail
                DatCard(
                  child: Row(children: [
                    const Icon(Icons.groups_outlined, color: DatColors.ink),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text('${s['queueAhead'] ?? 0} véhicule(s) en contrôle au portail',
                            style: const TextStyle(fontWeight: FontWeight.w700)),
                        Text((s['gateHours'] as String?) ?? '', style: const TextStyle(fontSize: 12, color: DatColors.muted)),
                      ]),
                    ),
                  ]),
                ),
                const SizedBox(height: 16),
                if (ticket != null)
                  DatCard(
                    padding: const EdgeInsets.all(20),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      const Text('TICKET DE RETRAIT', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, letterSpacing: 0.8, color: DatColors.muted)),
                      Text(ticket['ticketNumber'] as String, style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800)),
                      const SizedBox(height: 12),
                      Center(
                        child: QrImageView(
                          data: ticket['qrToken'] as String,
                          size: 220,
                          eyeStyle: const QrEyeStyle(eyeShape: QrEyeShape.square, color: DatColors.ink),
                          dataModuleStyle: const QrDataModuleStyle(dataModuleShape: QrDataModuleShape.square, color: DatColors.ink),
                        ),
                      ),
                      const Divider(height: 28),
                      _kv('Emplacement', ticket['placeLabel'] as String? ?? '—'),
                      _kv('Bénéficiaire', ticket['beneficiary'] as String? ?? '—'),
                      _kv('Valable jusqu\'au', fmt.format(DateTime.parse(ticket['validUntil'] as String).toLocal())),
                      if (ticket['slot'] != null) _kv('Créneau', ticket['slot'] as String),
                      if (ticket['used'] == true) const StatusBanner(kind: 'info', title: 'Ticket utilisé · véhicule sorti'),
                    ]),
                  ),
                const SizedBox(height: 12),
                const Text('Vous ne voyez que les informations de votre propre véhicule.',
                    textAlign: TextAlign.center, style: TextStyle(color: Color(0xFF1C1208), fontSize: 12)),
              ]),
            ),
    );
  }

  Widget _kv(String k, String v) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          SizedBox(width: 120, child: Text(k, style: const TextStyle(color: DatColors.muted))),
          Expanded(child: Text(v, style: const TextStyle(fontWeight: FontWeight.w700))),
        ]),
      );
}
