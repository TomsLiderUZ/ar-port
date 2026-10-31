import 'package:shared_preferences/shared_preferences.dart';

/// Persists user hardware selections and restores them across app launches.
class LauncherPreferences {
  static const _keyViewMode = 'launcher_view_mode';
  static const _keyCameraId = 'launcher_camera_id';
  static const _keyResolution = 'launcher_resolution';
  static const _keyTargetFps = 'launcher_target_fps';
  static const _keyIpdMm = 'launcher_ipd_mm';
  static const _keyLensProfile = 'launcher_lens_profile';
  static const _keyAudioInputId = 'launcher_audio_input_id';
  static const _keyAudioOutputId = 'launcher_audio_output_id';

  final SharedPreferences? _prefs;

  LauncherPreferences([this._prefs]);

  static Future<LauncherPreferences> getInstance() async {
    final prefs = await SharedPreferences.getInstance();
    return LauncherPreferences(prefs);
  }

  int getViewMode() => _prefs?.getInt(_keyViewMode) ?? 0;
  Future<void> setViewMode(int mode) async => await _prefs?.setInt(_keyViewMode, mode);

  String getCameraId() => _prefs?.getString(_keyCameraId) ?? '0';
  Future<void> setCameraId(String id) async => await _prefs?.setString(_keyCameraId, id);

  String getResolution() => _prefs?.getString(_keyResolution) ?? '1080p';
  Future<void> setResolution(String res) async => await _prefs?.setString(_keyResolution, res);

  int getTargetFps() => _prefs?.getInt(_keyTargetFps) ?? 60;
  Future<void> setTargetFps(int fps) async => await _prefs?.setInt(_keyTargetFps, fps);

  double getIpdMm() => _prefs?.getDouble(_keyIpdMm) ?? 63.0;
  Future<void> setIpdMm(double ipd) async => await _prefs?.setDouble(_keyIpdMm, ipd);

  String getLensProfile() => _prefs?.getString(_keyLensProfile) ?? 'cardboard_v2';
  Future<void> setLensProfile(String profile) async => await _prefs?.setString(_keyLensProfile, profile);

  int? getAudioInputId() => _prefs?.getInt(_keyAudioInputId);
  Future<void> setAudioInputId(int id) async => await _prefs?.setInt(_keyAudioInputId, id);

  int? getAudioOutputId() => _prefs?.getInt(_keyAudioOutputId);
  Future<void> setAudioOutputId(int id) async => await _prefs?.setInt(_keyAudioOutputId, id);
}

