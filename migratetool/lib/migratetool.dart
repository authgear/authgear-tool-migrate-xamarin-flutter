library migratetool;

import 'package:flutter/services.dart';

import 'exception.dart';

const _channel = MethodChannel("authgear_sdk_tool_migrate_xamarin_flutter");

Future<bool> migrate(String packageName) async {
  if (await _hasXamarinData(packageName)) {
    _migrate(packageName);
    return true;
  }
  return false;
}

Future<bool> _hasXamarinData(String packageName) async {
  try {
    return await _channel.invokeMethod("hasXamarinData", packageName);
  } on PlatformException catch (e) {
    throw AuthgearException(e);
  }
}

Future<bool> _migrate(String packageName) async {
  print("migrate");
  try {
    return await _channel.invokeMethod("migrate", packageName);
  } on PlatformException catch (e) {
    throw AuthgearException(e);
  }
}
