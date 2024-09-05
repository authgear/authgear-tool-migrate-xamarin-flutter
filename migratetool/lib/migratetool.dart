library migratetool;

import 'package:flutter/services.dart';

import 'exception.dart';

const _channel = MethodChannel("authgear_sdk_tool_migrate_xamarin_flutter");

Future<bool> migrate(String packageName,
    [String containerName = "default"]) async {
  if (await _hasXamarinData(packageName, containerName)) {
    _migrate(packageName, containerName);
    return true;
  }
  return false;
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
