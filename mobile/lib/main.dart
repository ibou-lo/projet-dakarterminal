import 'package:flutter/material.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'screens/login_screen.dart';
import 'theme.dart';
import 'widgets/phone_frame.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('fr_FR');
  runApp(const DakarAutoTrackApp());
}

class DakarAutoTrackApp extends StatelessWidget {
  const DakarAutoTrackApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Dakar AutoTrack',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      // Format téléphone conservé sur grand écran (navigateur, Windows).
      builder: (context, child) => PhoneFrame(child: child ?? const SizedBox.shrink()),
      home: const LoginScreen(),
    );
  }
}
