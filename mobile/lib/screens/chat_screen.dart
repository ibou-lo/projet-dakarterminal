import 'package:flutter/material.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:image_picker/image_picker.dart';
import 'package:speech_to_text/speech_to_text.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/nav.dart';
import '../widgets/common.dart';
import 'place_screen.dart';

class _Msg {
  _Msg.user(this.text) : reply = null, mine = true;
  _Msg.ai(Map<String, dynamic> r)
      : reply = r,
        text = r['text'] as String,
        mine = false;

  final String text;
  final Map<String, dynamic>? reply;
  final bool mine;
}

/// Assistant terrain : question à la voix, par écrit ou avec une photo de VIN.
/// Les actions proposées ne sont exécutées qu'après validation de l'agent.
class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final _input = TextEditingController();
  final _scroll = ScrollController();
  final _speech = SpeechToText();
  final List<_Msg> _msgs = [];
  final Map<int, String> _actionStatus = {};
  List<String> _suggestions = ['Place libre ?', 'Mouvements sans scan aujourd\'hui', 'Signaler un dommage'];
  bool _listening = false;
  bool _busy = false;
  String? _contextVin;

  Future<void> _send(String text) async {
    final q = text.trim();
    if (q.isEmpty) return;
    _input.clear();
    setState(() {
      _msgs.add(_Msg.user(q));
      _busy = true;
    });
    try {
      final r = await ApiClient.instance.post('/api/assistant/chat', {'message': q, 'contextVin': _contextVin})
          as Map<String, dynamic>;
      final v = r['vehicle'] as Map<String, dynamic>?;
      setState(() {
        _msgs.add(_Msg.ai(r));
        if (v != null) _contextVin = v['vin'] as String;
        final s = (r['suggestions'] as List?)?.cast<String>();
        if (s != null && s.isNotEmpty) _suggestions = s;
      });
      await Future<void>.delayed(const Duration(milliseconds: 50));
      if (_scroll.hasClients) _scroll.animateTo(_scroll.position.maxScrollExtent, duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _toggleVoice() async {
    if (_listening) {
      await _speech.stop();
      setState(() => _listening = false);
      return;
    }
    final ok = await _speech.initialize();
    if (!ok) {
      if (mounted) showError(context, 'Micro indisponible');
      return;
    }
    setState(() => _listening = true);
    await _speech.listen(
      localeId: 'fr_FR',
      onResult: (r) {
        _input.text = r.recognizedWords;
        if (r.finalResult) {
          setState(() => _listening = false);
          _send(r.recognizedWords);
        }
      },
    );
  }

  Future<void> _photo() async {
    final shot = await ImagePicker().pickImage(source: ImageSource.camera, imageQuality: 85, maxWidth: 2000);
    if (shot == null) return;
    final recognizer = TextRecognizer(script: TextRecognitionScript.latin);
    try {
      final text = (await recognizer.processImage(InputImage.fromFilePath(shot.path))).text;
      final check = await ApiClient.instance.post('/api/vehicles/vin-check', {'rawText': text}) as Map<String, dynamic>;
      final vin = check['vin'] as String;
      if (check['validFormat'] != true) {
        if (mounted) showError(context, 'VIN illisible : reprenez la photo ou saisissez-le');
        return;
      }
      _contextVin = vin;
      await _send('C\'est quel véhicule ? VIN lu : $vin');
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      await recognizer.close();
    }
  }

  Future<void> _decide(Map<String, dynamic> action, bool accept) async {
    try {
      final r = await ApiClient.instance.post('/api/assistant/actions/${action['id']}', {'accept': accept}) as Map<String, dynamic>;
      setState(() => _actionStatus[action['id'] as int] = r['status'] as String);
    } catch (e) {
      if (mounted) showError(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        // Onglet de l'accueil : pas de retour ; ouvert par-dessus un écran : bouton Retour
        leading: Navigator.of(context).canPop() ? const DatBackButton() : null,
        automaticallyImplyLeading: false,
        title: const Text('Assistant terrain'),
      ),
      body: Column(children: [
        Expanded(
          child: ListView.builder(
            controller: _scroll,
            padding: const EdgeInsets.all(16),
            itemCount: _msgs.length,
            itemBuilder: (_, i) => _bubble(_msgs[i]),
          ),
        ),
        Container(
          color: Colors.white,
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 20),
          child: Column(children: [
            SizedBox(
              height: 36,
              child: ListView(scrollDirection: Axis.horizontal, children: [
                for (final s in _suggestions)
                  Padding(
                    padding: const EdgeInsets.only(right: 6),
                    child: ActionChip(label: Text(s), onPressed: _busy ? null : () => _send(s)),
                  ),
              ]),
            ),
            const SizedBox(height: 10),
            Row(children: [
              IconButton.outlined(tooltip: 'Photo du VIN', onPressed: _busy ? null : _photo, icon: const Icon(Icons.photo_camera_outlined)),
              const SizedBox(width: 8),
              Expanded(
                child: TextField(
                  controller: _input,
                  textInputAction: TextInputAction.send,
                  onSubmitted: _send,
                  decoration: const InputDecoration(hintText: 'Écrire…'),
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filled(
                tooltip: 'Maintenir pour parler',
                style: IconButton.styleFrom(backgroundColor: _listening ? DatColors.danger : DatColors.orange, minimumSize: const Size(56, 56)),
                onPressed: _toggleVoice,
                icon: Icon(_listening ? Icons.stop : Icons.mic),
              ),
            ]),
          ]),
        ),
      ]),
    );
  }

  Widget _bubble(_Msg m) {
    if (m.mine) {
      return Align(
        alignment: Alignment.centerRight,
        child: Container(
          margin: const EdgeInsets.only(bottom: 12, left: 60),
          padding: const EdgeInsets.all(12),
          decoration: const BoxDecoration(
            color: DatColors.ink,
            borderRadius: BorderRadius.only(topLeft: Radius.circular(14), topRight: Radius.circular(14), bottomLeft: Radius.circular(14), bottomRight: Radius.circular(4)),
          ),
          child: Text(m.text, style: const TextStyle(color: Colors.white)),
        ),
      );
    }
    final r = m.reply!;
    final v = r['vehicle'] as Map<String, dynamic>?;
    final action = r['action'] as Map<String, dynamic>?;
    final table = r['table'] as Map<String, dynamic>?;
    final status = action == null ? null : (_actionStatus[action['id'] as int] ?? action['status'] as String);
    return Container(
      margin: const EdgeInsets.only(bottom: 12, right: 30),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: DatColors.line),
        borderRadius: const BorderRadius.only(topLeft: Radius.circular(14), topRight: Radius.circular(14), bottomRight: Radius.circular(14), bottomLeft: Radius.circular(4)),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(m.text, style: const TextStyle(height: 1.45)),
        if (v != null) ...[
          const SizedBox(height: 10),
          PlaceBoxes(placeCode: v['placeCode'] as String?),
          if (v['placeCode'] == null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: FilledButton(
                onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PlaceScreen(vin: v['vin'] as String))),
                child: const Text('Scanner la place pour confirmer'),
              ),
            ),
        ],
        if (table != null) ...[
          const SizedBox(height: 10),
          for (final row in (table['rows'] as List).cast<List>())
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Text(row.join('  ·  '), style: const TextStyle(fontSize: 13)),
            ),
        ],
        if (action != null) ...[
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: DatColors.warnBg, borderRadius: BorderRadius.circular(8)),
            child: Text(action['description'] as String, style: const TextStyle(color: DatColors.warnInk)),
          ),
          const SizedBox(height: 8),
          if (status == 'PROPOSED')
            Row(children: [
              Expanded(child: FilledButton(onPressed: () => _decide(action, true), child: const Text('Confirmer'))),
              const SizedBox(width: 8),
              OutlinedButton(onPressed: () => _decide(action, false), child: const Text('Annuler')),
            ])
          else
            StatusBanner(kind: status == 'CONFIRMED' ? 'ok' : 'info', title: status == 'CONFIRMED' ? 'Action validée · journalisée' : 'Action annulée'),
        ],
        if ((r['sources'] as List?)?.isNotEmpty ?? false)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text('Sources : ${(r['sources'] as List).join(', ')}', style: const TextStyle(fontSize: 11, color: DatColors.muted)),
          ),
      ]),
    );
  }
}
