import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../theme.dart';
import '../widgets/nav.dart';

/// Scanner QR générique : renvoie le premier contenu lu (ou une saisie manuelle).
class ScannerScreen extends StatefulWidget {
  const ScannerScreen({super.key, required this.title, this.hint});

  final String title;
  final String? hint;

  @override
  State<ScannerScreen> createState() => _ScannerScreenState();
}

class _ScannerScreenState extends State<ScannerScreen> {
  final _controller = MobileScannerController(detectionSpeed: DetectionSpeed.noDuplicates);
  bool _done = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _finish(String value) {
    if (_done) return;
    _done = true;
    Navigator.of(context).pop(value.trim());
  }

  Future<void> _manual() async {
    final ctrl = TextEditingController();
    final v = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Saisie manuelle'),
        content: TextField(controller: ctrl, autofocus: true, decoration: InputDecoration(hintText: widget.hint ?? 'Code')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Annuler')),
          TextButton(onPressed: () => Navigator.pop(ctx, ctrl.text), child: const Text('Valider')),
        ],
      ),
    );
    if (v != null && v.trim().isNotEmpty) _finish(v);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: const DatBackButton(),
        title: Text(widget.title),
        actions: [
          IconButton(tooltip: 'Lampe', icon: const Icon(Icons.flashlight_on_outlined), onPressed: () => _controller.toggleTorch()),
        ],
      ),
      backgroundColor: DatColors.ink,
      body: Column(children: [
        Expanded(
          child: Stack(children: [
            MobileScanner(
              controller: _controller,
              onDetect: (capture) {
                for (final b in capture.barcodes) {
                  final raw = b.rawValue;
                  if (raw != null && raw.isNotEmpty) {
                    _finish(raw);
                    break;
                  }
                }
              },
            ),
            Center(
              child: Container(
                width: 240,
                height: 240,
                decoration: BoxDecoration(
                  border: Border.all(color: DatColors.peach, width: 3),
                  borderRadius: BorderRadius.circular(16),
                ),
              ),
            ),
          ]),
        ),
        Padding(
          padding: const EdgeInsets.all(16),
          child: OutlinedButton.icon(
            style: OutlinedButton.styleFrom(foregroundColor: Colors.white, side: const BorderSide(color: DatColors.inkLine)),
            onPressed: _manual,
            icon: const Icon(Icons.keyboard),
            label: const Text('Saisir le code à la main'),
          ),
        ),
      ]),
    );
  }
}
