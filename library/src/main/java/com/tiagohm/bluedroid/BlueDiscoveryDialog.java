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
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import br.tiagohm.easyadapter.EasyAdapter;
import br.tiagohm.easyadapter.EasyInjector;
import br.tiagohm.easyadapter.Injector;

public class BlueDiscoveryDialog extends MaterialDialog.Builder implements BlueDroid.ConnectionListener, BlueDroid.DiscoveryListener {

    private final BlueDroid blueDroid;
    private final EasyAdapter adapter = EasyAdapter.create();
    private final ProgressBar progressoDoEscaneamento;
    private final ImageView botaoPararEscaneamento;

    public BlueDiscoveryDialog(@NonNull final Activity context, @NonNull BlueDroid bt) {
        super(context);
        //BlueDroid
        blueDroid = bt;
        blueDroid.addConnectionListener(this);
        blueDroid.addDiscoveryListener(this);
        //Layout do Dialog.
        title(R.string.dialog_bluetooth_discovery_title);
        customView(R.layout.dialog_bluetooth_discovery, true);
        positiveText(android.R.string.ok);
        neutralText(R.string.dialog_bluetooth_discovery_scan);
        autoDismiss(false);
        //Eventos
        onNeutral(new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                blueDroid.doDiscovery(context);
            }
        });
        onPositive(new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                dialog.dismiss();
            }
        });
        dismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                blueDroid.cancelDiscovery();
                blueDroid.removeDiscoveryListener(BlueDiscoveryDialog.this);
                blueDroid.removeConnectionListener(BlueDiscoveryDialog.this);
                dialogInterface.dismiss();
            }
        });

        progressoDoEscaneamento = customView.findViewById(R.id.progressoDoEscaneamento);

        botaoPararEscaneamento = customView.findViewById(R.id.botaoPararEscaneamento);
        botaoPararEscaneamento.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                blueDroid.cancelDiscovery();
            }
        });

        RecyclerView listaDeDispositivos = customView.findViewById(R.id.listaDeDispositivos);
        listaDeDispositivos.setLayoutManager(new LinearLayoutManager(context));
        //Caso esteja conectado, exibi-lo.
        if (bt.getCurrentDevice() != null) {
            adapter.addData(bt.getCurrentDevice());
        }
        //Registrar layout quando estiver vazio.
        adapter.registerEmpty(R.layout.dialog_bluetooth_discovery_empty, null);
        //Registrar layout para um dispositivo.
        adapter.register(Device.class, R.layout.device_item, new EasyInjector<Device>() {
            @Override
            public void onInject(final Device device, Injector injector) {
                injector.text(R.id.bt_device_address, device.getAddress());
                injector.text(R.id.bt_device_name, device.getName());
                injector.image(R.id.bt_device_icon, device.getDeviceClassIcon());
                injector.visibility(R.id.bt_device_disconnect,
                        device.equals(blueDroid.getCurrentDevice()) ? View.VISIBLE : View.GONE);
                injector.onClick(0, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (!blueDroid.isConnected()) {
                            blueDroid.connect(device);
                        }
                    }
                });
                injector.onClick(R.id.bt_device_disconnect, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        blueDroid.disconnect();
                    }
                });
            }
        });

        adapter.attachTo(listaDeDispositivos);
    }

    @Override
    public void onDeviceConnecting() {
    }

    @Override
    public void onDeviceConnected() {
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDeviceDisconnected() {
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDeviceConnectionFailed() {
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDiscoveryStarted() {
        //Remove tudo.
        adapter.clear();
        //Caso esteja conectado, exibi-lo.
        Log.d("TAG", String.format("%s", blueDroid.getCurrentDevice()));
        if (blueDroid.getCurrentDevice() != null) {
            adapter.addData(blueDroid.getCurrentDevice());
        }
        //Exibe a barra de status do progresso.
        progressoDoEscaneamento.setVisibility(View.VISIBLE);
        botaoPararEscaneamento.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDiscoveryFinished() {
        progressoDoEscaneamento.setVisibility(View.INVISIBLE);
        botaoPararEscaneamento.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onNoDevicesFound() {
    }

    @Override
    public void onDeviceFound(final Device device) {
        adapter.addData(device);
    }

    @Override
    public void onDiscoveryFailed() {
    }
}
