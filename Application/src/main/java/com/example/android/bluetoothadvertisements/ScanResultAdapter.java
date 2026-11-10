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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class ScanResultAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final ArrayList<DiscoveredDevice> devices = new ArrayList<>();

    ScanResultAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    void setDevices(ArrayList<DiscoveredDevice> newDevices) {
        devices.clear();
        devices.addAll(newDevices);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public DiscoveredDevice getItem(int position) {
        return devices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return devices.get(position).id.hashCode();
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        if (view == null) {
            view = inflater.inflate(R.layout.listitem_scanresult, parent, false);
        }

        DiscoveredDevice device = devices.get(position);
        ((TextView) view.findViewById(R.id.device_name)).setText(device.name);
        ((TextView) view.findViewById(R.id.device_address)).setText(device.id);
        ((TextView) view.findViewById(R.id.signal_strength))
                .setText(device.rssi + " dBm · 最近发现 " + timeSince(device.lastSeen));
        TextView bindingStatus = view.findViewById(R.id.binding_status);
        if (device.bindingDataUnavailable) {
            bindingStatus.setText("绑定信息不可用");
        } else if (device.invalidBindingData) {
            bindingStatus.setText("绑定信息无效");
        } else if (device.boundPhonePublicAddress == null) {
            bindingStatus.setText("未绑定状态");
        } else {
            bindingStatus.setText("绑定手机 public 地址：" + device.boundPhonePublicAddress);
        }
        ((TextView) view.findViewById(R.id.connection_state))
                .setText(connectionDescription(device));
        return view;
    }

    private String connectionDescription(DiscoveredDevice device) {
        switch (device.connectionState) {
            case CONNECTING:
                return "连接中 · 点击取消";
            case CONNECTED:
                return "已连接 · 点击断开";
            case DISCONNECTING:
                return "正在断开";
            case DISCONNECTED:
            default:
                return Boolean.FALSE.equals(device.connectable) ? "不可连接" : "点击连接";
        }
    }

    private String timeSince(long timestamp) {
        long seconds = Math.max(0, (System.currentTimeMillis() - timestamp) / 1000);
        if (seconds < 5) {
            return "刚刚";
        } else if (seconds < 60) {
            return seconds + " 秒前";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " 分钟前";
        } else {
            long hours = seconds / 3600;
            return hours + " 小时前";
        }
    }
}
