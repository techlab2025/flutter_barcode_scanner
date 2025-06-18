package com.amolg.flutterbarcodescanner;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter;

public class FlutterBarcodeScannerPlugin implements MethodCallHandler, FlutterPlugin, ActivityAware, EventChannel.StreamHandler {
    private static final String CHANNEL = "flutter_barcode_scanner";
    private static final String EVENT_CHANNEL = "flutter_barcode_scanner_receiver";
    private static final int RC_BARCODE_CAPTURE = 9001;

    private static final String TAG = FlutterBarcodeScannerPlugin.class.getSimpleName();

    private MethodChannel channel;
    private EventChannel eventChannel;
    private FlutterPluginBinding pluginBinding;
    private ActivityPluginBinding activityBinding;
    private Application applicationContext;
    private Lifecycle lifecycle;
    private LifeCycleObserver observer;
    private Activity activity;
    private static Result pendingResult;
    private Map<String, Object> arguments;
    public static String lineColor = "";
    public static boolean isShowFlashIcon = false;
    public static boolean isContinuousScan = false;
    static EventChannel.EventSink barcodeStream;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        this.pluginBinding = binding;
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        clearPluginSetup();
        this.pluginBinding = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.activityBinding = binding;
        this.activity = binding.getActivity();
        this.applicationContext = (Application) activity.getApplicationContext();

        BinaryMessenger messenger = pluginBinding.getBinaryMessenger();
        channel = new MethodChannel(messenger, CHANNEL);
        channel.setMethodCallHandler(this);

        eventChannel = new EventChannel(messenger, EVENT_CHANNEL);
        eventChannel.setStreamHandler(this);

        binding.addActivityResultListener(this::onActivityResult);

        lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(binding);
        observer = new LifeCycleObserver(activity);
        lifecycle.addObserver(observer);
        applicationContext.registerActivityLifecycleCallbacks(observer);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        clearPluginSetup();
    }

    private void clearPluginSetup() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(this::onActivityResult);
        }
        if (lifecycle != null && observer != null) {
            lifecycle.removeObserver(observer);
        }
        if (applicationContext != null && observer != null) {
            applicationContext.unregisterActivityLifecycleCallbacks(observer);
        }
        if (channel != null) {
            channel.setMethodCallHandler(null);
        }
        if (eventChannel != null) {
            eventChannel.setStreamHandler(null);
        }

        activityBinding = null;
        lifecycle = null;
        observer = null;
        channel = null;
        eventChannel = null;
        activity = null;
        applicationContext = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        pendingResult = result;

        if (call.method.equals("scanBarcode")) {
            if (!(call.arguments instanceof Map)) {
                result.error("INVALID_ARGUMENT", "Expected a Map argument", null);
                return;
            }

            arguments = (Map<String, Object>) call.arguments;
            lineColor = (String) arguments.getOrDefault("lineColor", "#DC143C");
            isShowFlashIcon = (boolean) arguments.getOrDefault("isShowFlashIcon", false);
            isContinuousScan = (boolean) arguments.getOrDefault("isContinuousScan", false);

            int scanMode = BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();
            if (arguments.get("scanMode") != null) {
                int mode = (int) arguments.get("scanMode");
                if (mode == BarcodeCaptureActivity.SCAN_MODE_ENUM.DEFAULT.ordinal()) {
                    scanMode = BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();
                } else {
                    scanMode = mode;
                }
            }

            BarcodeCaptureActivity.SCAN_MODE = scanMode;
            String cancelButtonText = (String) arguments.getOrDefault("cancelButtonText", "Cancel");

            startBarcodeScannerActivityView(cancelButtonText, isContinuousScan);
        } else {
            result.notImplemented();
        }
    }

    private void startBarcodeScannerActivityView(String buttonText, boolean isContinuousScan) {
        try {
            Intent intent = new Intent(activity, BarcodeCaptureActivity.class)
                    .putExtra("cancelButtonText", buttonText);

            if (isContinuousScan) {
                activity.startActivity(intent);
            } else {
                activity.startActivityForResult(intent, RC_BARCODE_CAPTURE);
            }
        } catch (Exception e) {
            Log.e(TAG, "startBarcodeScannerActivityView: " + e.getLocalizedMessage());
            if (pendingResult != null) {
                pendingResult.error("ACTIVITY_LAUNCH_ERROR", e.getMessage(), null);
                pendingResult = null;
            }
        }
    }

    private boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (pendingResult == null) return false;

            if (resultCode == CommonStatusCodes.SUCCESS && data != null) {
                try {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    pendingResult.success(barcode != null ? barcode.rawValue : "-1");
                } catch (Exception e) {
                    pendingResult.success("-1");
                }
            } else {
                pendingResult.success("-1");
            }

            pendingResult = null;
            arguments = null;
            return true;
        }

        return false;
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        barcodeStream = events;
    }

    @Override
    public void onCancel(Object arguments) {
        barcodeStream = null;
    }

    public static void onBarcodeScanReceiver(final Barcode barcode) {
        if (barcode != null && !barcode.displayValue.isEmpty() && barcodeStream != null && activity != null) {
            activity.runOnUiThread(() -> barcodeStream.success(barcode.rawValue));
        }
    }

    private static class LifeCycleObserver implements Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
        private final Activity thisActivity;

        LifeCycleObserver(Activity activity) {
            this.thisActivity = activity;
        }

        @Override
        public void onStop(@NonNull LifecycleOwner owner) {
            onActivityStopped(thisActivity);
        }

        @Override
        public void onDestroy(@NonNull LifecycleOwner owner) {
            onActivityDestroyed(thisActivity);
        }

        @Override
        public void onActivityStopped(Activity activity) {}

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (thisActivity == activity && activity.getApplicationContext() != null) {
                ((Application) activity.getApplicationContext()).unregisterActivityLifecycleCallbacks(this);
            }
        }

        // Unused lifecycle methods
        @Override public void onCreate(@NonNull LifecycleOwner owner) {}
        @Override public void onStart(@NonNull LifecycleOwner owner) {}
        @Override public void onResume(@NonNull LifecycleOwner owner) {}
        @Override public void onPause(@NonNull LifecycleOwner owner) {}
        @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityResumed(Activity activity) {}
        @Override public void onActivityPaused(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    }
}
