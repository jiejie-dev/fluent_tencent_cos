import 'dart:async';

import 'package:fluent_object_storage/put_object_event_handler.dart';
import 'package:fluent_object_storage/put_object_request.dart';
import 'package:fluent_object_storage/put_object_result.dart';
import 'package:flutter/services.dart';

export 'package:fluent_object_storage/fluent_object_storage.dart';

class FluentTencentCos {
  static final MethodChannel _channel =
      const MethodChannel('fluent_tencent_cos')
        ..setMethodCallHandler(_methodHandler);

  static final Map<String, ObjectStoragePutObjectEventHandler> _handlers = {};

  static final StreamController<ObjectStoragePutObjectResult>
      _streamController =
      StreamController<ObjectStoragePutObjectResult>.broadcast()
        ..stream.listen((event) {
          final handler = _handlers[event.taskId];
          print("find handler for taskid " + (event.taskId ?? ""));
          if (handler != null) {
            print("dispatch event");
            handler.dispatch(event);
            if (event.isFinished) _handlers.remove(event.taskId);
          }
        });

  static Future _methodHandler(MethodCall call) {
    final result = call.arguments;
    final taskId = result['taskId'];
    switch (call.method) {
      case 'onProgress':
        _streamController.sink.add(ObjectStoragePutObjectResult(
            taskId: taskId,
            currentSize: result['currentSize'],
            totalSize: result['totalSize']));
        break;
      case 'onSuccess':
        _streamController.sink.add(
            ObjectStoragePutObjectResult(taskId: taskId, url: result['url']));
        break;
      case 'onFailure':
        _streamController.sink.add(ObjectStoragePutObjectResult(
            taskId: taskId, errorMessage: result['errorMessage']));
    }
    return Future.value();
  }

  /// 简单文件上传
  static Future<ObjectStoragePutObjectEventHandler> putObject(
      ObjectStoragePutObjectRequest putObjectRequest) async {
    String taskId =
        await _channel.invokeMethod("putObject", putObjectRequest.toMap());
    final ObjectStoragePutObjectEventHandler handler =
        ObjectStoragePutObjectEventHandler(taskId: taskId);
    print("add taskId " + taskId);
    _handlers[taskId] = handler;
    return handler;
  }

  static void setMethodCallHandler(
      Future<dynamic> Function(MethodCall call) handler) {
    _channel.setMethodCallHandler(handler);
  }

  void dispose() {
    _streamController.close();
    _handlers.clear();
  }
}
