package com.authgear.sdk.migrateplugin.xamarinflutter

import android.os.Build
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.Base64
import android.security.keystore.KeyProperties
import androidx.annotation.NonNull
import androidx.security.crypto.MasterKey
import androidx.security.crypto.EncryptedSharedPreferences
import com.google.crypto.tink.shaded.protobuf.InvalidProtocolBufferException

import java.security.KeyStore
import java.security.KeyPair
import java.security.Key
import java.security.PrivateKey
import java.security.InvalidAlgorithmParameterException
import java.security.GeneralSecurityException
import java.nio.charset.StandardCharsets
import java.io.File
import java.io.IOException

import javax.crypto.SecretKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result


/** MigratetoolPlugin */
class MigratetoolPlugin: FlutterPlugin, MethodCallHandler {
  companion object {
    private val TAG: String = MigratetoolPlugin.javaClass.simpleName
    private const val ENCRYPTED_SHARED_PREFERENCES_NAME = "authgear_encrypted_shared_preferences"
    private val keyRefreshToken = "refresh_token"
    private val keyAnonymousId = "anonymousId"
    private val keyBiometricKeyId = "biometricKeyId"
    private val keyUseSymmetricPreference = "essentials_use_symmetric";
    private val keyMasterKey = "SecureStorageKey"
    private val aesAlgorithm = "AES"
    private val cipherTransformationAsymmetric = "RSA/ECB/PKCS1Padding"
    private val cipherTransformationSymmetric = "AES/GCM/NoPadding"
    private const val initializationVectorLen = 12; // Android supports an IV of 12 for AES/GCM
    private val subKeys = listOf(keyRefreshToken, keyAnonymousId, keyBiometricKeyId)
  }
  class KeyMaker {
    fun scopedKey(key: String): String {
      return "authgear_${key}"
    }
    fun keyRefreshToken(namespace: String): String {
      return scopedKey("${namespace}_refreshToken")
    }
    fun keyAnonymousKeyId(namespace: String): String {
      return scopedKey("${namespace}_anonymousKeyID")
    }
    fun keyBiometricKeyId(namespace: String): String {
      return scopedKey("${namespace}_biometricKeyID")
    }
  }
  class XamarinStorage(private val context: Context) {
    private fun alias(packageName: String): String {
      return "${packageName}.xamarinessentials"
    }

    private fun getPrefs(packageName: String): SharedPreferences {
      return context.getSharedPreferences(alias(packageName), Context.MODE_PRIVATE)
    }

    private fun fullKey(containerName: String, subKey: String): String {
      return "${containerName}_${subKey}"
    }

    fun hasXamarinData(packageName: String, containerName: String): Boolean {
      val prefs = getPrefs(packageName)
      for (subKey in subKeys) {
        val key = fullKey(containerName, subKey)
        val value = prefs.getString(key, "")
        if (value != "") {
          return true
        }
      }
      return false
    }
    // A port of https://github.com/xamarin/Essentials/blob/main/Xamarin.Essentials/SecureStorage/SecureStorage.android.cs#L55
    fun getValue(keyStore: KeyStore, packageName: String, containerName: String, subKey: String): String? {
      // TODO: Handle MD5 legacy handling
      val prefs = getPrefs(packageName)
      val valueRaw = prefs.getString(fullKey(containerName, subKey), null)
      if (valueRaw == null || valueRaw == "") {
        return null
      }
      val base64Decoded = Base64.decode(valueRaw, Base64.NO_WRAP)
      return decrypt(keyStore, packageName, base64Decoded)
    }

    // A port of https://github.com/xamarin/Essentials/blob/main/Xamarin.Essentials/SecureStorage/SecureStorage.android.cs#L364
    fun decrypt(keyStore: KeyStore, packageName: String, data: ByteArray): String? {
      if (data.size < initializationVectorLen) {
        return null
      }
      val key = getKey(keyStore, packageName)
      val iv = data.copyOf(initializationVectorLen)
      var cipher: Cipher
      try {
        cipher = Cipher.getInstance(cipherTransformationSymmetric)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
      } catch (e: InvalidAlgorithmParameterException) {
        cipher = Cipher.getInstance(cipherTransformationSymmetric)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
      }
      val decryptedData = cipher.doFinal(data, initializationVectorLen, data.size - initializationVectorLen)
      return String(decryptedData, StandardCharsets.UTF_8)
    }

    // A port of https://github.com/xamarin/Essentials/blob/main/Xamarin.Essentials/SecureStorage/SecureStorage.android.cs#L176
    fun getKey(keyStore: KeyStore, packageName: String): SecretKey {
      val prefs = getPrefs(packageName)
      val useSymmetric = prefs.getBoolean(keyUseSymmetricPreference, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
      if (useSymmetric) {
        return getSymmetricKey(packageName, keyStore)
      }
      val keyPair = getAsymmetricKeyPair(keyStore, packageName)
      val existingKeyStr = prefs.getString(keyMasterKey, null) ?: ""
      if (existingKeyStr != "") {
        try {
          val wrappedKey = Base64.decode(existingKeyStr, Base64.NO_WRAP)
          val unwrappedKey = unwrapKey(wrappedKey, keyPair.private)
          return unwrappedKey as SecretKey
        } catch (e: Throwable) {
          Log.d(TAG, "Failed to unwrap key: ${e}")
        }
      }
      // We only migrate, so we skip porting the key-generating code
      throw IllegalStateException("No existing xamarin secure storage key found")
    }

    fun getSymmetricKey(packageName: String, keyStore: KeyStore): SecretKey {
      val existingKey = keyStore.getKey(alias(packageName), null)
      if (existingKey != null) {
        return existingKey as SecretKey
      }
      // We only migrate, so we skip porting the key-generating code
      throw IllegalStateException("No existing xamarin secure storage symmetric key found")
    }

    fun getAsymmetricKeyPair(keyStore: KeyStore, packageName: String): KeyPair {
      val asymmetricAlias = "${alias(packageName)}.asymmetric"
      val privateKey = keyStore.getKey(asymmetricAlias, null) as PrivateKey
      val publicKey = keyStore.getCertificate(asymmetricAlias)?.let { it.publicKey }
      if (privateKey != null && publicKey != null) {
        return KeyPair(publicKey, privateKey)
      }
      // We only migrate, so we skip porting the key-generating code
      throw IllegalStateException("No existing xamarin secure storage symmetric key found")
    }

    fun unwrapKey(data: ByteArray, key: Key): Key {
      val cipher = Cipher.getInstance(cipherTransformationAsymmetric)
      cipher.init(Cipher.UNWRAP_MODE, key)
      return cipher.unwrap(data, KeyProperties.KEY_ALGORITHM_AES, Cipher.SECRET_KEY)
    }
  }
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel: MethodChannel
  private var pluginBinding: FlutterPlugin.FlutterPluginBinding? = null
  private lateinit var context: Context
  private lateinit var xamarinStorage: XamarinStorage

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "authgear_sdk_tool_migrate_xamarin_flutter")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
    pluginBinding = flutterPluginBinding
    xamarinStorage = XamarinStorage(context)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    try {
      if (call.method == "hasXamarinData") {
        val packageName = call.argument<String>("packageName")
        val containerName = call.argument<String>("containerName")
        if (packageName == null || containerName == null) {
          result.error("INVALID_ARGUMENT", "Expected {packageName, containerName}", null);
        } else {
          result.success(xamarinStorage.hasXamarinData(packageName, containerName))
        }
      } else if (call.method == "hasFlutterData") {
        val packageName = call.argument<String>("packageName")
        val containerName = call.argument<String>("containerName")
        if (packageName == null || containerName == null) {
          result.error("INVALID_ARGUMENT", "Expected {packageName, containerName}", null);
        } else {
          result.success(hasFlutterData(packageName, containerName))
        }
      } else if (call.method == "migrate") {
        val packageName = call.argument<String>("packageName")
        val containerName = call.argument<String>("containerName")
        if (packageName == null || containerName == null) {
          result.error("INVALID_ARGUMENT", "Expected {packageName, containerName}", null);
        } else {
          result.success(migrate(packageName, containerName))
        }
      } else {
        result.notImplemented()
      }
    } catch (e: Throwable) {
      result.error("Unknown error", e.toString(), null)
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  fun migrate(packageName: String, containerName: String): Boolean {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    val refreshToken = xamarinStorage.getValue(keyStore, packageName, containerName, keyRefreshToken)
    val anonymousId = xamarinStorage.getValue(keyStore, packageName, containerName, keyAnonymousId)
    val biometricKeyId = xamarinStorage.getValue(keyStore, packageName, containerName, keyBiometricKeyId)
    val keyMaker = KeyMaker()
    if (refreshToken != null) {
      storageSetItem(keyMaker.keyRefreshToken(containerName), refreshToken)
    }
    if (anonymousId != null) {
      storageSetItem(keyMaker.keyAnonymousKeyId(containerName), anonymousId)
    }
    if (biometricKeyId != null) {
      storageSetItem(keyMaker.keyBiometricKeyId(containerName), biometricKeyId)
    }
    return false
  }

  fun hasFlutterData(packageName: String, containerName: String): Boolean {
    val keyMaker = KeyMaker()
    val hasRefreshToken = storageGetItem(keyMaker.keyRefreshToken(containerName)) != null
    val hasAnonymousKeyId = storageGetItem(keyMaker.keyAnonymousKeyId(containerName)) != null
    val hasBiometricKeyId = storageGetItem(keyMaker.keyBiometricKeyId(containerName)) != null
    return hasRefreshToken || hasAnonymousKeyId || hasBiometricKeyId
  }

  // Direct copy of https://github.com/authgear/authgear-sdk-flutter/blob/main/android/src/main/kotlin/com/authgear/flutter/AuthgearPlugin.kt#L469C3-L486C4
  private fun storageSetItem(key: String, value: String) {
    try {
      val sharedPreferences = this.getSharedPreferences()
      sharedPreferences.edit().putString(key, value).commit()
    } catch (e: Exception) {
      // NOTE(backup): Please search NOTE(backup) to understand what is going on here.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        if (e is GeneralSecurityException) {
          Log.w(TAG, "try to recover from backup problem in storageSetItem", e)
          val context = pluginBinding?.applicationContext!!
          deleteSharedPreferences(context, ENCRYPTED_SHARED_PREFERENCES_NAME)
          return storageSetItem(key, value)
        }
      }
    }
  }

  private fun storageGetItem(key: String): String? {
    try {
      val sharedPreferences = this.getSharedPreferences()
      val value = sharedPreferences.getString(key, null)
      return value
    } catch (e: Exception) {
      // NOTE(backup): Please search NOTE(backup) to understand what is going on here.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        if (e is GeneralSecurityException) {
          Log.w(TAG, "try to recover from backup problem in storageGetItem", e)
          val context = pluginBinding?.applicationContext!!
          deleteSharedPreferences(context, ENCRYPTED_SHARED_PREFERENCES_NAME)
          return storageGetItem(key)
        }
      }
      throw e
    }
  }

  // Direct copy of https://github.com/authgear/authgear-sdk-flutter/blob/main/android/src/main/kotlin/com/authgear/flutter/AuthgearPlugin.kt#L437
  private fun getSharedPreferences(): SharedPreferences {
    val context = pluginBinding?.applicationContext!!
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
      try {
        return EncryptedSharedPreferences.create(
          context,
          ENCRYPTED_SHARED_PREFERENCES_NAME,
          masterKey,
          EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
          EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
      } catch (e: InvalidProtocolBufferException) {
        // NOTE(backup): Please search NOTE(backup) to understand what is going on here.
        Log.w(TAG, "try to recover from backup problem in EncryptedSharedPreferences.create", e)
        deleteSharedPreferences(context, ENCRYPTED_SHARED_PREFERENCES_NAME)
        return getSharedPreferences()
      } catch (e: GeneralSecurityException) {
        // NOTE(backup): Please search NOTE(backup) to understand what is going on here.
        Log.w(TAG, "try to recover from backup problem in EncryptedSharedPreferences.create", e)
        deleteSharedPreferences(context, ENCRYPTED_SHARED_PREFERENCES_NAME)
        return getSharedPreferences()
      } catch (e: IOException) {
        // NOTE(backup): Please search NOTE(backup) to understand what is going on here.
        Log.w(TAG, "try to recover from backup problem in EncryptedSharedPreferences.create", e)
        deleteSharedPreferences(context, ENCRYPTED_SHARED_PREFERENCES_NAME)
        return getSharedPreferences()
      }
    }
    return context.getSharedPreferences("authgear_shared_preferences", Context.MODE_PRIVATE)
  }

  // Direct copy of https://github.com/authgear/authgear-sdk-flutter/blob/main/android/src/main/kotlin/com/authgear/flutter/AuthgearPlugin.kt#L398
  private fun deleteSharedPreferences(context: Context, name: String) {
    // NOTE(backup): See original repo's explanation.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      context.deleteSharedPreferences(name)
    } else {
      context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
      val dir = File(context.applicationInfo.dataDir, "shared_prefs")
      File(dir, "$name.xml").delete()
    }
  }
}
