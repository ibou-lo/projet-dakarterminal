import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../config.dart';
import '../theme.dart';
import '../widgets/common.dart';
import 'chat_screen.dart';
import 'login_screen.dart';
import 'place_screen.dart';
import 'rafale_screen.dart';
import 'reception_vin_screen.dart';
import 'scanner_screen.dart';
import 'tag_key_screen.dart';

/// Espace de l'agent de parc : barre de navigation en bas (Accueil · Scanner · Assistant · Profil).
class AgentHome extends StatefulWidget {
  const AgentHome({super.key});

  @override
  State<AgentHome> createState() => _AgentHomeState();
}

class _AgentHomeState extends State<AgentHome> {
  int _tab = 0;

  @override
  Widget build(BuildContext context) {
    return PopScope(
      // Bouton retour du téléphone : revient d'abord à l'onglet Accueil
      canPop: _tab == 0,
      onPopInvoked: (didPop) {
        if (!didPop && _tab != 0) setState(() => _tab = 0);
      },
      child: Scaffold(
        body: IndexedStack(index: _tab, children: [
          _HomeTab(onOpenTab: (i) => setState(() => _tab = i)),
          const _ScanTab(),
          const ChatScreen(),
          const _ProfileTab(),
        ]),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _tab,
          onDestinationSelected: (i) => setState(() => _tab = i),
          backgroundColor: Colors.white,
          indicatorColor: const Color(0xFFFBE0CC),
          height: 66,
          destinations: const [
            NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: 'Accueil'),
            NavigationDestination(icon: Icon(Icons.qr_code_scanner), label: 'Scanner'),
            NavigationDestination(icon: Icon(Icons.auto_awesome_outlined), selectedIcon: Icon(Icons.auto_awesome), label: 'Assistant'),
            NavigationDestination(icon: Icon(Icons.person_outline), selectedIcon: Icon(Icons.person), label: 'Profil'),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------- Accueil

class _HomeTab extends StatelessWidget {
  const _HomeTab({required this.onOpenTab});

  final ValueChanged<int> onOpenTab;

  void _open(BuildContext context, Widget screen) => Navigator.push(context, MaterialPageRoute(builder: (_) => screen));

  @override
  Widget build(BuildContext context) {
    final s = ApiClient.instance.session;
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('Dakar AutoTrack'),
          Text('${s?.fullName ?? ''} · zone ${s?.zone ?? '—'}',
              style: const TextStyle(fontSize: 12, color: DatColors.onInkMuted, fontWeight: FontWeight.w400)),
        ]),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        children: [
          // Tuiles filtrées selon les privilèges du profil (réglés par l'administrateur)
          if (s?.can('VEHICULE_RECEPTIONNER') ?? true)
            _BigAction(
              icon: Icons.document_scanner_outlined,
              title: 'Réceptionner un véhicule',
              subtitle: 'VIN → état → place',
              onTap: () => _open(context, const ReceptionVinScreen()),
            ),
          if ((s?.can('VEHICULE_STATIONNER') ?? true) || (s?.can('INVENTAIRE_RAFALE') ?? true)) ...[
            const _Section('Sur le parc'),
            _Grid(children: [
              if (s?.can('VEHICULE_STATIONNER') ?? true)
                _Tile(icon: Icons.swap_horiz, title: 'Déplacer', subtitle: 'Véhicule puis place', onTap: () => _startMove(context)),
              if (s?.can('INVENTAIRE_RAFALE') ?? true)
                _Tile(icon: Icons.video_camera_back_outlined, title: 'Inventaire', subtitle: 'Rafale d\'une allée',
                    onTap: () => _open(context, const RafaleScreen())),
            ]),
          ],
          if ((s?.can('ETIQUETTE_ASSOCIER') ?? true) || (s?.can('CLE_MOUVEMENTER') ?? true)) ...[
            const _Section('Étiquettes & clés'),
            _Grid(children: [
              if (s?.can('ETIQUETTE_ASSOCIER') ?? true)
                _Tile(icon: Icons.nfc, title: 'Étiquette RFID', subtitle: 'Associer la puce',
                    onTap: () => _open(context, const TagKeyScreen())),
              if (s?.can('CLE_MOUVEMENTER') ?? true)
                _Tile(icon: Icons.key_outlined, title: 'Clés', subtitle: 'Sortir · déposer',
                    onTap: () => _open(context, const TagKeyScreen())),
            ]),
          ],
          const _Section('Aide'),
          _Grid(children: [
            _Tile(icon: Icons.qr_code_scanner, title: 'Scanner un QR', subtitle: 'Identifier un code', onTap: () => onOpenTab(1)),
            _Tile(icon: Icons.auto_awesome, title: 'Assistant', subtitle: 'Voix, photo, texte', onTap: () => onOpenTab(2)),
          ]),
        ],
      ),
    );
  }
}

/// Déplacement : scan du véhicule (VH:, KEY:, DAT-, puce), puis écran de la nouvelle place.
Future<void> _startMove(BuildContext context, [String? preScanned]) async {
  final code = preScanned ??
      await Navigator.push<String>(
          context, MaterialPageRoute(builder: (_) => const ScannerScreen(title: 'Scanner le véhicule', hint: 'VH:…, KEY:… ou DAT-…')));
  if (code == null || !context.mounted) return;
  final kind = scanKindOf(code);
  if (kind == ScanKind.place || kind == ScanKind.ticket) {
    showError(context, 'Ce QR est celui ${kind == ScanKind.place ? "d'une place" : "d'un ticket"} : scannez d\'abord l\'étiquette du véhicule (VH:…) ou sa clé.');
    return;
  }
  try {
    final p = await ApiClient.instance.get('/api/vehicles/${Uri.encodeComponent(normalizeScan(code))}') as Map<String, dynamic>;
    final vin = (p['vehicle'] as Map<String, dynamic>)['vin'] as String;
    if (!context.mounted) return;
    Navigator.push(context, MaterialPageRoute(builder: (_) => PlaceScreen(vin: vin, isMove: true)));
  } catch (e) {
    if (context.mounted) showError(context, e);
  }
}

// ---------------------------------------------------------------------------- Scanner universel

class _ScanTab extends StatefulWidget {
  const _ScanTab();

  @override
  State<_ScanTab> createState() => _ScanTabState();
}

class _ScanTabState extends State<_ScanTab> {
  String? _code;
  ScanKind? _kind;
  Map<String, dynamic>? _vehicle;
  String? _info;
  bool _busy = false;

  Future<void> _scan() async {
    final code = await Navigator.push<String>(
        context, MaterialPageRoute(builder: (_) => const ScannerScreen(title: 'Scanner un QR', hint: 'VH:…, KEY:…, PL:…, DAT-…')));
    if (code == null || !mounted) return;
    final c = normalizeScan(code);
    final k = scanKindOf(c);
    setState(() {
      _code = c;
      _kind = k;
      _vehicle = null;
      _info = null;
    });
    switch (k) {
      case ScanKind.place:
        setState(() => _info = 'QR de la place ${c.substring(3)}. Il se scanne à l\'étape « place » d\'une réception ou d\'un déplacement.');
      case ScanKind.ticket:
        setState(() => _info = 'Ticket de retrait : il se contrôle au portail de sortie.');
      default:
        await _loadVehicle(c);
    }
  }

  Future<void> _loadVehicle(String c) async {
    setState(() => _busy = true);
    try {
      final p = await ApiClient.instance.get('/api/vehicles/${Uri.encodeComponent(c)}') as Map<String, dynamic>;
      setState(() => _vehicle = p['vehicle'] as Map<String, dynamic>);
    } catch (e) {
      setState(() => _info = e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _kindLabel(ScanKind k) => switch (k) {
        ScanKind.vehicle => 'Étiquette véhicule',
        ScanKind.key => 'QR de clé',
        ScanKind.place => 'QR de place',
        ScanKind.ticket => 'Ticket de retrait',
        ScanKind.epc => 'Puce RFID',
        ScanKind.other => 'Code saisi',
      };

  @override
  Widget build(BuildContext context) {
    final v = _vehicle;
    return Scaffold(
      appBar: AppBar(automaticallyImplyLeading: false, title: const Text('Scanner un QR')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        const Text('Scannez n\'importe quel QR du terminal : l\'application reconnaît le type et propose les actions.',
            style: TextStyle(color: DatColors.text2)),
        const SizedBox(height: 14),
        FilledButton.icon(
          style: FilledButton.styleFrom(backgroundColor: DatColors.ink),
          onPressed: _busy ? null : _scan,
          icon: const Icon(Icons.qr_code_scanner),
          label: const Text('Scanner'),
        ),
        const SizedBox(height: 16),
        if (_busy) const Center(child: CircularProgressIndicator()),
        if (_code != null && _kind != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Row(children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(color: DatColors.infoBg, borderRadius: BorderRadius.circular(999)),
                child: Text(_kindLabel(_kind!), style: const TextStyle(color: DatColors.infoInk, fontWeight: FontWeight.w600, fontSize: 12)),
              ),
              const SizedBox(width: 8),
              Expanded(child: Text(_code!, style: monoStyle.copyWith(fontSize: 12, color: DatColors.muted), overflow: TextOverflow.ellipsis)),
            ]),
          ),
        if (_info != null) StatusBanner(kind: 'info', title: _info!),
        if (v != null) ...[
          DatCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(v['shortCode'] as String? ?? '', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
              Text(v['vin'] as String? ?? '', style: monoStyle.copyWith(color: DatColors.muted)),
              Text('${v['model'] ?? ''} · ${v['placeLabel'] ?? 'Non affecté'}', style: const TextStyle(color: DatColors.text2)),
              const SizedBox(height: 6),
              Text(v['statusLabel'] as String? ?? v['status'] as String? ?? '', style: const TextStyle(fontWeight: FontWeight.w600)),
            ]),
          ),
          const SizedBox(height: 12),
          if (ApiClient.instance.session?.can('VEHICULE_STATIONNER') ?? true)
          FilledButton.icon(
            onPressed: () => _startMove(context, v['vin'] as String),
            icon: const Icon(Icons.swap_horiz),
            label: const Text('Déplacer ce véhicule'),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () => Navigator.push(
                context, MaterialPageRoute(builder: (_) => TagKeyScreen(initialCode: v['vin'] as String))),
            icon: const Icon(Icons.key_outlined),
            label: const Text('Étiquette & clé'),
          ),
        ],
      ]),
    );
  }
}

// ---------------------------------------------------------------------------- Profil

class _ProfileTab extends StatelessWidget {
  const _ProfileTab();

  @override
  Widget build(BuildContext context) {
    final s = ApiClient.instance.session;
    final initials = (s?.fullName ?? '?').split(RegExp(r'[\s.]+')).where((p) => p.isNotEmpty).map((p) => p[0]).take(2).join().toUpperCase();
    return Scaffold(
      appBar: AppBar(automaticallyImplyLeading: false, title: const Text('Profil')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        DatCard(
          child: Row(children: [
            CircleAvatar(radius: 26, backgroundColor: DatColors.ink, child: Text(initials, style: const TextStyle(color: Colors.white))),
            const SizedBox(width: 14),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(s?.fullName ?? '', style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
                Text('${roleLabel(s?.role)} · zone ${s?.zone ?? '—'}', style: const TextStyle(color: DatColors.muted)),
              ]),
            ),
          ]),
        ),
        const SizedBox(height: 12),
        DatCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Serveur', style: TextStyle(fontWeight: FontWeight.w700)),
            Text(AppConfig.apiBaseUrl, style: monoStyle.copyWith(fontSize: 12, color: DatColors.muted)),
            const SizedBox(height: 8),
            const Text('Données fictives de démonstration.', style: TextStyle(fontSize: 12, color: DatColors.muted)),
          ]),
        ),
        const SizedBox(height: 20),
        OutlinedButton.icon(
          style: OutlinedButton.styleFrom(foregroundColor: DatColors.danger, minimumSize: const Size.fromHeight(50)),
          onPressed: () async {
            await ApiClient.instance.logout();
            if (context.mounted) {
              Navigator.of(context).pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const LoginScreen()), (_) => false);
            }
          },
          icon: const Icon(Icons.logout),
          label: const Text('Déconnexion'),
        ),
      ]),
    );
  }
}

// ---------------------------------------------------------------------------- Éléments

class _Section extends StatelessWidget {
  const _Section(this.title);

  final String title;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(2, 20, 0, 8),
        child: Text(title.toUpperCase(),
            style: const TextStyle(fontSize: 12, letterSpacing: .8, fontWeight: FontWeight.w700, color: DatColors.muted)),
      );
}

class _Grid extends StatelessWidget {
  const _Grid({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        for (var i = 0; i < children.length; i++) ...[
          if (i > 0) const SizedBox(width: 12),
          Expanded(child: children[i]),
        ],
        if (children.length == 1) ...[const SizedBox(width: 12), const Spacer()],
      ]);
}

class _BigAction extends StatelessWidget {
  const _BigAction({required this.icon, required this.title, required this.subtitle, required this.onTap});

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: DatColors.ink,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Row(children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(color: DatColors.orange, borderRadius: BorderRadius.circular(14)),
              child: Icon(icon, color: Colors.white, size: 28),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Colors.white)),
                Text(subtitle, style: const TextStyle(color: DatColors.onInkMuted)),
              ]),
            ),
            const Icon(Icons.chevron_right, color: DatColors.onInkMuted),
          ]),
        ),
      ),
    );
  }
}

class _Tile extends StatelessWidget {
  const _Tile({required this.icon, required this.title, required this.subtitle, required this.onTap});

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: onTap,
        child: Container(
          height: 118,
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(14), border: Border.all(color: DatColors.line)),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(color: DatColors.ground, borderRadius: BorderRadius.circular(10)),
              child: Icon(icon, color: DatColors.ink),
            ),
            const Spacer(),
            Text(title, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: DatColors.ink)),
            Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, color: DatColors.muted)),
          ]),
        ),
      ),
    );
  }
}
