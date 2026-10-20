import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/nav.dart';
import '../widgets/common.dart';
import 'place_screen.dart';

/// Étape 2 : état du véhicule à la réception (8 photos, réserves, carburant, clés).
class EtatScreen extends StatefulWidget {
  const EtatScreen({super.key, required this.vin});

  final String vin;

  @override
  State<EtatScreen> createState() => _EtatScreenState();
}

class _EtatScreenState extends State<EtatScreen> {
  static const views = ['Avant', 'Arrière', 'Gauche', 'Droite', 'Toit', 'Intérieur', 'Compteur', 'Plaque VIN'];
  final Map<String, Uint8List> _photos = {};
  final Map<String, String> _refs = {};
  final List<Map<String, String>> _reserves = [];
  final _km = TextEditingController();
  String _fuel = '1/4';
  int _keys = 2;
  bool _busy = false;

  Future<void> _shoot(String view) async {
    final shot = await ImagePicker().pickImage(source: ImageSource.camera, imageQuality: 75, maxWidth: 1600);
    if (shot == null) return;
    final bytes = await shot.readAsBytes();
    setState(() => _photos[view] = bytes);
    try {
      final name = shot.name.isEmpty ? 'photo.jpg' : shot.name;
      final res = await ApiClient.instance.uploadPhoto(widget.vin, view, bytes, name);
      _refs[view] = res['storageRef'] as String;
    } catch (e) {
      if (mounted) showError(context, 'Photo « $view » non envoyée : $e');
    }
  }

  Future<void> _addReserve() async {
    final loc = TextEditingController();
    final desc = TextEditingController();
    String photoView = 'Gauche';
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setD) => AlertDialog(
          title: const Text('Signaler un dommage'),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            TextField(controller: loc, decoration: const InputDecoration(labelText: 'Emplacement (ex. portière AR gauche)')),
            const SizedBox(height: 8),
            TextField(controller: desc, decoration: const InputDecoration(labelText: 'Description (ex. rayure superficielle)')),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: photoView,
              items: views.map((v) => DropdownMenuItem(value: v, child: Text('Photo « $v »'))).toList(),
              onChanged: (v) => setD(() => photoView = v ?? photoView),
            ),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Annuler')),
            TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Ajouter')),
          ],
        ),
      ),
    );
    if (ok == true && desc.text.trim().isNotEmpty) {
      setState(() => _reserves.add({'location': loc.text.trim(), 'description': desc.text.trim(), 'photoView': photoView}));
    }
  }

  Future<void> _save() async {
    setState(() => _busy = true);
    try {
      await ApiClient.instance.put('/api/vehicles/${widget.vin}/inspection', {
        'fuelLevel': _fuel,
        'keysCount': _keys,
        'mileage': int.tryParse(_km.text.trim()),
        'photos': _refs.entries.map((e) => {'view': e.key, 'storageRef': e.value}).toList(),
        'reserves': _reserves,
      });
      if (!mounted) return;
      Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => PlaceScreen(vin: widget.vin)));
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(leading: const DatBackButton(), title: const Text('Réception · 2/3 · État'), actions: const [DatHomeButton()]),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(widget.vin, style: monoStyle.copyWith(color: DatColors.muted)),
          const SizedBox(height: 12),
          Row(children: [
            const Text('Photos', style: TextStyle(fontWeight: FontWeight.w600)),
            const Spacer(),
            Text('${_photos.length} / ${views.length} prises', style: const TextStyle(color: DatColors.muted, fontSize: 12)),
          ]),
          const SizedBox(height: 8),
          GridView.count(
            crossAxisCount: 4,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            mainAxisSpacing: 8,
            crossAxisSpacing: 8,
            children: [
              for (final v in views)
                GestureDetector(
                  onTap: () => _shoot(v),
                  child: Container(
                    decoration: BoxDecoration(
                      color: const Color(0xFFD9D4C8),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(
                          color: _reserves.any((r) => r['photoView'] == v) ? DatColors.orange : Colors.transparent, width: 2),
                      image: _photos[v] != null ? DecorationImage(image: MemoryImage(_photos[v]!), fit: BoxFit.cover) : null,
                    ),
                    alignment: Alignment.bottomLeft,
                    padding: const EdgeInsets.all(4),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(4)),
                      child: Text(v, style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w600)),
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 16),
          DatCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                const Expanded(child: Text('Réserves', style: TextStyle(fontWeight: FontWeight.w600))),
                TextButton.icon(onPressed: _addReserve, icon: const Icon(Icons.add), label: const Text('Signaler un dommage')),
              ]),
              if (_reserves.isEmpty) const Text('Aucune réserve', style: TextStyle(color: DatColors.muted)),
              for (var i = 0; i < _reserves.length; i++)
                Container(
                  margin: const EdgeInsets.only(top: 6),
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(color: DatColors.warnBg, borderRadius: BorderRadius.circular(8)),
                  child: Text('Réserve ${i + 1} · ${_reserves[i]['description']}, ${_reserves[i]['location']} · photo « ${_reserves[i]['photoView']} »',
                      style: const TextStyle(color: DatColors.warnInk)),
                ),
            ]),
          ),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(
              child: DropdownButtonFormField<String>(
                value: _fuel,
                decoration: const InputDecoration(labelText: 'Carburant'),
                items: const ['Vide', '1/4', '1/2', '3/4', 'Plein'].map((f) => DropdownMenuItem(value: f, child: Text(f))).toList(),
                onChanged: (v) => setState(() => _fuel = v ?? _fuel),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: DropdownButtonFormField<int>(
                value: _keys,
                decoration: const InputDecoration(labelText: 'Clés remises'),
                items: const [0, 1, 2, 3].map((k) => DropdownMenuItem(value: k, child: Text('$k'))).toList(),
                onChanged: (v) => setState(() => _keys = v ?? _keys),
              ),
            ),
          ]),
          const SizedBox(height: 10),
          TextField(controller: _km, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Kilométrage')),
          const SizedBox(height: 20),
          FilledButton(onPressed: _busy ? null : _save, child: const Text('Enregistrer l\'état')),
        ],
      ),
    );
  }
}
