import 'package:flutter_test/flutter_test.dart';
import 'package:migratetool/migratetool.dart';
import 'package:migratetool/migratetool_platform_interface.dart';
import 'package:migratetool/migratetool_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockMigratetoolPlatform
    with MockPlatformInterfaceMixin
    implements MigratetoolPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final MigratetoolPlatform initialPlatform = MigratetoolPlatform.instance;

  test('$MethodChannelMigratetool is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelMigratetool>());
  });

  test('getPlatformVersion', () async {
    Migratetool migratetoolPlugin = Migratetool();
    MockMigratetoolPlatform fakePlatform = MockMigratetoolPlatform();
    MigratetoolPlatform.instance = fakePlatform;

    expect(await migratetoolPlugin.getPlatformVersion(), '42');
  });
}
