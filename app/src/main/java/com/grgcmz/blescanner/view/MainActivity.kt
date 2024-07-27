package com.grgcmz.blescanner.view

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.grgcmz.blescanner.BuildConfig
import com.grgcmz.blescanner.controller.MultiplePermissionHandler
import com.grgcmz.blescanner.controller.Scanning
import com.grgcmz.blescanner.view.composables.DeviceList
import com.grgcmz.blescanner.view.composables.ScanButton
import com.grgcmz.blescanner.view.theme.BLEScannerTheme
import timber.log.Timber
import java.util.UUID

class MainActivity : ComponentActivity() {

    val TAG = "MainActivity"

    // lazy load bluetoothAdapter and bluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    // Create Multiple Permission Handler to handle all the required permissions
    private val multiplePermissionHandler: MultiplePermissionHandler by lazy {
        MultiplePermissionHandler(this, this)
    }

    // Scanning
    private val bluetoothLeScanner: BluetoothLeScanner by lazy { bluetoothAdapter?.bluetoothLeScanner!! }

    private val scanResults = mutableStateListOf<ScanResult>()

    // Define Scan Settings
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
        .build()

    private val deviceNameToFilter = "MS1089"
    private val scanFilters = ScanFilter.Builder()
        .setDeviceName(deviceNameToFilter)
        .build()

    // Device scan Callback
    private val scanCallback: ScanCallback = object : ScanCallback() {

        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
            } else {
                with(result.device) {
                    Timber.e("Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                }
                // 같은 결과가 들어 가는 것을 방지
                if(scanResults.indexOf(result) <  0) {
                    scanResults.add(result)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Timber.e("BLE Scan failed with error code: $errorCode")
        }
    }

    private lateinit var bleDevice: android.bluetooth.BluetoothDevice
    private var bluetoothGatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String) {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Timber.e("Bluetooth is not enabled")
            return
        }

        bleDevice = bluetoothAdapter!!.getRemoteDevice(deviceAddress)
        bluetoothGatt = bleDevice.connectGatt(this@MainActivity, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Timber.tag(TAG).d("Connection state changed: %s", newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Timber.e("Connected to device: %s", gatt.device.name)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Timber.e("Disconnected from device: %s", gatt.device.name)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.tag(TAG).i("Services discovered")

                // 서비스 및 특성 접근 예시
                val service = gatt.getService(UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB"))
                if (service != null) {
                    val characteristic = service.getCharacteristic(UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB"))
                    if (characteristic != null) {
                        // 특성 데이터 읽기, 쓰기 등을 수행
                        // ...
                        Timber.e("Services discovery returned: [${characteristic.value}]/${status}")
                        Toast.makeText(this@MainActivity, "Services discovery returned: [${characteristic.value}]/${status}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Timber.e("Services discovery failed: %s", status)
            }
        }
    }

    // On create function
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // init Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.e("Activity Created...")

        // Set the content to the ble scanner theme starting with the Scanning Screen
        setContent {
            BLEScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScanningScreen()
                }
            }
        }

        Timber.e("Content Set...")

        try {
            entry()
        } catch (e: Exception) {
            Timber.tag(e.toString())
        }
    }


    // Entry point for permission checks
    private fun entry() {
        multiplePermissionHandler.checkBlePermissions(bluetoothAdapter)
    }


    // Scanning Screen Composable
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ScanningScreen() {
        var isScanning: Boolean by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            // Scaffold as outermost on screen
            Scaffold(
                modifier = Modifier
                    .fillMaxSize(),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(text = "BLE Devices Nearby")
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            ) {
                // Order UI Elements in a column
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(paddingValues = it)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,

                        ) {
                        // Box containing a Column with the Devices
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(),

                                ) {
                                DeviceList(scanResults,
                                    doConnect = { deviceModel ->
                                        Timber.e("doConnect called, deviceModel: %s", deviceModel.address)
                                        connect(deviceModel.address)
                                    }
                                )
                            }
                        }
                    }
                    // Bottom Row containing two buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Clear Results Button
                        Button(
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 24.dp),
                            onClick = { scanResults.clear() },
                            content = {
                                Text("Clear Results")
                            }
                        )
                        // Start/Stop Scanning Button
                        ScanButton(
                            isScanning,
                            onClick = {
                                isScanning = Scanning.scanBleDevices(
                                    bluetoothLeScanner = bluetoothLeScanner,
                                    //scanFilters = listOf(scanFilters),
                                    null,
                                    scanSettings = scanSettings,
                                    scanCallback = scanCallback,
                                    scanning = isScanning
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // Preview Scanning Screen for Emulator
    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        BLEScannerTheme {
            ScanningScreen()
        }
    }
}