package com.myapp;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.nordicid.nurapi.BleScanner;
import com.nordicid.nurapi.DeviceScannerManager;
import com.nordicid.nurapi.NurDeviceSpec;
import com.nordicid.nurapi.NurApi;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.util.List;

public class DeviceScannerModule extends ReactContextBaseJavaModule implements DeviceScannerManager.ScanListener {
    private static final String TAG = "DeviceScannerModule";
    private final ReactApplicationContext reactContext;
    private DeviceScannerManager deviceScannerManager;
    private final Handler handler;
    private final NurApi nurApi;

    public DeviceScannerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;

        HandlerThread handlerThread = new HandlerThread("DeviceScannerThread");
        handlerThread.start();

        Looper looper = handlerThread.getLooper();
        handler = new Handler(looper);

        nurApi = new NurApi();

        // Initialize scanner on the handler thread
        handler.post(this::initializeScanner);
    }

    private void initializeScanner() {
        try {
            if (reactContext == null) {
                Log.e(TAG, "React context is null");
                return;
            }

            // Initialize BleScanner first
            BleScanner.init(reactContext);

            // Initialize BLE adapter first
            BluetoothManager bluetoothManager = (BluetoothManager) reactContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                Log.e(TAG, "Bluetooth manager not available");
                return;
            }

            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter == null) {
                Log.e(TAG, "Bluetooth adapter not available");
                return;
            }

            // Now initialize the device scanner manager
            this.deviceScannerManager = new DeviceScannerManager(
                    reactContext,
                    7, // requestedDevices - ALL_DEVICES
                    nurApi,
                    this
            );

            Log.d(TAG, "Scanner initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing scanner", e);
        }
    }

    @NonNull
    @Override
    public String getName() {
        return "DeviceScanner";
    }

    @ReactMethod
    public void startScan() {
        handler.post(() -> {
            try {
                if (deviceScannerManager == null) {
                    Log.e(TAG, "DeviceScannerManager is null, reinitializing...");
                    initializeScanner();
                }

                if (deviceScannerManager != null) {
                    deviceScannerManager.startScan();
                } else {
                    Log.e(TAG, "Failed to initialize DeviceScannerManager");
                    // Notify JS side of error
                    WritableMap params = Arguments.createMap();
                    params.putString("error", "Failed to initialize scanner");
                    sendEvent("onScanError", params);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error starting scan", e);
                // Notify JS side of error
                WritableMap params = Arguments.createMap();
                params.putString("error", e.getMessage());
                sendEvent("onScanError", params);
            }
        });
    }

    @ReactMethod
    public void stopScan() {
        handler.post(() -> {
            try {
                if (deviceScannerManager != null) {
                    deviceScannerManager.stopScan();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping scan", e);
            }
        });
    }

    @ReactMethod
    public void getDevicesList(Callback callback) {
        handler.post(() -> {
            try {
                if (deviceScannerManager != null) {
                    WritableArray deviceArray = Arguments.createArray();
                    for (NurDeviceSpec device : deviceScannerManager.getDevicesList()) {
                        WritableMap deviceMap = Arguments.createMap();
                        deviceMap.putString("name", device.getName());
                        deviceMap.putString("address", device.getAddress());
                        deviceMap.putString("type", device.getType());
                        deviceArray.pushMap(deviceMap);
                    }
                    callback.invoke(null, deviceArray);
                } else {
                    callback.invoke("Scanner not initialized", null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting devices list", e);
                callback.invoke(e.getMessage(), null);
            }
        });
    }

    private void sendEvent(String eventName, WritableMap params) {
        try {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, params);
        } catch (Exception e) {
            Log.e(TAG, "Error sending event", e);
        }
    }

    @Override
    public void onDeviceFound(NurDeviceSpec device) {
        try {
            WritableMap params = Arguments.createMap();
            params.putString("name", device.getName());
            params.putString("address", device.getAddress());
            params.putString("type", device.getType());
            sendEvent("onDeviceFound", params);
        } catch (Exception e) {
            Log.e(TAG, "Error handling device found", e);
        }
    }

    @Override
    public void onScanFinished(List<NurDeviceSpec> deviceList) {
        try {
            WritableArray devices = Arguments.createArray();
            for (NurDeviceSpec device : deviceList) {
                WritableMap deviceMap = Arguments.createMap();
                deviceMap.putString("name", device.getName());
                deviceMap.putString("address", device.getAddress());
                deviceMap.putString("type", device.getType());
                devices.pushMap(deviceMap);
            }
            WritableMap params = Arguments.createMap();
            params.putArray("devices", devices);
            sendEvent("onScanFinished", params);
        } catch (Exception e) {
            Log.e(TAG, "Error handling scan finished", e);
        }
    }
}