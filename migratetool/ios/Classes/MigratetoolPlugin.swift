import Flutter
import UIKit

public class MigratetoolPlugin: NSObject, FlutterPlugin {
  private static let keyRefreshToken: String = "refresh_token"
  private static let keyAnonymousId: String = "anonymousId"
  private static let keyBiometricKeyId: String = "biometricKeyId"
  private static let partialKeys: [String] = [keyRefreshToken, keyAnonymousId, keyBiometricKeyId];
  class KeyMaker {
    func scopedKey(key: String) -> String {
      return "authgear_\(key)"
    }
    func keyRefreshToken(namespace: String) -> String {
        return scopedKey(key: "\(namespace)_refreshToken")
    }
    func keyAnonymousKeyId(namespace: String) -> String {
        return scopedKey(key: "\(namespace)_anonymousKeyID")
    }
    func keyBiometricKeyId(namespace: String) -> String {
        return scopedKey(key: "\(namespace)_biometricKeyID")
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
        guard let args = call.arguments as? Dictionary<String, String> else {
            result("INVALID_ARGUMENT")
            return
        }
        guard let containerName = args["containerName"] else {
            result("INVALID_ARGUMENT")
            return
        }
      result(hasFlutterData(containerName: containerName))
    case "hasXamarinData":
        guard let args = call.arguments as? Dictionary<String, String> else {
            result("INVALID_ARGUMENT")
                return
        }
        guard let containerName = args["containerName"] else {
            result("INVALID_ARGUMENT")
            return
        }
        guard let packageName = args["packageName"] else {
            result("INVALID_ARGUMENT")
            return
        }
      result(hasXamarinData(packageName: packageName, containerName: containerName))
    case "migrate":
        guard let args = call.arguments as? Dictionary<String, String> else {
            result("INVALID_ARGUMENT")
            return
        }
        guard let containerName = args["containerName"] else {
            result("INVALID_ARGUMENT")
            return
        }
        guard let packageName = args["packageName"] else {
            result("INVALID_ARGUMENT")
            return
        }
        do {
            result(try migrate(packageName: packageName, containerName: containerName))
        } catch (let e) {
            result(e)
        }
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  private func hasFlutterData(containerName: String) -> Bool {
    let keyMaker = KeyMaker()
    let hasRefreshToken = storageGetItem(key: keyMaker.keyRefreshToken(namespace: containerName)) != nil
    let hasAnonymousKeyId = storageGetItem(key: keyMaker.keyAnonymousKeyId(namespace: containerName)) != nil
    let hasBiometricKeyId = storageGetItem(key: keyMaker.keyBiometricKeyId(namespace: containerName)) != nil
    return hasRefreshToken || hasAnonymousKeyId || hasBiometricKeyId
  }

  private func xamarinFullKey(containerName: String, subKey: String) -> String {
    return "\(containerName)_\(subKey)"
  }

  private func xamarinEssentialAlias(packageName: String) -> String {
    return "\(packageName)_xamarinessentials"
  }

  private func hasXamarinData(packageName: String, containerName: String) -> Bool {
    let alias = xamarinEssentialAlias(packageName: packageName)
    for subKey in MigratetoolPlugin.partialKeys {
    let fullKey = xamarinFullKey(containerName: containerName, subKey: subKey)
    let value = storageGetItem(key: fullKey, service: alias)
      if value != nil {
        return true
      }
    }
    return false
  }

  private func migrate(packageName: String, containerName: String) throws -> Bool {
    let alias = xamarinEssentialAlias(packageName: packageName)
    let refreshToken = storageGetItem(key: xamarinFullKey(containerName: containerName, subKey: MigratetoolPlugin.keyRefreshToken))
    let anonymousId = storageGetItem(key: xamarinFullKey(containerName: containerName, subKey: MigratetoolPlugin.keyAnonymousId))
    let biometricKeyId = storageGetItem(key: xamarinFullKey(containerName: containerName, subKey: MigratetoolPlugin.keyBiometricKeyId))
    let keyMaker = KeyMaker()
    if let nonNilToken = refreshToken {
        try storageSetItem(key: keyMaker.keyRefreshToken(namespace: containerName), value: nonNilToken)
    }
    if let nonNilAnonymousId = anonymousId{
        try storageSetItem(key: keyMaker.keyAnonymousKeyId(namespace: containerName), value: nonNilAnonymousId)
    }
    if let nonNilBiometricKeyId = biometricKeyId {
        try storageSetItem(key: keyMaker.keyBiometricKeyId(namespace: containerName), value: nonNilBiometricKeyId)
    }
    return false
  }

  // Direct copy of https://github.com/authgear/authgear-sdk-flutter/blob/main/ios/Classes/SwiftAuthgearPlugin.swift#L389
  private func storageSetItem(key: String, value: String) throws {
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
      return
    default:
      let addQuery: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrAccount as String: key,
        kSecValueData as String: data,
      ]

      let addStatus = SecItemAdd(addQuery as CFDictionary, nil)
      switch addStatus {
      case errSecSuccess:
        return
      default:
          throw NSError(domain: "Fail to set item", code: 0)
      }
    }
  }


  // Direct copy of https://github.com/authgear/authgear-sdk-flutter/blob/main/ios/Classes/SwiftAuthgearPlugin.swift#L420C3-L442C4
  private func storageGetItem(key: String, service: String? = nil) -> String? {
    var query: [String: Any] = [
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
