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
    ConnectionState connectionState = ConnectionState.DISCONNECTED;

    DiscoveredDevice(BluetoothDevice device, String name, int rssi, long lastSeen,
                     Boolean connectable, ConnectionState connectionState) {
        this.device = device;
        this.id = device.getAddress();
        this.name = name;
        this.rssi = rssi;
        this.lastSeen = lastSeen;
        this.connectable = connectable;
        this.connectionState = connectionState;
    }
}
