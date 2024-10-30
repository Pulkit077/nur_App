package com.myapp

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.nordicid.nurapi.BleScanner
import com.nordicid.nurapi.DeviceScannerManager
import com.nordicid.nurapi.DeviceScannerManager.ScanListener
import com.nordicid.nurapi.NurApi
import com.nordicid.nurapi.NurDeviceSpec

class DeviceScannerModule(private val reactContext: ReactApplicationContext?) :
    ReactContextBaseJavaModule(
        reactContext
    ),
    ScanListener {
    private val handler: Handler
    private val nurApi: NurApi
    private var deviceScannerManager: DeviceScannerManager? = null

    init {
        val handlerThread = HandlerThread("DeviceScannerThread")
        handlerThread.start()

        val looper = handlerThread.looper
        handler = Handler(looper)

        nurApi = NurApi()

        // Initialize scanner on the handler thread
        handler.post { this.initializeScanner() }
    }

    private fun initializeScanner() {
        try {
            if (reactContext == null) {
                Log.e(TAG, "React context is null")
                return
            }

            // Initialize BleScanner first
            BleScanner.init(reactContext)

            // Initialize BLE adapter first
            val bluetoothManager =
                reactContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

            val bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter == null) {
                Log.e(TAG, "Bluetooth adapter not available")
                return
            }

            // Now initialize the device scanner manager
            this.deviceScannerManager = DeviceScannerManager(
                reactContext,
                7,  // requestedDevices - ALL_DEVICES
                nurApi,
                this
            )

            Log.d(TAG, "Scanner initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing scanner", e)
        }
    }

    override fun getName(): String {
        return "DeviceScanner"
    }

    @ReactMethod
    fun startScan() {
        handler.post {
            try {
                if (deviceScannerManager == null) {
                    Log.e(
                        TAG,
                        "DeviceScannerManager is null, reinitializing..."
                    )
                    initializeScanner()
                }

                if (deviceScannerManager != null) {
                    deviceScannerManager!!.startScan()
                } else {
                    Log.e(
                        TAG,
                        "Failed to initialize DeviceScannerManager"
                    )
                    // Notify JS side of error
                    val params = Arguments.createMap()
                    params.putString("error", "Failed to initialize scanner")
                    sendEvent("onScanError", params)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting scan", e)
                // Notify JS side of error
                val params = Arguments.createMap()
                params.putString("error", e.message)
                sendEvent("onScanError", params)
            }
        }
    }

    @ReactMethod
    fun stopScan() {
        handler.post {
            try {
                if (deviceScannerManager != null) {
                    deviceScannerManager!!.stopScan()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan", e)
            }
        }
    }

    @ReactMethod
    fun getDevicesList(callback: Callback) {
        handler.post {
            try {
                if (deviceScannerManager != null) {
                    val deviceArray =
                        Arguments.createArray()
                    for (device in deviceScannerManager!!.devicesList) {
                        val deviceMap = Arguments.createMap()
                        deviceMap.putString("name", device.name)
                        deviceMap.putString("address", device.address)
                        deviceMap.putString("type", device.type)
                        deviceArray.pushMap(deviceMap)
                    }
                    callback.invoke(null, deviceArray)
                } else {
                    callback.invoke("Scanner not initialized", null)
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error getting devices list",
                    e
                )
                callback.invoke(e.message, null)
            }
        }
    }

    private fun sendEvent(eventName: String, params: WritableMap) {
        try {
            reactContext
                ?.getJSModule(ReactContext.RCTDeviceEventEmitter::class.java)
                ?.emit(eventName, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending event", e)
        }
    }

    override fun onDeviceFound(device: NurDeviceSpec) {
        try {
            val params = Arguments.createMap()
            params.putString("name", device.name)
            params.putString("address", device.address)
            params.putString("type", device.type)
            sendEvent("onDeviceFound", params)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling device found", e)
        }
    }

    override fun onScanFinished(deviceList: List<NurDeviceSpec>) {
        try {
            val devices = Arguments.createArray()
            for (device in deviceList) {
                val deviceMap = Arguments.createMap()
                deviceMap.putString("name", device.name)
                deviceMap.putString("address", device.address)
                deviceMap.putString("type", device.type)
                devices.pushMap(deviceMap)
            }
            val params = Arguments.createMap()
            params.putArray("devices", devices)
            sendEvent("onScanFinished", params)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling scan finished", e)
        }
    }

    companion object {
        private const val TAG = "DeviceScannerModule"
    }
}