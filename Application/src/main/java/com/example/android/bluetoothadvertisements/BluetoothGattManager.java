package com.example.android.bluetoothadvertisements;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

/**
 * Manager for BLE GATT operations related to OS type identification.
 * 
 * This class handles:
 * - Connecting to BLE devices
 * - Discovering services after connection
 * - Writing the OS type identifier (Android) to the device
 */
public class BluetoothGattManager {

    private final Context context;
    private final GattOperationCallback callback;

    /**
     * Callback interface for GATT operations
     */
    public interface GattOperationCallback {
        /**
         * Called when OS type has been successfully written to the device
         *
         * @param deviceId the MAC address of the device
         * @param success true if the write was successful
         */
        void onOsTypeWritten(String deviceId, boolean success);

        /**
         * Called when there is a status update
         *
         * @param message a descriptive message about the operation
         */
        void onStatusUpdate(String message);
    }

    /**
     * Creates a new BluetoothGattManager instance
     *
     * @param context the application context
     * @param callback callback for GATT operation results
     */
    public BluetoothGattManager(Context context, GattOperationCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    /**
     * Initiates a connection to the specified device and performs OS type identification
     *
     * @param device the BluetoothDevice to connect to
     * @return the BluetoothGatt instance representing the connection
     */
    public BluetoothGatt connect(BluetoothDevice device) {
        return device.connectGatt(context, false, createGattCallback(device.getAddress()));
    }

    /**
     * Creates the GATT callback that handles connection and service discovery
     *
     * @param deviceId the MAC address of the device
     * @return a BluetoothGattCallback instance
     */
    private BluetoothGattCallback createGattCallback(String deviceId) {
        return new BluetoothGattCallback() {

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (callback != null) {
                        callback.onStatusUpdate("设备已连接：" + deviceId + "，正在发现服务...");
                    }
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (callback != null) {
                        callback.onStatusUpdate("设备已断开连接：" + deviceId);
                    }
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (callback != null) {
                        callback.onOsTypeWritten(deviceId, false);
                        callback.onStatusUpdate("服务发现失败，状态码：" + status);
                    }
                    return;
                }

                // Get the OS Type service and characteristic
                BluetoothGattCharacteristic ch = gatt.getService(Constants.OS_TYPE_SVC_UUID)
                        .getCharacteristic(Constants.OS_TYPE_CHAR_UUID);

                if (ch == null) {
                    if (callback != null) {
                        callback.onStatusUpdate("设备未提供 OS 类型特征值");
                    }
                    return;
                }

                // Prepare the OS type value (Android = 1)
                byte[] value = {Constants.OS_TYPE_ANDROID};

                // Write the characteristic using the appropriate API for the Android version
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Android 13+ uses the new API
                        gatt.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    } else {
                        // Android 12 and below use the deprecated API
                        @SuppressWarnings("deprecation")
                        byte[] oldValue = ch.getValue();
                        ch.setValue(value);
                        ch.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                        @SuppressWarnings("deprecation")
                        boolean success = gatt.writeCharacteristic(ch);
                        if (!success && callback != null) {
                            callback.onStatusUpdate("OS 类型写入失败（API 级别 <= 31）");
                        }
                    }
                    if (callback != null) {
                        callback.onStatusUpdate("正在写入 OS 类型（Android）...");
                    }
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onStatusUpdate("OS 类型写入异常：" + e.getMessage());
                    }
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic ch,
                                               int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (callback != null) {
                        callback.onOsTypeWritten(deviceId, true);
                        callback.onStatusUpdate("OS 类型已成功写入设备");
                    }
                } else {
                    if (callback != null) {
                        callback.onOsTypeWritten(deviceId, false);
                        callback.onStatusUpdate("OS 类型写入失败，状态码：" + status);
                    }
                }
            }
        };
    }
}
