package com.example.android.bluetoothadvertisements;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ScannerFragment extends Fragment {
    private static final long CONNECTION_TIMEOUT_MS = 20_000L;
    private static final UUID SECURITY_SERVICE_UUID =
            UUID.fromString("6B1F0001-4C2A-4F7E-9D3B-5A8C1E2F7A10");
    private static final UUID SECURITY_STATUS_UUID =
            UUID.fromString("6B1F0002-4C2A-4F7E-9D3B-5A8C1E2F7A10");
    private static final String TAG = "BluetoothScanner";
    private static final int MAX_LOG_ENTRIES = 300;
    private static final long SCAN_DIAGNOSTIC_DELAY_MS = 10_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, DiscoveredDevice> discoveredDevices = new HashMap<>();
    private final Map<String, BluetoothGatt> gattConnections = new HashMap<>();
    private final Map<String, Runnable> connectionTimeouts = new HashMap<>();
    private final ArrayList<String> logs = new ArrayList<>();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private ScanCallback scanCallback;
    private ScanResultAdapter deviceAdapter;
    private LogAdapter logAdapter;
    private ListView deviceList;
    private ListView logList;
    private Button scanButton;
    private TextView scanStatus;
    private TextView statusMessage;
    private ProgressBar scanProgress;
    private boolean scanning;
    private boolean receiverRegistered;
    private boolean bondReceiverRegistered;
    private int lastBluetoothState = Integer.MIN_VALUE;
    private int scanResultCount;
    private Runnable scanDiagnostic;

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                updateBluetoothState();
            }
        }
    };

    private final BroadcastReceiver bondStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) {
                return;
            }
            String id;
            try {
                id = device.getAddress();
            } catch (SecurityException exception) {
                log("无法读取配对设备地址：缺少蓝牙连接权限");
                return;
            }
            if (!gattConnections.containsKey(id)) {
                return;
            }
            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
            int previousState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
            if (state == BluetoothDevice.BOND_BONDING) {
                log("设备 " + id + " 正在配对，请按系统提示操作");
            } else if (state == BluetoothDevice.BOND_BONDED) {
                log("设备 " + id + " 配对成功");
            } else if (state == BluetoothDevice.BOND_NONE
                    && previousState == BluetoothDevice.BOND_BONDING) {
                log("设备 " + id + " 配对失败或已取消，请检查设备配对模式后重新连接");
                showMessage("配对失败或已取消，请查看蓝牙事件日志。");
            }
        }
    };

    public void setBluetoothAdapter(BluetoothAdapter adapter) {
        bluetoothAdapter = adapter;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scanner, container, false);
        deviceList = view.findViewById(R.id.device_list);
        logList = view.findViewById(R.id.log_list);
        scanButton = view.findViewById(R.id.scan_button);
        scanStatus = view.findViewById(R.id.scan_status);
        statusMessage = view.findViewById(R.id.bluetooth_status);
        scanProgress = view.findViewById(R.id.scan_progress);
        deviceAdapter = new ScanResultAdapter(requireContext());
        logAdapter = new LogAdapter();
        deviceList.setAdapter(deviceAdapter);
        logList.setAdapter(logAdapter);
        deviceList.setEmptyView(view.findViewById(R.id.device_empty));
        logList.setEmptyView(view.findViewById(R.id.log_empty));

        deviceList.setOnItemClickListener((parent, row, position, id) ->
                toggleConnection(deviceAdapter.getItem(position)));
        scanButton.setOnClickListener(v -> {
            if (scanning) {
                stopScanning();
            } else {
                startScanning();
            }
        });
        view.findViewById(R.id.clear_logs).setOnClickListener(v -> clearLogs());
        requireContext().registerReceiver(bondStateReceiver,
                new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        bondReceiverRegistered = true;
        updateBluetoothState();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null && !receiverRegistered) {
            getContext().registerReceiver(bluetoothStateReceiver,
                    new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            receiverRegistered = true;
        }
        updateBluetoothState();
    }

    @Override
    public void onPause() {
        stopScanning();
        if (receiverRegistered && getContext() != null) {
            getContext().unregisterReceiver(bluetoothStateReceiver);
            receiverRegistered = false;
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        stopScanning();
        if (bondReceiverRegistered && getContext() != null) {
            getContext().unregisterReceiver(bondStateReceiver);
            bondReceiverRegistered = false;
        }
        closeAllGattConnections();
        super.onDestroyView();
    }

    private void closeAllGattConnections() {
        for (Runnable timeout : connectionTimeouts.values()) {
            handler.removeCallbacks(timeout);
        }
        connectionTimeouts.clear();
        for (BluetoothGatt gatt : gattConnections.values()) {
            gatt.close();
        }
        gattConnections.clear();
    }

    private void updateBluetoothState() {
        if (bluetoothAdapter == null && getContext() != null) {
            BluetoothManager manager = (BluetoothManager) getContext()
                    .getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = manager == null ? null : manager.getAdapter();
        }

        String message = null;
        int currentState = -1;
        String stateDescription = "不支持";
        if (bluetoothAdapter == null) {
            message = "此设备不支持蓝牙。";
        } else {
            try {
                currentState = bluetoothAdapter.getState();
                if (currentState == BluetoothAdapter.STATE_OFF
                        || currentState == BluetoothAdapter.STATE_TURNING_OFF) {
                    message = "蓝牙已关闭，请在系统设置中开启蓝牙。";
                } else if (currentState != BluetoothAdapter.STATE_ON) {
                    message = "蓝牙正在启动，请稍候。";
                }
                stateDescription = bluetoothStateDescription(currentState);
            } catch (SecurityException exception) {
                currentState = -2;
                stateDescription = "未授权";
                message = "此 App 没有蓝牙权限，请在系统设置中允许附近设备权限。";
            }
        }
        if (message == null && Build.VERSION.SDK_INT <= Build.VERSION_CODES.R
                && !isLocationEnabled()) {
            message = "请开启系统定位服务后再扫描（Android 6–11 扫描 BLE 需要定位开关）。";
        }
        if (currentState != lastBluetoothState) {
            lastBluetoothState = currentState;
            log("蓝牙状态：" + stateDescription);
        }

        if (statusMessage == null || scanButton == null) {
            return;
        }

        statusMessage.setText(message == null ? "" : message);
        statusMessage.setVisibility(message == null ? View.GONE : View.VISIBLE);
        scanButton.setEnabled(message == null);
        updateScanControls();
        if (message != null && scanning) {
            stopScanning();
            log("蓝牙不可用，扫描已停止");
            resetConnectionStates();
        }
        if (message != null && !gattConnections.isEmpty()) {
            closeAllGattConnections();
            resetConnectionStates();
        }
    }

    private String bluetoothStateDescription(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_ON:
                return "已开启";
            case BluetoothAdapter.STATE_OFF:
                return "已关闭";
            case BluetoothAdapter.STATE_TURNING_ON:
                return "启动中";
            case BluetoothAdapter.STATE_TURNING_OFF:
                return "关闭中";
            default:
                return state == -2 ? "未授权" : "未知";
        }
    }

    private void startScanning() {
        if (bluetoothAdapter == null || !hasBluetoothPermissions()) {
            showMessage("请授予蓝牙扫描和连接权限后重试。");
            return;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                updateBluetoothState();
                return;
            }
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bleScanner == null) {
                showMessage("无法启动 BLE 扫描，请检查蓝牙是否已开启。");
                log("无法启动扫描：BLE 扫描器不可用");
                return;
            }

            Iterator<Map.Entry<String, DiscoveredDevice>> iterator =
                    discoveredDevices.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue().connectionState
                        == DiscoveredDevice.ConnectionState.DISCONNECTED) {
                    iterator.remove();
                }
            }
            refreshDeviceList();

            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    updateDevice(result);
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    for (ScanResult result : results) {
                        updateDevice(result);
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    runOnUiThread(() -> {
                        String error = scanErrorDescription(errorCode);
                        log("扫描失败：" + error + "（错误代码 " + errorCode + "）");
                        if (statusMessage != null) {
                            statusMessage.setText("扫描失败：" + error + "（错误代码 "
                                    + errorCode + "），请稍后重试。");
                            statusMessage.setVisibility(View.VISIBLE);
                        }
                        showMessage("扫描失败：" + error);
                        stopScanning();
                    });
                }
            };
            scanResultCount = 0;
            bleScanner.startScan(null,
                    new ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .build(),
                    scanCallback);
            scanning = true;
            log("开始扫描 BLE 设备");
            updateScanControls();
            scheduleScanDiagnostic();
        } catch (SecurityException exception) {
            scanCallback = null;
            scanning = false;
            showMessage("请授予蓝牙扫描和连接权限后重试。");
            log("启动扫描失败：缺少蓝牙权限");
            updateScanControls();
        }
    }

    private void stopScanning() {
        if (!scanning) {
            return;
        }
        try {
            if (bleScanner != null && scanCallback != null && hasBluetoothPermissions()) {
                bleScanner.stopScan(scanCallback);
            }
        } catch (SecurityException exception) {
            log("停止扫描失败：缺少蓝牙权限");
        }
        scanning = false;
        scanCallback = null;
        if (scanDiagnostic != null) {
            handler.removeCallbacks(scanDiagnostic);
            scanDiagnostic = null;
        }
        log("已停止扫描");
        updateScanControls();
    }

    private void updateDevice(ScanResult result) {
        runOnUiThread(() -> {
            BluetoothDevice device = result.getDevice();
            String name;
            try {
                String advertisedName = result.getScanRecord() == null
                        ? null : result.getScanRecord().getDeviceName();
                name = advertisedName != null ? advertisedName : device.getName();
            } catch (SecurityException exception) {
                name = null;
            }
            if (name == null || name.trim().isEmpty()) {
                name = "未命名设备";
            }
            Boolean connectable = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                connectable = result.isConnectable();
            }

            String id = device.getAddress();
            DiscoveredDevice existing = discoveredDevices.get(id);
            boolean isNew = existing == null;
            scanResultCount++;
            if (scanDiagnostic != null) {
                handler.removeCallbacks(scanDiagnostic);
                scanDiagnostic = null;
            }
            DiscoveredDevice.ConnectionState state = existing == null
                    ? DiscoveredDevice.ConnectionState.DISCONNECTED
                    : existing.connectionState;
            discoveredDevices.put(id, new DiscoveredDevice(device, name, result.getRssi(),
                    System.currentTimeMillis(), connectable, state));
            refreshDeviceList();
            if (statusMessage != null) {
                statusMessage.setVisibility(View.GONE);
            }
            if (scanStatus != null && scanning) {
                scanStatus.setText("正在扫描 · 已发现 " + discoveredDevices.size() + " 台设备");
            }
            if (isNew) {
                log("发现设备：" + name + " (" + id + ")，信号 " + result.getRssi() + " dBm");
            }
        });
    }

    private void toggleConnection(DiscoveredDevice device) {
        if (bluetoothAdapter == null || !hasBluetoothPermissions()) {
            showMessage("请授予蓝牙扫描和连接权限后重试。");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            updateBluetoothState();
            return;
        }

        String id = device.id;
        switch (device.connectionState) {
            case DISCONNECTED:
                if (Boolean.FALSE.equals(device.connectable)) {
                    showNotice("此设备未提供可连接的蓝牙广播。");
                    log("无法连接 " + device.name + "：" + id + " 的广播不可连接");
                    return;
                }
                setConnectionState(id, DiscoveredDevice.ConnectionState.CONNECTING);
                log("正在连接 " + device.name + " (" + id + ")");
                try {
                    BluetoothGatt gatt = device.device.connectGatt(
                            getContext(), false, createGattCallback(id));
                    if (gatt == null) {
                        setConnectionState(id, DiscoveredDevice.ConnectionState.DISCONNECTED);
                        showNotice("无法启动 BLE 连接。");
                        log("连接启动失败：" + device.name + " (" + id + ")");
                        return;
                    }
                    gattConnections.put(id, gatt);
                    scheduleConnectionTimeout(id, gatt, device.name);
                } catch (SecurityException exception) {
                    setConnectionState(id, DiscoveredDevice.ConnectionState.DISCONNECTED);
                    showMessage("请授予蓝牙连接权限后重试。");
                    log("连接失败：" + device.name + " (" + id + ")，缺少蓝牙权限");
                }
                break;
            case CONNECTING:
            case CONNECTED:
                log(device.connectionState == DiscoveredDevice.ConnectionState.CONNECTING
                        ? "取消连接 " + device.name : "正在断开 " + device.name);
                cancelConnectionTimeout(id);
                setConnectionState(id, DiscoveredDevice.ConnectionState.DISCONNECTING);
                BluetoothGatt gatt = gattConnections.get(id);
                if (gatt != null) {
                    try {
                        gatt.disconnect();
                    } catch (SecurityException exception) {
                        log("断开连接失败：" + device.name + " (" + id + ")，缺少蓝牙权限");
                    }
                }
                break;
            case DISCONNECTING:
                break;
        }
    }

    private BluetoothGattCallback createGattCallback(String id) {
        return new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                runOnUiThread(() -> {
                    if (status == BluetoothGatt.GATT_SUCCESS
                            && newState == BluetoothProfile.STATE_CONNECTED) {
                        if (getConnectionState(id) != DiscoveredDevice.ConnectionState.CONNECTING) {
                            safeDisconnect(gatt);
                            return;
                        }
                        cancelConnectionTimeout(id);
                        setConnectionState(id, DiscoveredDevice.ConnectionState.CONNECTED);
                        String name = getDeviceName(id);
                        log("已连接：" + name + " (" + id + ")");
                        requestPairing(gatt.getDevice(), id);
                        if (name.toLowerCase(Locale.ROOT).contains("zephyr")) {
                            log("Zephyr 设备 " + id + "：正在查找自定义安全状态服务");
                            try {
                                if (!gatt.discoverServices()) {
                                    log("设备 " + id + " 安全服务发现请求未能发送");
                                }
                            } catch (SecurityException exception) {
                                log("设备 " + id + " 安全服务发现失败：缺少蓝牙连接权限");
                            }
                        }
                    } else if (status != BluetoothGatt.GATT_SUCCESS
                            || newState == BluetoothProfile.STATE_DISCONNECTED) {
                        boolean wasConnecting = getConnectionState(id)
                                == DiscoveredDevice.ConnectionState.CONNECTING;
                        boolean wasDisconnecting = getConnectionState(id)
                                == DiscoveredDevice.ConnectionState.DISCONNECTING;
                        cancelConnectionTimeout(id);
                        logGattStatus("连接断开", id, status);
                        setConnectionState(id, DiscoveredDevice.ConnectionState.DISCONNECTED);
                        log(status == BluetoothGatt.GATT_SUCCESS
                                ? "已断开：" + getDeviceName(id) + " (" + id + ")"
                                : "连接已断开：" + getDeviceName(id) + " (" + id
                                        + ")，状态码 " + status);
                        closeGatt(id, gatt);
                        if (wasConnecting) {
                            showNotice("连接失败，状态码：" + status);
                        } else if (!wasDisconnecting && status != BluetoothGatt.GATT_SUCCESS) {
                            showNotice("连接已断开，状态码：" + status);
                        }
                    }
                });
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                runOnUiThread(() -> {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        log("设备 " + id + " 安全服务发现失败，状态码 " + status);
                        return;
                    }

                    // Try to write OS type identifier (Android) to the device
                    writeOsTypeIfAvailable(gatt, id);

                    BluetoothGattService service = gatt.getService(SECURITY_SERVICE_UUID);
                    if (service == null) {
                        log("设备 " + id + " 未提供自定义安全状态服务，无法读取外设报告的加密等级");
                        return;
                    }
                    BluetoothGattCharacteristic characteristic =
                            service.getCharacteristic(SECURITY_STATUS_UUID);
                    if (characteristic == null) {
                        log("设备 " + id + " 未提供安全状态特征");
                        return;
                    }
                    int properties = characteristic.getProperties();
                    if ((properties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY
                            | BluetoothGattCharacteristic.PROPERTY_INDICATE)) == 0) {
                        log("设备 " + id + " 安全状态特征不支持通知或指示，无法订阅");
                        return;
                    }
                    try {
                        if (!gatt.setCharacteristicNotification(characteristic, true)) {
                            log("设备 " + id + " 安全状态通知订阅启动失败");
                            return;
                        }
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                        if (descriptor == null) {
                            log("设备 " + id + " 安全状态特征缺少客户端配置描述符");
                            return;
                        }
                        descriptor.setValue((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                                ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        log("设备 " + id + " 正在订阅外设安全状态");
                        if (!gatt.writeDescriptor(descriptor)) {
                            log("设备 " + id + " 安全状态订阅请求未能发送");
                        }
                    } catch (SecurityException exception) {
                        log("设备 " + id + " 安全状态订阅失败：缺少蓝牙连接权限");
                    }
                });
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt,
                                          android.bluetooth.BluetoothGattDescriptor descriptor,
                                          int status) {
                if (!SECURITY_STATUS_UUID.equals(descriptor.getCharacteristic().getUuid())
                        || !SECURITY_SERVICE_UUID.equals(descriptor.getCharacteristic()
                                .getService().getUuid())) {
                    return;
                }
                runOnUiThread(() -> {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        log("设备 " + id + " 安全状态订阅失败，状态码 " + status);
                        return;
                    }
                    log("设备 " + id + " 已订阅安全状态");
                    BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
                    if ((characteristic.getProperties()
                            & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                        try {
                            if (!gatt.readCharacteristic(characteristic)) {
                                log("设备 " + id + " 安全状态读取请求未能发送");
                            }
                        } catch (SecurityException exception) {
                            log("设备 " + id + " 安全状态读取失败：缺少蓝牙连接权限");
                        }
                    } else {
                        log("设备 " + id + " 安全状态不支持读取，等待外设通知");
                    }
                });
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt,
                                                BluetoothGattCharacteristic characteristic) {
                handleSecurityStatus(id, characteristic, 0);
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt,
                                             BluetoothGattCharacteristic characteristic,
                                             int status) {
                handleSecurityStatus(id, characteristic, status);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt,
                                                BluetoothGattCharacteristic characteristic,
                                                byte[] value) {
                handleSecurityStatus(id, characteristic, 0, value);
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt,
                                             BluetoothGattCharacteristic characteristic,
                                             byte[] value, int status) {
                handleSecurityStatus(id, characteristic, status, value);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt,
                                              BluetoothGattCharacteristic characteristic,
                                              int status) {
                if (Constants.OS_TYPE_CHAR_UUID.equals(characteristic.getUuid())) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        log("设备 " + id + " OS 类型已成功写入（Android）");
                    } else {
                        log("设备 " + id + " OS 类型写入失败，状态码 " + status);
                    }
                }
            }
        };
    }

    private void requestPairing(BluetoothDevice device, String id) {
        try {
            int state = device.getBondState();
            if (state == BluetoothDevice.BOND_BONDED) {
                log("设备 " + id + " 已配对，无需再次发起配对");
            } else if (state == BluetoothDevice.BOND_BONDING) {
                log("设备 " + id + " 正在配对，请按系统提示操作");
            } else if (device.createBond()) {
                log("设备 " + id + " 已发起配对请求，请按系统提示操作");
            } else {
                log("设备 " + id + " 配对请求未能启动，请检查设备配对模式后重新连接");
                showMessage("配对请求未能启动，请查看蓝牙事件日志。");
            }
        } catch (SecurityException exception) {
            log("设备 " + id + " 配对请求失败：缺少蓝牙连接权限");
            showMessage("请授予蓝牙连接权限后重试。");
        }
    }

    private void handleSecurityStatus(String id, BluetoothGattCharacteristic characteristic,
                                      int status) {
        handleSecurityStatus(id, characteristic, status, characteristic.getValue());
    }

    private void handleSecurityStatus(String id, BluetoothGattCharacteristic characteristic,
                                      int status, byte[] value) {
        if (!SECURITY_STATUS_UUID.equals(characteristic.getUuid())
                || characteristic.getService() == null
                || !SECURITY_SERVICE_UUID.equals(characteristic.getService().getUuid())) {
            return;
        }
        runOnUiThread(() -> {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("设备 " + id + " 安全状态读取或通知失败，状态码 " + status);
                return;
            }
            if (value == null || value.length < 2) {
                log("设备 " + id + " 安全状态数据无效：需要至少 2 字节");
                return;
            }
            int level = value[0] & 0xff;
            int code = value[1] & 0xff;
            log("设备 " + id + " 外设报告安全状态：等级 " + level + "，错误 " + code
                    + "（" + securityErrorDescription(code) + "）");
            if (level == 1 && code == 4
                    && getConnectionState(id) == DiscoveredDevice.ConnectionState.CONNECTED) {
                String name = getDeviceName(id);
                setConnectionState(id, DiscoveredDevice.ConnectionState.DISCONNECTING);
                showNotice(name + " 报告安全等级 1、错误 4（不支持配对）。"
                        + "请按设备上的配对键，使设备进入配对模式，然后重新连接。");
                log("设备 " + id + " 报告等级 1、错误 4，正在断开连接并提示用户");
                safeDisconnect(gattConnections.get(id));
            }
        });
    }

    private String securityErrorDescription(int code) {
        String[] descriptions = {
                "成功", "认证失败", "PIN 或密钥缺失", "带外认证失败", "不支持配对",
                "不允许配对", "参数无效", "密钥被拒绝", "未指定错误"
        };
        return code >= 0 && code < descriptions.length ? descriptions[code] : "未知错误";
    }

    private void writeOsTypeIfAvailable(BluetoothGatt gatt, String id) {
        try {
            BluetoothGattService service = gatt.getService(Constants.OS_TYPE_SVC_UUID);
            if (service == null) {
                return;
            }

            BluetoothGattCharacteristic ch = service.getCharacteristic(Constants.OS_TYPE_CHAR_UUID);
            if (ch == null) {
                return;
            }

            byte[] value = {Constants.OS_TYPE_ANDROID};

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                @SuppressWarnings("deprecation")
                byte[] oldValue = ch.getValue();
                ch.setValue(value);
                ch.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                @SuppressWarnings("deprecation")
                boolean success = gatt.writeCharacteristic(ch);
                if (!success) {
                    log("设备 " + id + " OS 类型写入请求未能发送");
                }
            }
            log("设备 " + id + " 正在写入 OS 类型（Android）...");
        } catch (SecurityException exception) {
            log("设备 " + id + " OS 类型写入失败：缺少蓝牙连接权限");
        } catch (Exception exception) {
            log("设备 " + id + " OS 类型写入异常：" + exception.getMessage());
        }
    }

    private void scheduleConnectionTimeout(String id, BluetoothGatt gatt, String name) {
        cancelConnectionTimeout(id);
        Runnable timeout = () -> {
            if (getConnectionState(id) != DiscoveredDevice.ConnectionState.CONNECTING) {
                return;
            }
            connectionTimeouts.remove(id);
            setConnectionState(id, DiscoveredDevice.ConnectionState.DISCONNECTING);
            safeDisconnect(gatt);
            showNotice("连接超时，请确认设备可连接并靠近手机，然后重试。");
            log("连接超时：" + name + " (" + id + ")");
        };
        connectionTimeouts.put(id, timeout);
        handler.postDelayed(timeout, CONNECTION_TIMEOUT_MS);
    }

    private void cancelConnectionTimeout(String id) {
        Runnable timeout = connectionTimeouts.remove(id);
        if (timeout != null) {
            handler.removeCallbacks(timeout);
        }
    }

    private void safeDisconnect(BluetoothGatt gatt) {
        if (gatt == null) {
            return;
        }
        try {
            gatt.disconnect();
        } catch (SecurityException exception) {
            log("断开连接失败：缺少蓝牙连接权限");
        }
    }

    private void closeGatt(String id, BluetoothGatt gatt) {
        if (gattConnections.get(id) == gatt) {
            gattConnections.remove(id);
        }
        gatt.close();
    }

    private void logGattStatus(String callback, String id, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            String securityDetail = status == 5 ? "，认证失败"
                    : status == 15 ? "，加密等级不足" : "";
            log(callback + "：Android GATT 状态码 " + status + securityDetail
                    + "；设备 " + id + "。");
        }
    }

    private void resetConnectionStates() {
        for (String id : new ArrayList<>(discoveredDevices.keySet())) {
            cancelConnectionTimeout(id);
            setConnectionState(id, DiscoveredDevice.ConnectionState.DISCONNECTED);
        }
    }

    private void setConnectionState(String id, DiscoveredDevice.ConnectionState state) {
        DiscoveredDevice device = discoveredDevices.get(id);
        if (device != null) {
            device.connectionState = state;
            refreshDeviceList();
        }
    }

    private DiscoveredDevice.ConnectionState getConnectionState(String id) {
        DiscoveredDevice device = discoveredDevices.get(id);
        return device == null ? DiscoveredDevice.ConnectionState.DISCONNECTED
                : device.connectionState;
    }

    private String getDeviceName(String id) {
        DiscoveredDevice device = discoveredDevices.get(id);
        return device == null ? "未命名设备" : device.name;
    }

    private void refreshDeviceList() {
        if (deviceAdapter == null) {
            return;
        }
        ArrayList<DiscoveredDevice> devices = new ArrayList<>(discoveredDevices.values());
        Collections.sort(devices, Comparator
                .comparingLong((DiscoveredDevice device) -> device.lastSeen).reversed()
                .thenComparing(device -> device.name, String.CASE_INSENSITIVE_ORDER));
        deviceAdapter.setDevices(devices);
        if (scanStatus != null) {
            scanStatus.setText(scanning ? "正在扫描" : "已发现 " + devices.size() + " 台设备");
        }
    }

    private void updateScanControls() {
        if (scanButton == null) {
            return;
        }
        scanButton.setText(scanning ? "停止扫描" : "开始扫描");
        scanButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                getResources().getColor(scanning ? R.color.scan_stop : R.color.scan_start)));
        if (scanProgress != null) {
            scanProgress.setVisibility(scanning ? View.VISIBLE : View.GONE);
        }
        if (scanStatus != null) {
            scanStatus.setText(scanning ? "正在扫描 · 已发现 " + discoveredDevices.size() + " 台设备"
                    : "已发现 " + discoveredDevices.size() + " 台设备");
        }
    }

    private void scheduleScanDiagnostic() {
        scanDiagnostic = () -> {
            if (!scanning || scanResultCount != 0 || statusMessage == null) {
                return;
            }
            String message = Build.VERSION.SDK_INT <= Build.VERSION_CODES.R
                    ? "仍未发现设备。请确认系统定位服务已开启，并检查附近是否有正在广播的 BLE 设备。"
                    : "仍未发现设备。请确认附近有正在广播的 BLE 设备、应用的附近设备权限已允许，且设备未被其他条件限制。";
            statusMessage.setText(message);
            statusMessage.setVisibility(View.VISIBLE);
            log("扫描已持续 10 秒，尚未收到设备广播");
        };
        handler.postDelayed(scanDiagnostic, SCAN_DIAGNOSTIC_DELAY_MS);
    }

    private String scanErrorDescription(int errorCode) {
        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                return "扫描已在运行";
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                return "系统未能注册扫描，请关闭并重新打开蓝牙后重试";
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                return "设备不支持 BLE 扫描";
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                return "蓝牙系统内部错误";
            case ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES:
                return "蓝牙扫描资源不足";
            case ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY:
                return "扫描请求过于频繁，请稍后重试";
            default:
                return "未知扫描错误";
        }
    }

    private boolean isLocationEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManager locationManager =
                    (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            return locationManager != null && locationManager.isLocationEnabled();
        }
        return android.provider.Settings.Secure.getInt(
                requireContext().getContentResolver(),
                android.provider.Settings.Secure.LOCATION_MODE,
                android.provider.Settings.Secure.LOCATION_MODE_OFF)
                != android.provider.Settings.Secure.LOCATION_MODE_OFF;
    }

    private void log(String message) {
        String entry = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new java.util.Date()) + "  " + message;
        logs.add(entry);
        if (logs.size() > MAX_LOG_ENTRIES) {
            logs.subList(0, logs.size() - MAX_LOG_ENTRIES).clear();
        }
        if (logAdapter != null) {
            logAdapter.notifyDataSetChanged();
            if (logList != null && logAdapter.getCount() > 0) {
                logList.setSelection(0);
            }
        }
    }

    private void clearLogs() {
        logs.clear();
        if (logAdapter != null) {
            logAdapter.notifyDataSetChanged();
        }
    }

    private boolean hasBluetoothPermissions() {
        if (getContext() == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(getContext(),
                    Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(getContext(),
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void showMessage(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private void showNotice(String message) {
        if (!isAdded() || getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        String visibleMessage = message == null || message.trim().isEmpty()
                ? "未提供详细信息，请查看蓝牙事件日志。"
                : message;
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("蓝牙连接")
                .setMessage(visibleMessage)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) {
                messageView.setText(visibleMessage);
                messageView.setTextColor(android.graphics.Color.rgb(33, 33, 33));
                messageView.setTextSize(16);
                messageView.setVisibility(View.VISIBLE);
            }
        });
        dialog.show();
    }

    private void runOnUiThread(Runnable action) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(action);
        }
    }

    private final class LogAdapter extends android.widget.BaseAdapter {
        @Override
        public int getCount() {
            return logs.size();
        }

        @Override
        public Object getItem(int position) {
            return logs.get(logs.size() - position - 1);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView = convertView instanceof TextView ? (TextView) convertView
                    : new TextView(requireContext());
            textView.setText(getItem(position).toString());
            textView.setTextSize(11);
            textView.setTextIsSelectable(true);
            textView.setPadding(0, 4, 0, 4);
            return textView;
        }
    }
}
