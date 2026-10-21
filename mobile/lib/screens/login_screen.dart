import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../config.dart';
import '../theme.dart';
import 'agent_home.dart';
import 'client_screen.dart';
import 'portail_screen.dart';

/// Connexion : personnel du terminal (identifiant + mot de passe) ou client (BL + code SMS).
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _user = TextEditingController();
  final _pwd = TextEditingController();
  final _bl = TextEditingController();
  final _code = TextEditingController();
  bool _client = false;
  bool _showPwd = false;
  bool _busy = false;
  String? _error;

  static const _demoStaff = [
    ('agent.diallo', 'Agent@2026', 'Agent de parc', Icons.qr_code_scanner),
    ('portail.sarr', 'Portail@2026', 'Agent de portail', Icons.fence_outlined),
  ];

  @override
  void dispose() {
    for (final c in [_user, _pwd, _bl, _code]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    final staff = !_client;
    if (staff && (_user.text.trim().isEmpty || _pwd.text.isEmpty)) {
      setState(() => _error = 'Saisissez votre identifiant et votre mot de passe.');
      return;
    }
    if (!staff && (_bl.text.trim().isEmpty || _code.text.trim().isEmpty)) {
      setState(() => _error = 'Saisissez le numéro de BL et le code reçu par SMS.');
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final s = staff
          ? await ApiClient.instance.login(_user.text.trim(), _pwd.text)
          : await ApiClient.instance.clientLogin(_bl.text.trim(), _code.text.trim());
      if (!mounted) return;
      if (s.mustChangePassword) {
        await ApiClient.instance.logout();
        setState(() => _error = 'Mot de passe provisoire : choisissez d\'abord votre mot de passe personnel sur le poste web (Mon compte).');
        return;
      }
      if (s.role == 'ADMIN') {
        // L'administration se fait sur le poste web (séparation des tâches, pas d'opérations terrain).
        await ApiClient.instance.logout();
        setState(() => _error = 'Profil administrateur : utilisez le poste web (menu Administration).');
        return;
      }
      final Widget next = switch (s.role) {
        'CLIENT' => const ClientScreen(),
        'PORTAIL' => const PortailScreen(),
        _ => const AgentHome(),
      };
      Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => next));
    } catch (e) {
      if (mounted) {
        final msg = e.toString();
        setState(() => _error = msg.contains('Failed to fetch') ||
                msg.contains('SocketException') ||
                msg.contains('ClientException') ||
                msg.contains('Timeout') ||
                msg.contains('Timed out') ||
                msg.contains('Connection')
            ? 'Serveur injoignable (${AppConfig.apiBaseUrl}). '
                'Vérifiez le réseau, puis réessayez : l\'API Render Free met parfois une minute à se réveiller.'
            : msg);
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: DatColors.ink,
      body: SafeArea(
        bottom: false,
        child: LayoutBuilder(
          builder: (context, c) => SingleChildScrollView(
            child: ConstrainedBox(
              constraints: BoxConstraints(minHeight: c.maxHeight),
              child: IntrinsicHeight(
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  const _Hero(),
                  Expanded(
                    child: Container(
                      decoration: const BoxDecoration(
                        color: DatColors.ground,
                        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
                      ),
                      padding: const EdgeInsets.fromLTRB(20, 22, 20, 28),
                      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                        _Segmented(
                          client: _client,
                          onChanged: (v) => setState(() {
                            _client = v;
                            _error = null;
                          }),
                        ),
                        const SizedBox(height: 20),
                        Text(_client ? 'Suivre mon véhicule' : 'Connexion',
                            style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800, color: DatColors.ink)),
                        const SizedBox(height: 4),
                        Text(
                          _client
                              ? 'Avec le numéro de BL et le code reçu par SMS.'
                              : 'Accès réservé au personnel du terminal.',
                          style: const TextStyle(color: DatColors.muted),
                        ),
                        const SizedBox(height: 18),
                        AnimatedSwitcher(
                          duration: const Duration(milliseconds: 180),
                          child: _client ? _clientFields() : _staffFields(),
                        ),
                        if (_error != null) ...[
                          const SizedBox(height: 4),
                          Container(
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(color: DatColors.dangerBg, borderRadius: BorderRadius.circular(12)),
                            child: Row(children: [
                              const Icon(Icons.error_outline, color: DatColors.danger, size: 20),
                              const SizedBox(width: 8),
                              Expanded(child: Text(_error!, style: const TextStyle(color: DatColors.danger))),
                            ]),
                          ),
                        ],
                        const SizedBox(height: 16),
                        FilledButton(
                          onPressed: _busy ? null : _submit,
                          child: _busy
                              ? const Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                                  SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.4, color: Colors.white)),
                                  SizedBox(width: 10),
                                  Text('Connexion…'),
                                ])
                              : Text(_client ? 'Suivre mon véhicule' : 'Se connecter'),
                        ),
                        const SizedBox(height: 22),
                        if (!_client) ..._demoBlock() else _clientHelp(),
                        const Spacer(),
                        const SizedBox(height: 16),
                        Text(AppConfig.apiBaseUrl,
                            textAlign: TextAlign.center,
                            style: monoStyle.copyWith(fontSize: 11, color: DatColors.muted)),
                        const SizedBox(height: 8),
                        const Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                          Icon(Icons.verified_user_outlined, size: 16, color: DatColors.ok),
                          SizedBox(width: 6),
                          Flexible(
                            child: Text('Connexion sécurisée · données fictives de démonstration',
                                style: TextStyle(fontSize: 12, color: DatColors.muted), textAlign: TextAlign.center),
                          ),
                        ]),
                      ]),
                    ),
                  ),
                ]),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _staffFields() => Column(key: const ValueKey('staff'), children: [
        _Field(
          controller: _user,
          label: 'Identifiant',
          icon: Icons.person_outline,
          action: TextInputAction.next,
          autofill: const [AutofillHints.username],
        ),
        const SizedBox(height: 12),
        _Field(
          controller: _pwd,
          label: 'Mot de passe',
          icon: Icons.lock_outline,
          obscure: !_showPwd,
          action: TextInputAction.done,
          onSubmitted: (_) => _submit(),
          autofill: const [AutofillHints.password],
          suffix: IconButton(
            tooltip: _showPwd ? 'Masquer le mot de passe' : 'Afficher le mot de passe',
            icon: Icon(_showPwd ? Icons.visibility_off_outlined : Icons.visibility_outlined, color: DatColors.muted),
            onPressed: () => setState(() => _showPwd = !_showPwd),
          ),
        ),
        const SizedBox(height: 12),
      ]);

  Widget _clientFields() => Column(key: const ValueKey('client'), children: [
        _Field(
          controller: _bl,
          label: 'Numéro de BL',
          hint: 'DKR-88341',
          icon: Icons.description_outlined,
          action: TextInputAction.next,
          caps: TextCapitalization.characters,
        ),
        const SizedBox(height: 12),
        _Field(
          controller: _code,
          label: 'Code reçu par SMS',
          hint: '4 chiffres',
          icon: Icons.sms_outlined,
          keyboard: TextInputType.number,
          action: TextInputAction.done,
          onSubmitted: (_) => _submit(),
        ),
        const SizedBox(height: 12),
      ]);

  List<Widget> _demoBlock() => [
        const Row(children: [
          Text('Comptes de démonstration', style: TextStyle(fontWeight: FontWeight.w700, color: DatColors.ink)),
          Spacer(),
          Text('un appui pour remplir', style: TextStyle(fontSize: 12, color: DatColors.muted)),
        ]),
        const SizedBox(height: 10),
        Row(children: [
          for (var i = 0; i < _demoStaff.length; i++) ...[
            if (i > 0) const SizedBox(width: 10),
            Expanded(child: _demoCard(_demoStaff[i])),
          ],
        ]),
      ];

  Widget _demoCard((String, String, String, IconData) a) {
    final selected = _user.text == a.$1;
    return Material(
      color: selected ? const Color(0xFFFDF3EC) : Colors.white,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: () => setState(() {
          _user.text = a.$1;
          _pwd.text = a.$2;
          _error = null;
        }),
        child: Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: selected ? DatColors.rust : DatColors.line, width: selected ? 1.5 : 1),
          ),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(a.$4, color: DatColors.ink, size: 22),
            const SizedBox(height: 8),
            Text(a.$3, style: const TextStyle(fontWeight: FontWeight.w700, color: DatColors.ink)),
            Text(a.$1, style: monoStyle.copyWith(fontSize: 11, color: DatColors.muted)),
          ]),
        ),
      ),
    );
  }

  Widget _clientHelp() => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(color: DatColors.infoBg, borderRadius: BorderRadius.circular(14)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Row(children: [
            Icon(Icons.info_outline, color: DatColors.infoInk, size: 18),
            SizedBox(width: 6),
            Text('Vous ne voyez que le véhicule de votre BL', style: TextStyle(color: DatColors.infoInk, fontWeight: FontWeight.w600)),
          ]),
          const SizedBox(height: 6),
          const Text('Le code SMS est envoyé au bénéficiaire ou au transitaire déclaré.',
              style: TextStyle(color: DatColors.infoInk, fontSize: 12)),
          const SizedBox(height: 8),
          GestureDetector(
            onTap: () => setState(() {
              _bl.text = 'DKR-88341';
              _code.text = '5821';
              _error = null;
            }),
            child: const Text('Démo : DKR-88341 · code 5821 (appuyer pour remplir)',
                style: TextStyle(color: DatColors.rust, fontSize: 12, fontWeight: FontWeight.w600)),
          ),
        ]),
      );
}

// ------------------------------------------------------------------ éléments

class _Hero extends StatelessWidget {
  const _Hero();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 24),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(color: DatColors.orange, borderRadius: BorderRadius.circular(11)),
            child: const Icon(Icons.directions_car_filled_outlined, color: Colors.white),
          ),
          const SizedBox(width: 12),
          const Text('Dakar AutoTrack', style: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w800)),
        ]),
        const SizedBox(height: 20),
        const Text('Le passeport numérique\ndes véhicules au terminal',
            style: TextStyle(color: Colors.white, fontSize: 22, height: 1.2, fontWeight: FontWeight.w800)),
        const SizedBox(height: 16),
        const _MiniPlan(),
      ]),
    );
  }
}

/// Petit plan de zone décoratif : places occupées, prêtes, libres et une place localisée.
class _MiniPlan extends StatelessWidget {
  const _MiniPlan();

  @override
  Widget build(BuildContext context) {
    Widget slot(int r, int c) {
      if (r == 1 && c == 9) {
        return Container(
          decoration: BoxDecoration(
            color: DatColors.orange,
            borderRadius: BorderRadius.circular(3),
            border: Border.all(color: Colors.white, width: 1.2),
          ),
        );
      }
      final n = (r * 7 + c * 5) % 11;
      if (n < 4) return Container(decoration: BoxDecoration(color: const Color(0xFF3A4E64), borderRadius: BorderRadius.circular(3)));
      if (n < 6) return Container(decoration: BoxDecoration(color: const Color(0xFF2E8B5E), borderRadius: BorderRadius.circular(3)));
      return Container(
        decoration: BoxDecoration(border: Border.all(color: const Color(0xFF4A627C)), borderRadius: BorderRadius.circular(3)),
      );
    }

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: DatColors.ink2,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: DatColors.inkLine),
      ),
      child: Column(children: [
        for (var r = 0; r < 3; r++)
          Padding(
            padding: EdgeInsets.only(bottom: r < 2 ? 6 : 0),
            child: Row(children: [
              for (var c = 0; c < 12; c++) ...[
                if (c > 0) const SizedBox(width: 4),
                Expanded(child: SizedBox(height: 20, child: slot(r, c))),
              ],
            ]),
          ),
        const SizedBox(height: 10),
        const Row(children: [
          Icon(Icons.place, color: DatColors.peach, size: 16),
          SizedBox(width: 4),
          Text('B · 3 · 18 — position confirmée par scan', style: TextStyle(color: DatColors.onInkMuted, fontSize: 12)),
        ]),
      ]),
    );
  }
}

class _Segmented extends StatelessWidget {
  const _Segmented({required this.client, required this.onChanged});

  final bool client;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    Widget seg(String label, IconData icon, bool value) {
      final on = client == value;
      return Expanded(
        child: GestureDetector(
          onTap: () => onChanged(value),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 160),
            height: 42,
            decoration: BoxDecoration(
              color: on ? Colors.white : Colors.transparent,
              borderRadius: BorderRadius.circular(10),
              boxShadow: on ? const [BoxShadow(color: Color(0x1A0F1D2B), blurRadius: 6, offset: Offset(0, 2))] : null,
            ),
            child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              Icon(icon, size: 18, color: on ? DatColors.ink : DatColors.muted),
              const SizedBox(width: 6),
              Text(label, style: TextStyle(fontWeight: FontWeight.w600, color: on ? DatColors.ink : DatColors.muted)),
            ]),
          ),
        ),
      );
    }

    return Semantics(
      label: 'Type de compte',
      child: Container(
        padding: const EdgeInsets.all(4),
        decoration: BoxDecoration(color: const Color(0xFFE6E1D6), borderRadius: BorderRadius.circular(13)),
        child: Row(children: [
          seg('Personnel', Icons.badge_outlined, false),
          seg('Client', Icons.person_pin_circle_outlined, true),
        ]),
      ),
    );
  }
}

class _Field extends StatelessWidget {
  const _Field({
    required this.controller,
    required this.label,
    required this.icon,
    this.hint,
    this.obscure = false,
    this.suffix,
    this.keyboard,
    this.action,
    this.onSubmitted,
    this.autofill,
    this.caps = TextCapitalization.none,
  });

  final TextEditingController controller;
  final String label;
  final String? hint;
  final IconData icon;
  final bool obscure;
  final Widget? suffix;
  final TextInputType? keyboard;
  final TextInputAction? action;
  final ValueChanged<String>? onSubmitted;
  final Iterable<String>? autofill;
  final TextCapitalization caps;

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      obscureText: obscure,
      keyboardType: keyboard,
      textInputAction: action,
      onSubmitted: onSubmitted,
      autofillHints: autofill,
      textCapitalization: caps,
      autocorrect: false,
      decoration: InputDecoration(
        labelText: label,
        hintText: hint,
        prefixIcon: Icon(icon, color: DatColors.muted),
        suffixIcon: suffix,
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: DatColors.line)),
        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: DatColors.line)),
        focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: DatColors.ink, width: 1.6)),
      ),
    );
  }
}
