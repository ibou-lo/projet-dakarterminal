import 'package:dakar_autotrack/main.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets("L'écran de connexion s'affiche", (tester) async {
    await tester.pumpWidget(const DakarAutoTrackApp());
    expect(find.text('Dakar AutoTrack'), findsOneWidget);
    expect(find.text('Se connecter'), findsOneWidget);
  });
}
