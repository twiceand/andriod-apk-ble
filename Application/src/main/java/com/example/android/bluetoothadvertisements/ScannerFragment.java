/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothadvertisements;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Scans for Bluetooth Low Energy Advertisements matching a filter and displays them to the user.
 */
public class ScannerFragment extends ListFragment {

    private static final String TAG = ScannerFragment.class.getSimpleName();

    /**
     * Stops scanning after 5 seconds.
     */
    private static final long SCAN_PERIOD = 5000;

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothLeScanner mBluetoothLeScanner;

    private ScanCallback mScanCallback;

    private ScanResultAdapter mAdapter;

    private Handler mHandler;

    private BluetoothDevice mPendingDevice;

    private BluetoothGatt mBluetoothGatt;

    private final BroadcastReceiver mBondStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE);
            if (mPendingDevice == null || device == null
                    || !mPendingDevice.getAddress().equals(device.getAddress())) {
                return;
            }

            int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.ERROR);
            if (bondState == BluetoothDevice.BOND_BONDED) {
                showMessage(R.string.pairing_succeeded);
            } else if (bondState == BluetoothDevice.BOND_NONE) {
                showMessage(R.string.pairing_failed);
                mPendingDevice = null;
            }
        }
    };

    /**
     * Must be called after object creation by MainActivity.
     *
     * @param btAdapter the local BluetoothAdapter
     */
    public void setBluetoothAdapter(BluetoothAdapter btAdapter) {
        this.mBluetoothAdapter = btAdapter;
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (mBluetoothAdapter == null && getActivity() != null) {
            BluetoothManager bluetoothManager = (BluetoothManager) getActivity()
                    .getSystemService(android.content.Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
            mBluetoothLeScanner = mBluetoothAdapter == null
                    ? null : mBluetoothAdapter.getBluetoothLeScanner();
        }

        // Use getActivity().getApplicationContext() instead of just getActivity() because this
        // object lives in a fragment and needs to be kept separate from the Activity lifecycle.
        //
        // We could get a LayoutInflater from the ApplicationContext but it messes with the
        // default theme, so generate it from getActivity() and pass it in separately.
        mAdapter = new ScanResultAdapter(getActivity().getApplicationContext(),
                LayoutInflater.from(getActivity()));
        mHandler = new Handler();

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        final View view = super.onCreateView(inflater, container, savedInstanceState);

        setListAdapter(mAdapter);

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getListView().setDivider(null);
        getListView().setDividerHeight(0);

        setEmptyText(getString(R.string.empty_list));

        // Trigger refresh on app's 1st load
        startScanning();

    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().registerReceiver(mBondStateReceiver,
                new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
    }

    @Override
    public void onStop() {
        getActivity().unregisterReceiver(mBondStateReceiver);
        super.onStop();
    }

    @Override
    public void onListItemClick(android.widget.ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);
        if (mPendingDevice != null || mBluetoothGatt != null) {
            showMessage(R.string.connection_in_progress);
            return;
        }

        stopScanning();
        BluetoothDevice device = ((ScanResult) mAdapter.getItem(position)).getDevice();
        mPendingDevice = device;
        try {
            showMessage(R.string.connecting_to_device);
            connectToDevice(device);
        } catch (SecurityException exception) {
            mPendingDevice = null;
            showMessage(R.string.bluetooth_permission_required);
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        if (mBluetoothGatt != null) {
            return;
        }
        try {
            showMessage(R.string.connecting_to_device);
            mBluetoothGatt = device.connectGatt(getActivity(), false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                                    int newState) {
                    if (status == BluetoothGatt.GATT_SUCCESS
                            && newState == BluetoothProfile.STATE_CONNECTED) {
                        showMessage(R.string.device_connected);
                        requestPairing(gatt.getDevice());
                    } else if (status != BluetoothGatt.GATT_SUCCESS
                            || newState == BluetoothProfile.STATE_DISCONNECTED) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            showMessage(R.string.device_disconnected);
                        } else {
                            showMessage(R.string.connection_failed, status);
                        }
                        gatt.close();
                        if (mBluetoothGatt == gatt) {
                            mBluetoothGatt = null;
                            mPendingDevice = null;
                        }
                    }
                }
            });
            if (mBluetoothGatt == null) {
                mPendingDevice = null;
                showMessage(R.string.connection_failed_to_start);
            }
        } catch (SecurityException exception) {
            mPendingDevice = null;
            showMessage(R.string.bluetooth_permission_required);
        }
    }

    private void requestPairing(BluetoothDevice device) {
        try {
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                showMessage(R.string.device_already_paired);
            } else if (device.getBondState() == BluetoothDevice.BOND_BONDING
                    || device.createBond()) {
                showMessage(R.string.pairing_requested);
            } else {
                showMessage(R.string.pairing_failed);
                mPendingDevice = null;
            }
        } catch (SecurityException exception) {
            showMessage(R.string.bluetooth_permission_required);
            mPendingDevice = null;
        }
    }

    private void showMessage(final int messageId) {
        if (getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    Toast.makeText(getActivity(), messageId, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void showMessage(final String message) {
        if (getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void showMessage(final int messageId, final int argument) {
        if (getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    Toast.makeText(getActivity(), getString(messageId, argument),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.scanner_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh) {
            startScanning();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Start scanning for BLE Advertisements (& set it up to stop after a set period of time).
     */
    public void startScanning() {
        if (mBluetoothLeScanner == null) {
            Toast.makeText(getActivity(), R.string.ble_scan_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        if (mScanCallback == null) {
            Log.d(TAG, "Starting Scanning");

            mAdapter.clear();
            mAdapter.notifyDataSetChanged();

            // Will stop the scanning after a set time.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopScanning();
                }
            }, SCAN_PERIOD);

            // Kick off a new scan.
            mScanCallback = new SampleScanCallback();
            mBluetoothLeScanner.startScan(new ArrayList<ScanFilter>(),
                    buildScanSettings(), mScanCallback);

            String toastText = getString(R.string.scan_start_toast) + " "
                    + TimeUnit.SECONDS.convert(SCAN_PERIOD, TimeUnit.MILLISECONDS) + " "
                    + getString(R.string.seconds);
            Toast.makeText(getActivity(), toastText, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getActivity(), R.string.already_scanning, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Stop scanning for BLE Advertisements.
     */
    public void stopScanning() {
        Log.d(TAG, "Stopping Scanning");

        if (mScanCallback == null) {
            return;
        }
        mHandler.removeCallbacksAndMessages(null);
        if (mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
        }
        mScanCallback = null;

        // Even if no new results, update 'last seen' times.
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Return a {@link ScanSettings} object set to use low power (to preserve battery life).
     */
    private ScanSettings buildScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);
        return builder.build();
    }

    /**
     * Custom ScanCallback object - adds to adapter on success, displays error on failure.
     */
    private class SampleScanCallback extends ScanCallback {

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);

            for (ScanResult result : results) {
                mAdapter.add(result);
            }
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            mAdapter.add(result);
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            if (getActivity() != null) {
                Toast.makeText(getActivity(), getString(R.string.scan_failed, errorCode),
                        Toast.LENGTH_LONG).show();
            }
            stopScanning();
        }
    }

    @Override
    public void onPause() {
        stopScanning();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        super.onDestroy();
    }
}
