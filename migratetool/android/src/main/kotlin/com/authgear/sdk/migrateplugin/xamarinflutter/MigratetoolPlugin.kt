package com.authgear.sdk.migrateplugin.xamarinflutter

import android.content.Context
import android.util.Log
import androidx.annotation.NonNull

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
  }
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel: MethodChannel
  private lateinit var context: Context

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "authgear_sdk_tool_migrate_xamarin_flutter")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    try {
      if (call.method == "hasXamarinData") {
        val packageName = call.arguments<String>()
        if (packageName == null) {
          result.error("INVALID_ARGUMENT", "Expected argument to be string", null);
        } else {
          result.success(hasXamarinData(packageName))
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

  fun hasXamarinData(packageName: String): Boolean {
    val alias = "${packageName}.xamarinessentials"
    val subKeys = listOf("refresh_token", "anonymousId", "biometricKeyId")
    val prefs = context.getSharedPreferences(alias, Context.MODE_PRIVATE)
    val containerName = "default"
    for (subKey in subKeys) {
      val value = prefs.getString(key, "")
      if (value != "") {
        return true
      } 
    }
    return false
  }
}
