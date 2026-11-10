package com.example.android.bluetoothadvertisements;

import android.bluetooth.BluetoothDevice;

final class DiscoveredDevice {
    enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }

    final BluetoothDevice device;
    final String id;
    String name;
    int rssi;
    long lastSeen;
    Boolean connectable;
    final String boundPhonePublicAddress;
    final boolean invalidBindingData;
    final boolean bindingDataUnavailable;
    ConnectionState connectionState = ConnectionState.DISCONNECTED;

    DiscoveredDevice(BluetoothDevice device, String name, int rssi, long lastSeen,
                     Boolean connectable, String boundPhonePublicAddress,
                     boolean invalidBindingData, boolean bindingDataUnavailable,
                     ConnectionState connectionState) {
        this.device = device;
        this.id = device.getAddress();
        this.name = name;
        this.rssi = rssi;
        this.lastSeen = lastSeen;
        this.connectable = connectable;
        this.boundPhonePublicAddress = boundPhonePublicAddress;
        this.invalidBindingData = invalidBindingData;
        this.bindingDataUnavailable = bindingDataUnavailable;
        this.connectionState = connectionState;
    }
}
