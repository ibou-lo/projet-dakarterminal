import 'package:dakar_autotrack/screens/portail_screen.dart';
import 'package:dakar_autotrack/widgets/common.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Contrôle de sortie : interprétation du code scanné', () {
    test('QR du ticket signé', () {
      expect(gateRequestFor('TKT.TR-2026-004712.1790000000.abc'), {'ticketQr': 'TKT.TR-2026-004712.1790000000.abc'});
    });
    test('numéro de ticket tapé à la main', () {
      expect(gateRequestFor(' tr-2026-004712 '), {'ticketQr': 'TR-2026-004712'});
    });
    test('étiquette véhicule VH:', () {
      expect(gateRequestFor('VH:VF3LCYHZPJS218456'), {'vin': 'VF3LCYHZPJS218456'});
    });
    test('étiquette RFID', () {
      expect(gateRequestFor('e280-js218456'), {'rfidEpc': 'E280-JS218456'});
    });
    test('QR du porte-clé KEY:', () {
      expect(gateRequestFor('KEY:VF3LCYHZPJS218456'), {'vin': 'VF3LCYHZPJS218456'});
    });
    test('QR avec espaces ou retour à la ligne (lecteur)', () {
      expect(gateRequestFor(' vh:vf3lcyhzpjs218456\n'), {'vin': 'VF3LCYHZPJS218456'});
    });
    test('code court et VIN', () {
      expect(gateRequestFor('DAT-218456'), {'vin': 'DAT-218456'});
      expect(gateRequestFor('vf3lcyhzpjs218456'), {'vin': 'VF3LCYHZPJS218456'});
    });
  });

  group('Type de QR scanné', () {
    test('place, véhicule, clé, ticket, puce', () {
      expect(scanKindOf('PL:B-3-18'), ScanKind.place);
      expect(scanKindOf('pl:b-3-18'), ScanKind.place);
      expect(scanKindOf('VH:VF3LCYHZPJS218456'), ScanKind.vehicle);
      expect(scanKindOf('KEY:VF3LCYHZPJS218456'), ScanKind.key);
      expect(scanKindOf('TKT.TR-2026-004712.1790000000.abc'), ScanKind.ticket);
      expect(scanKindOf('E280-218456'), ScanKind.epc);
      expect(scanKindOf('DAT-218456'), ScanKind.other);
    });
    test('le ticket signé garde sa signature (casse)', () {
      expect(normalizeScan(' TKT.TR-1.179.aBc_Xy '), 'TKT.TR-1.179.aBc_Xy');
    });
  });
}
