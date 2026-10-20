# Dakar AutoTrack — application mobile (Flutter)

Une seule application, trois profils selon le rôle porté par le JWT :

- **Agent de parc** : réception (lecture assistée du VIN, état + photos, place par scan QR), déplacement, inventaire en rafale, assistant terrain (voix / photo / texte).
- **Agent de portail** : scan du ticket client, contrôles automatiques + contrôles manuels, validation ou blocage de la sortie.
- **Client** : connexion BL + code SMS, suivi du véhicule, alerte SMS, ticket de retrait (QR signé).

## Installation

```bash
flutter create . --platforms=android,ios --project-name dakar_autotrack
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

### Android — `android/app/src/main/AndroidManifest.xml`
Dans `<manifest>` :
```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<queries>
  <intent><action android:name="android.speech.RecognitionService"/></intent>
</queries>
```
Dans `<application …>` (API en HTTP pour la démo uniquement) : `android:usesCleartextTraffic="true"`.
`android/app/build.gradle` : `minSdkVersion 23` (ML Kit, mobile_scanner, secure storage).

### iOS — `ios/Runner/Info.plist`
```xml
<key>NSCameraUsageDescription</key><string>Scanner les QR codes et photographier les VIN</string>
<key>NSMicrophoneUsageDescription</key><string>Poser une question à l'assistant à la voix</string>
<key>NSSpeechRecognitionUsageDescription</key><string>Transcrire les questions à l'assistant</string>
<key>NSPhotoLibraryUsageDescription</key><string>Joindre une photo du véhicule</string>
```
Plateforme minimale : iOS 15.5 (ML Kit). Pour une API en HTTP : `NSAppTransportSecurity` → `NSAllowsArbitraryLoads` (démo uniquement).

## Contenu des QR codes
- Place : `PL:B-3-18`
- Véhicule (étiquette pare-brise) : `VH:VF3LCYHZPJS218456`
- Clé du véhicule (porte-clé) : `KEY:VF3LCYHZPJS218456`
- Puce RFID de l'étiquette : EPC `E280-218456`
- Ticket de retrait : `TKT.<numéro>.<expiration>.<signature>`

Étiquettes prêtes à imprimer : `../docs/etiquettes-demo.pdf` (régénérables avec `../tools/generer_etiquettes.py`).
