import Flutter
import UIKit

public class MigratetoolPlugin: NSObject, FlutterPlugin {
  private static keyRefreshToken: String = "refresh_token"
  private static keyAnonymousId: String = "anonymousId"
  private static keyBiometricKeyId: String = "biometricKeyId"
  private static partialKeys: String[] = [keyRefreshToken, keyAnonymousId, keyBiometricKeyId];
  class KeyMaker {
    func scopedKey(key: String) -> String {
      return "authgear_\(key)"
    }
    func keyRefreshToken(namespace: String) -> String {
      return scopedKey("\(namespace)_refreshToken")
    }
    func keyAnonymousKeyId(namespace: String) -> String {
      return scopedKey("\(namespace)_anonymousKeyID")
    }
    func keyBiometricKeyId(namespace: String) -> String {
      return scopedKey("\(namespace)_biometricKeyID")
    }
  }
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "migratetool", binaryMessenger: registrar.messenger())
    let instance = MigratetoolPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "hasFlutterData":
      result(hasFlutterData())
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  private func hasFlutterData(containerName: String) -> Bool {
    let keyMaker = KeyMaker()
    let hasRefreshToken = storageGetItem(keyMaker.keyRefreshToken(containerName)) != nil
    let hasAnonymousKeyId = storageGetItem(keyMaker.keyAnonymousKeyId(containerName)) != nil
    let hasBiometricKeyId = storageGetItem(keyMaker.keyBiometricKeyId(containerName)) != nil
    return hasRefreshToken || hasAnonymousKeyId || hasBiometricKeyId
  }

  private func xamarinFullKey(containerName: String, subKey: String) -> String {
    return "\(containerName)_\(subKey)"
  }

  private func xamarinEssentialAlias(packageName: String) -> String {
    return "\(packageName)_xamarinessentials"
  }

  private func hasXamarinData(packageName: String, containerName: String) -> Bool {
    let alias = xamarinEssentialAlias(packageName)
    for subKey in partialKeys {
      let fullKey = xamarinFullKey(containerName, subKey)
      let value = storageGetItem(fullKey, alias)
      if value != nil {
        return true
      }
    }
    return false
  }

  private func migrate(packageName: String, containerName: String) -> Bool {
    let alias = xamarinEssentialAlias(packageName)
    let refreshToken = storageGetItem(xamarinFullKey(containerName, keyRefreshToken))
    let anonymousId = storageGetItem(xamarinFullKey(containerName, keyAnonymousId))
    let biometricKeyId = storageGetItem(xamarinFullKey(containerName, keyBiometricKeyId))
    let keyMaker = KeyMaker()
    if refreshToken != nil {
      storageSetItem(keyMaker.keyRefreshToken(containerName), refreshToken)
    }
    if anonymousId != nil {
      storageSetItem(keyMaker.keyAnonymousKeyId(containerName), anonymousId)
    }
    if biometricKeyId != nil {
      storageSetItem(keyMaker.keyBiometricKeyId(containerName), biometricKeyId)
    }
    return false
  }

  // Direct copy of https://github.com/authgear/authgear-sdk-flutter/blob/main/ios/Classes/SwiftAuthgearPlugin.swift#L389
  private func storageSetItem(key: String, value: String, result: FlutterResult) {
    let data = value.data(using: .utf8)!
    let updateQuery: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: key,
    ]
    let update: [String: Any] = [
      kSecValueData as String: data,
    ]

    let updateStatus = SecItemUpdate(updateQuery as CFDictionary, update as CFDictionary)
    switch updateStatus {
    case errSecSuccess:
      result(nil)
    default:
      let addQuery: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrAccount as String: key,
        kSecValueData as String: data,
      ]

      let addStatus = SecItemAdd(addQuery as CFDictionary, nil)
      switch addStatus {
      case errSecSuccess:
        result(nil)
      default:
        result(FlutterError(status: addStatus))
      }
    }
  }


  // Direct copy of https://github.com/authgear/authgear-sdk-flutter/blob/main/ios/Classes/SwiftAuthgearPlugin.swift#L420C3-L442C4
  private func storageGetItem(key: String, service: String? = nil) -> String? {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: key,
      kSecMatchLimit as String: kSecMatchLimitOne,
      kSecReturnData as String: true,
    ]
    if service != nil {
      query[kSecAttrService as String] = service
    }

    var item: CFTypeRef?
    let status = withUnsafeMutablePointer(to: &item) {
      SecItemCopyMatching(query as CFDictionary, $0)
    }

    switch status {
    case errSecSuccess:
      let value = String(data: item as! Data, encoding: .utf8)
      return value
    case errSecItemNotFound:
      return nil
    default:
      return nil
    }
  }
}
