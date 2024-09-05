library migratetool;

import 'package:flutter/services.dart';

import 'exception.dart';

const _channel = MethodChannel("authgear_sdk_tool_migrate_xamarin_flutter");

enum MigrateResult {
  /// No xamarin data available to migrate, so no migration needed
  noMigrationNeeded,

  /// Migration already done previously, so would not migrate
  wouldNotMigrate,

  /// Migrated this time
  migrated
}

Future<MigrateResult> migrate(String packageName,
    [String containerName = "default"]) async {
  if (await _hasFlutterData(packageName, containerName)) {
    return MigrateResult.wouldNotMigrate;
  }
  if (await _hasXamarinData(packageName, containerName)) {
    _migrate(packageName, containerName);
    return MigrateResult.migrated;
  }
  return MigrateResult.noMigrationNeeded;
}

Future<bool> _hasFlutterData(String packageName, String containerName) async {
  try {
    return await _channel.invokeMethod("hasFlutterData",
        {"packageName": packageName, "containerName": containerName});
  } on PlatformException catch (e) {
    throw AuthgearException(e);
  }
}

Future<bool> _hasXamarinData(String packageName, String containerName) async {
  try {
    return await _channel.invokeMethod("hasXamarinData",
        {"packageName": packageName, "containerName": containerName});
  } on PlatformException catch (e) {
    throw AuthgearException(e);
  }
}

Future<bool> _migrate(String packageName, String containerName) async {
  try {
    return await _channel.invokeMethod("migrate",
        {"packageName": packageName, "containerName": containerName});
  } on PlatformException catch (e) {
    throw AuthgearException(e);
  }
}
