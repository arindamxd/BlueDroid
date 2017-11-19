/*
 * Copyright 2016-2017 tiagohm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tiagohm.bluedroid;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

public class BlueDiscoveryDialog extends AlertDialog implements BlueDroid.ConnectionListener, BlueDroid.DiscoveryListener {
    private final BlueDroid mBluetooth;
    private final View mView;
    private final Activity mActivity;

    public BlueDiscoveryDialog(@NonNull Activity activity, BlueDroid bt) {
        super(activity, false, null);

        mBluetooth = bt;
        mActivity = activity;

        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        setView(mView = LayoutInflater.from(activity).inflate(R.layout.dialog_bluetooth_discovery, null, false));

        mBluetooth.addConnectionListener(this);
        mBluetooth.addDiscoveryListener(this);

        ((ListView) mView.findViewById(R.id.device_list_view)).setAdapter(bt.getAdapter());
        ((ListView) mView.findViewById(R.id.device_list_view)).setDividerHeight(0);
        ((ListView) mView.findViewById(R.id.device_list_view)).setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!mBluetooth.isConnected()) {
                    Device device = (Device) view.getTag();
                    mBluetooth.connect(device);
                }
            }
        });

        mView.findViewById(R.id.scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetooth.doDiscovery(mActivity);
            }
        });

        mView.findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetooth.cancelDiscovery();
                dismiss();
            }
        });

        mView.findViewById(R.id.ok_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetooth.cancelDiscovery();
                dismiss();
            }
        });
    }

    @Override
    public void dismiss() {
        mBluetooth.removeDiscoveryListener(this);
        mBluetooth.removeConnectionListener(this);
        super.dismiss();
    }

    @Override
    public void onDeviceConnecting() {
    }

    @Override
    public void onDeviceConnected() {
        dismiss();
    }

    @Override
    public void onDeviceDisconnected() {
    }

    @Override
    public void onDeviceConnectionFailed() {
    }

    @Override
    public void onDiscoveryStarted() {
        mView.findViewById(R.id.progress).setVisibility(View.VISIBLE);
    }

    @Override
    public void onDiscoveryFinished() {
        mView.findViewById(R.id.progress).setVisibility(View.INVISIBLE);
    }

    @Override
    public void onNoDevicesFound() {
    }

    @Override
    public void onDeviceFound(Device device) {
    }

    @Override
    public void onDiscoveryFailed() {
    }
}
