import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:image_picker/image_picker.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/nav.dart';
import '../widgets/common.dart';
import 'etat_screen.dart';

/// Étape 1 : lecture assistée du VIN à partir d'une photo, confirmée par l'agent.
class ReceptionVinScreen extends StatefulWidget {
  const ReceptionVinScreen({super.key});

  @override
  State<ReceptionVinScreen> createState() => _ReceptionVinScreenState();
}

class _ReceptionVinScreenState extends State<ReceptionVinScreen> {
  final _vin = TextEditingController();
  final _model = TextEditingController();
  final _color = TextEditingController();
  final _bl = TextEditingController();
  final _escale = TextEditingController(text: '2026-114');
  Uint8List? _photo;
  Map<String, dynamic>? _check;
  bool _busy = false;

  List<int> get _uncertain => ((_check?['uncertainPositions'] as List?) ?? const []).cast<int>();

  Future<void> _takePhoto() async {
    final shot = await ImagePicker().pickImage(source: ImageSource.camera, imageQuality: 85, maxWidth: 2000);
    if (shot == null) return;
    final bytes = await shot.readAsBytes();
    setState(() {
      _photo = bytes;
      _busy = true;
    });
    final recognizer = TextRecognizer(script: TextRecognitionScript.latin);
    try {
      final result = await recognizer.processImage(InputImage.fromFilePath(shot.path));
      // On envoie tout le texte lu : le serveur extrait la séquence de 17 caractères.
      await _verify(result.text);
    } catch (e) {
      if (mounted) showError(context, 'Lecture impossible : saisissez le VIN à la main');
    } finally {
      await recognizer.close();
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _verify(String raw) async {
    final check = await ApiClient.instance.post('/api/vehicles/vin-check', {'rawText': raw}) as Map<String, dynamic>;
    setState(() {
      _check = check;
      _vin.text = check['vin'] as String;
    });
  }

  Future<void> _confirm() async {
    final vin = _vin.text.trim().toUpperCase();
    setState(() => _busy = true);
    try {
      if (_check?['alreadyKnown'] == true) {
        if (!mounted) return;
        Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => EtatScreen(vin: vin)));
        return;
      }
      await ApiClient.instance.post('/api/vehicles', {
        'vin': vin,
        'model': _model.text.trim().isEmpty ? null : _model.text.trim(),
        'color': _color.text.trim().isEmpty ? null : _color.text.trim(),
        'escale': _escale.text.trim(),
        'blNumber': _bl.text.trim().isEmpty ? null : _bl.text.trim().toUpperCase(),
      });
      if (!mounted) return;
      Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => EtatScreen(vin: vin)));
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final vin = _vin.text;
    final messages = ((_check?['messages'] as List?) ?? const []).cast<String>();
    return Scaffold(
      appBar: AppBar(leading: const DatBackButton(), title: const Text('Réception · 1/3 · Lecture du VIN')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          GestureDetector(
            onTap: _busy ? null : _takePhoto,
            child: Container(
              height: 220,
              decoration: BoxDecoration(color: const Color(0xFF243447), borderRadius: BorderRadius.circular(16)),
              clipBehavior: Clip.antiAlias,
              child: _photo != null
                  ? Image.memory(_photo!, fit: BoxFit.cover)
                  : const Center(
                      child: Column(mainAxisSize: MainAxisSize.min, children: [
                        Icon(Icons.photo_camera_outlined, color: Colors.white, size: 40),
                        SizedBox(height: 8),
                        Text('Photographier la plaque VIN ou l\'étiquette de portière',
                            style: TextStyle(color: Color(0xFFD5DCE4))),
                      ]),
                    ),
            ),
          ),
          const SizedBox(height: 16),
          DatCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Row(children: [
                Icon(Icons.auto_awesome, color: DatColors.rust, size: 18),
                SizedBox(width: 6),
                Text('Lecture assistée · à confirmer par l\'agent', style: TextStyle(color: DatColors.rust, fontWeight: FontWeight.w600)),
              ]),
              const SizedBox(height: 12),
              if (vin.isNotEmpty)
                Wrap(
                  spacing: 4,
                  runSpacing: 4,
                  children: List.generate(vin.length, (i) {
                    final unsure = _uncertain.contains(i + 1);
                    return Container(
                      width: 30,
                      height: 36,
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        color: unsure ? DatColors.warnBg : DatColors.ground,
                        border: Border.all(color: unsure ? const Color(0xFFD98E1F) : DatColors.ground, width: 1.5),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(vin[i], style: monoStyle.copyWith(fontSize: 17)),
                    );
                  }),
                ),
              const SizedBox(height: 12),
              TextField(
                controller: _vin,
                textCapitalization: TextCapitalization.characters,
                maxLength: 17,
                style: monoStyle,
                decoration: const InputDecoration(labelText: 'VIN (corriger si besoin)'),
                onChanged: (_) => setState(() {}),
                onSubmitted: (v) => _verify(v),
              ),
              for (final m in messages)
                Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Text(m, style: const TextStyle(fontSize: 13, color: DatColors.warnInk)),
                ),
            ]),
          ),
          const SizedBox(height: 12),
          ExpansionTile(
            title: const Text('Informations complémentaires (facultatif)'),
            children: [
              TextField(controller: _model, decoration: const InputDecoration(labelText: 'Modèle')),
              const SizedBox(height: 8),
              TextField(controller: _color, decoration: const InputDecoration(labelText: 'Couleur')),
              const SizedBox(height: 8),
              TextField(controller: _bl, decoration: const InputDecoration(labelText: 'N° de BL')),
              const SizedBox(height: 8),
              TextField(controller: _escale, decoration: const InputDecoration(labelText: 'Escale')),
              const SizedBox(height: 8),
            ],
          ),
          const SizedBox(height: 16),
          Row(children: [
            OutlinedButton(onPressed: _busy ? null : () => _verify(_vin.text), child: const Text('Vérifier')),
            const SizedBox(width: 10),
            Expanded(
              child: FilledButton(
                onPressed: _busy || _vin.text.trim().length != 17 ? null : _confirm,
                child: Text(_check?['alreadyKnown'] == true ? 'Ouvrir le passeport' : 'Confirmer le VIN'),
              ),
            ),
          ]),
        ],
      ),
    );
  }
}
