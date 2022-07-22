package dev.jiejie.fluent_tencent_cos

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import com.tencent.cos.xml.CosXmlService
import com.tencent.cos.xml.transfer.TransferConfig
import androidx.annotation.NonNull
import com.tencent.cos.xml.CosXmlServiceConfig
import com.tencent.cos.xml.exception.CosXmlClientException
import com.tencent.cos.xml.exception.CosXmlServiceException
import com.tencent.cos.xml.listener.CosXmlProgressListener
import com.tencent.cos.xml.listener.CosXmlResultListener
import com.tencent.cos.xml.model.CosXmlRequest
import com.tencent.cos.xml.model.CosXmlResult
import com.tencent.cos.xml.transfer.COSXMLUploadTask
import com.tencent.cos.xml.transfer.TransferManager

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding


/** FluentTencentCosPlugin  */
class FluentTencentCosPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private var channel: MethodChannel? = null
  private var mContext: Context? = null
  private var mActivity: Activity? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.getFlutterEngine().dartExecutor, "fluent_tencent_cos")
    mContext = flutterPluginBinding.applicationContext
    channel?.setMethodCallHandler(this)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    if (call.method.equals("putObject")) {
      Log.d("onMethodCall", "putObject")
      val secretId: String = call.argument("accessKeyId")!! //secretId
      val secretKey: String = call.argument("accessKeySecret")!! //secretKey
      val securityToken: String = call.argument("securityToken")!! //secretKey
      val expiredTime: Long = call.argument("expiredTime")!! //secretKey

      // 此秘钥计算方法与项目中用到的不符合，所以不使用该方法生成秘钥
      // QCloudCredentialProvider myCredentialProvider =
      //        new ShortTimeCredentialProvider(secretId, secretKey, 300);
      val myCredentialProvider = LocalSessionCredentialProvider(
        secretId,
        secretKey,
        securityToken,
        expiredTime,
      )
      val region: String = call.argument("region")!! // region
      val bucket: String = call.argument("bucketName")!! // bucket
      val localPath: String = call.argument("filePath")!! // localPath
      val cosPath: String= call.argument("objectName")!! // cosPath

      /// 初始化 COS Service
      // 创建 CosXmlServiceConfig 对象，根据需要修改默认的配置参数
      val serviceConfig: CosXmlServiceConfig = CosXmlServiceConfig.Builder()
        .setRegion(region)
        .isHttps(true) // 使用 HTTPS 请求, 默认为 HTTP 请求
        .builder()
      val cosXmlService = CosXmlService(mContext, serviceConfig, myCredentialProvider)

      // 初始化 TransferConfig，这里使用默认配置，如果需要定制，请参考 SDK 接口文档
      val transferConfig: TransferConfig = TransferConfig.Builder().build()
      //初始化 TransferManager
      val transferManager = TransferManager(cosXmlService, transferConfig)
      //上传文件
      val cosxmlUploadTask: COSXMLUploadTask = transferManager.upload(bucket, cosPath, localPath, null)
      val data = HashMap<String, Any>()
      data["taskId"] = cosPath
      data["localPath"] = localPath
      data["objectName"] = cosPath
      Log.d("onMethodCall", "startUpload")
      cosxmlUploadTask.setCosXmlProgressListener { complete, target ->
        Log.d("onProgress", "$complete : $target")
        mActivity?.runOnUiThread {
          val res: MutableMap<String, Any> = java.util.HashMap()
          res["taskId"] = cosPath
          res["filePath"] = cosPath
          res["objectName"] = localPath
          res["currentSize"] = complete
          res["totalSize"] = target
          res["percent"] = complete * 100.0 / target
          channel?.invokeMethod("onProgress", res)
        }
      }

      //设置返回结果回调
      cosxmlUploadTask.setCosXmlResultListener(object : CosXmlResultListener {
        override fun onSuccess(request: CosXmlRequest?, httpResult: CosXmlResult) {
          Log.d("onSuccess", httpResult.printResult())
          val res: MutableMap<String, Any> = java.util.HashMap()
          res["url"] = httpResult.accessUrl ?: ""
          res["taskId"] = cosPath
          mActivity?.runOnUiThread{
            channel?.invokeMethod("onSuccess", res)
          }
        }

        override fun onFail(request: CosXmlRequest?, exception: CosXmlClientException?, serviceException: CosXmlServiceException) {
          Log.d("onFail", exception.toString() + serviceException.toString())
          data["errorMessage"] = exception.toString() + serviceException.toString()
          mActivity?.runOnUiThread {
            channel?.invokeMethod("onFailed", data)
          }
          if (exception != null) {
            exception.printStackTrace()
          } else {
            serviceException.printStackTrace()
          }
        }
      })
      result.success(cosPath)
    } else {
      result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel?.setMethodCallHandler(null)
    channel = null
  }

  ///activity 生命周期
  override fun onAttachedToActivity(@NonNull binding: ActivityPluginBinding) {
    mActivity = binding.activity
  }

  override fun onDetachedFromActivity() {
    mActivity = null
  }

  override fun onDetachedFromActivityForConfigChanges() {}

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {}


}