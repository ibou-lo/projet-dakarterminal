import 'dart:convert';
import 'dart:io';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';

import '../config.dart';

/// Erreur renvoyée par l'API (message lisible en français).
class ApiError implements Exception {
  ApiError(this.status, this.message);
  final int status;
  final String message;

  @override
  String toString() => message;
}

/// Session courante (issue du JWT).
class Session {
  Session({required this.username, required this.fullName, required this.role, this.zone, this.bl, this.privileges, this.mustChangePassword = false});

  final String username;
  final String fullName;
  final String role; // SUPERVISEUR, AGENT, PORTAIL, CLIENT
  final String? zone;
  final String? bl;

  /// Privilèges du profil, configurés par l'administrateur (null : session ancienne, le serveur tranche).
  final List<String>? privileges;

  bool can(String privilege) => privileges == null || privileges!.contains(privilege);

  /// Mot de passe provisoire : à remplacer avant tout accès.
  final bool mustChangePassword;

  factory Session.fromJson(Map<String, dynamic> j) => Session(
        username: j['username'] as String,
        fullName: (j['fullName'] ?? '') as String,
        role: j['role'] as String,
        zone: j['zone'] as String?,
        bl: j['bl'] as String?,
        privileges: (j['privileges'] as List?)?.map((e) => e.toString()).toList(),
        mustChangePassword: j['mustChangePassword'] == true,
      );
}

/// Client HTTP de l'API Dakar AutoTrack avec gestion du JWT.
///
/// - jetons stockés dans le coffre sécurisé du téléphone (Keychain / Keystore) ;
/// - sur un 401, une tentative de rafraîchissement puis nouvel essai.
class ApiClient {
  ApiClient._();
  static final ApiClient instance = ApiClient._();

  static const _storage = FlutterSecureStorage();
  static const _kAccess = 'dat_access';
  static const _kRefresh = 'dat_refresh';

  String? _access;
  Session? session;

  Uri _uri(String path, [Map<String, String>? query]) =>
      Uri.parse('${AppConfig.apiBaseUrl}$path').replace(queryParameters: query);

  Future<Session> login(String username, String password) =>
      _authenticate('/api/auth/login', {'username': username, 'password': password});

  Future<Session> clientLogin(String bl, String code) =>
      _authenticate('/api/client/login', {'blNumber': bl, 'smsCode': code});

  static const _requestTimeout = Duration(seconds: 90);

  Future<Session> _authenticate(String path, Map<String, String> body) async {
    await _wakeServer();
    final res = await _postJson(path, body);
    final data = _decode(res) as Map<String, dynamic>;
    await _store(data);
    return session!;
  }

  /// L'instance Render Free s'endort : un GET préalable + quelques essais
  /// évitent un échec au premier appel depuis le téléphone.
  Future<void> _wakeServer() async {
    try {
      await http.get(_uri('/api/health')).timeout(_requestTimeout);
    } catch (_) {}
  }

  Future<http.Response> _postJson(String path, Map<String, String> body) async {
    Object? last;
    for (var attempt = 1; attempt <= 3; attempt++) {
      try {
        return await http
            .post(_uri(path), headers: _jsonHeaders(null), body: jsonEncode(body))
            .timeout(_requestTimeout);
      } catch (e) {
        last = e;
        if (attempt < 3) {
          await Future<void>.delayed(Duration(seconds: 4 * attempt));
        }
      }
    }
    throw last ?? Exception('Serveur injoignable (${AppConfig.apiBaseUrl})');
  }

  Future<void> _store(Map<String, dynamic> t) async {
    _access = t['accessToken'] as String;
    await _storage.write(key: _kAccess, value: _access);
    await _storage.write(key: _kRefresh, value: t['refreshToken'] as String);
    session = Session.fromJson(t['user'] as Map<String, dynamic>);
  }

  Future<bool> _refresh() async {
    final refresh = await _storage.read(key: _kRefresh);
    if (refresh == null) return false;
    final res = await http.post(_uri('/api/auth/refresh'),
        headers: _jsonHeaders(null), body: jsonEncode({'refreshToken': refresh}));
    if (res.statusCode != 200) return false;
    await _store(jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>);
    return true;
  }

  /// Déconnexion : le serveur invalide les jetons du compte (personnel), puis effacement local.
  Future<void> logout() async {
    final token = _access;
    if (token != null && session?.role != 'CLIENT') {
      try {
        await http.post(_uri('/api/auth/logout'), headers: _jsonHeaders(token)).timeout(const Duration(seconds: 5));
      } catch (_) {
        // hors ligne : les jetons expireront d'eux-mêmes
      }
    }
    _access = null;
    session = null;
    await _storage.delete(key: _kAccess);
    await _storage.delete(key: _kRefresh);
  }

  Map<String, String> _jsonHeaders(String? token) => {
        HttpHeaders.contentTypeHeader: 'application/json',
        HttpHeaders.acceptHeader: 'application/json',
        if (token != null) HttpHeaders.authorizationHeader: 'Bearer $token',
      };

  Future<dynamic> get(String path, [Map<String, String>? query]) =>
      _send(() => http.get(_uri(path, query), headers: _jsonHeaders(_access)));

  Future<dynamic> post(String path, [Object? body]) => _send(() => http.post(_uri(path),
      headers: _jsonHeaders(_access), body: body == null ? null : jsonEncode(body)));

  Future<dynamic> put(String path, Object body) =>
      _send(() => http.put(_uri(path), headers: _jsonHeaders(_access), body: jsonEncode(body)));

  /// Envoi d'une photo (multipart) : renvoie {view, storageRef}.
  Future<Map<String, dynamic>> uploadPhoto(String vin, String view, List<int> bytes, String filename) async {
    final png = filename.toLowerCase().endsWith('.png');
    Future<http.Response> send() async {
      final req = http.MultipartRequest('POST', _uri('/api/vehicles/$vin/photos'))
        ..headers[HttpHeaders.authorizationHeader] = 'Bearer $_access'
        ..fields['view'] = view
        ..files.add(http.MultipartFile.fromBytes('file', bytes,
            filename: filename, contentType: MediaType('image', png ? 'png' : 'jpeg')));
      return http.Response.fromStream(await req.send());
    }

    var res = await send();
    if (res.statusCode == 401 && await _refresh()) res = await send();
    return _decode(res) as Map<String, dynamic>;
  }

  Future<dynamic> _send(Future<http.Response> Function() call) async {
    var res = await call();
    if (res.statusCode == 401 && await _refresh()) {
      res = await call();
    }
    return _decode(res);
  }

  dynamic _decode(http.Response res) {
    final text = utf8.decode(res.bodyBytes);
    final body = text.isEmpty ? null : jsonDecode(text);
    if (res.statusCode >= 200 && res.statusCode < 300) return body;
    final msg = body is Map && body['message'] != null ? body['message'] as String : 'Erreur ${res.statusCode}';
    throw ApiError(res.statusCode, msg);
  }
}

/// Libellé du profil connecté.
String roleLabel(String? role) => switch (role) {
      'ADMIN' => 'Administrateur',
      'SUPERVISEUR' => 'Superviseur parc',
      'AGENT' => 'Agent de parc',
      'PORTAIL' => 'Agent de portail',
      'CLIENT' => 'Client',
      _ => '',
    };
