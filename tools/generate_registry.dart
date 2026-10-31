// ignore_for_file: avoid_print

import 'dart:convert';
import 'dart:io';

/// Script that scans apps/*/manifest.json, validates schemas,
/// and outputs apps/app_registry.g.json and assets/registry/app_registry.json.
void main() {
  print('========================================');
  print('AR Port: App Registry Generator & Validator');
  print('========================================');

  final appsDir = Directory('apps');
  if (!appsDir.existsSync()) {
    stderr.writeln('Error: apps/ directory does not exist.');
    exit(1);
  }

  final List<Map<String, dynamic>> validManifests = [];
  final manifestFiles = appsDir
      .listSync(recursive: true)
      .where((f) => f is File && f.path.endsWith('manifest.json'))
      .cast<File>()
      .toList();

  print('Found ${manifestFiles.length} manifest files.');

  final requiredFields = [
    'id',
    'name',
    'version',
    'type',
    'category',
    'entry',
    'defaultAspect',
    'defaultSize',
    'order',
  ];

  final validTypes = ['web', 'native', 'game2d', 'game3d', 'game'];
  final validCategories = ['media', 'social', 'tools', 'games', 'system'];

  for (final file in manifestFiles) {
    if (file.path.contains('_template')) continue;

    try {
      final content = file.readAsStringSync();
      final data = jsonDecode(content) as Map<String, dynamic>;

      // Schema validations
      for (final field in requiredFields) {
        if (!data.containsKey(field) || data[field] == null) {
          throw Exception("Missing required field '$field'");
        }
      }

      final id = data['id'] as String;
      final type = data['type'] as String;
      final category = data['category'] as String;

      if (!validTypes.contains(type)) {
        throw Exception("Invalid type '$type'. Must be one of: $validTypes");
      }

      if (!validCategories.contains(category)) {
        throw Exception("Invalid category '$category'. Must be one of: $validCategories");
      }

      validManifests.add(data);
      print('  ✓ [${data['category']}] $id (${data['name']['uz'] ?? data['name']['en']})');
    } catch (e) {
      stderr.writeln('Error validating ${file.path}: $e');
      exit(1);
    }
  }

  // Sort by order
  validManifests.sort((a, b) => (a['order'] as int).compareTo(b['order'] as int));

  final outputJson = JsonEncoder.withIndent('  ').convert({
    'version': '1.0.0',
    'generated_at': DateTime.now().toUtc().toIso8601String(),
    'apps_count': validManifests.length,
    'apps': validManifests,
  });

  // 1. Output apps/app_registry.g.json
  final appRegistryFile = File('apps/app_registry.g.json');
  appRegistryFile.writeAsStringSync(outputJson);
  print('Generated ${appRegistryFile.path}');

  // 2. Output android/arcore-engine/src/main/assets/registry/app_registry.json
  final androidAssetFile = File('android/arcore-engine/src/main/assets/registry/app_registry.json');
  androidAssetFile.parent.createSync(recursive: true);
  androidAssetFile.writeAsStringSync(outputJson);
  print('Copied to ${androidAssetFile.path}');

  print('Successfully processed ${validManifests.length} apps.');
}

