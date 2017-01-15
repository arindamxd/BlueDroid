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

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class BlueDroid
{
    public static final int REQUEST_COARSE_LOCATION_PERMISSIONS = 0x00BB;

    private static final String TAG = "TAG";
    private final Context mContext;
    private final BlueDroidAdapter mAdapter = new BlueDroidAdapter();
    private final ConnectionDevice mConnectionDevice;
    private final ConnectionSecure mConnectionSecure;
    private final List<Device> mDevices = new ArrayList<>(32);
    private final List<DiscoveryListener> discoveryListener = new ArrayList<>();
    private final List<DataReceivedListener> dataReceivedListener = new ArrayList<>();
    private final List<ConnectionListener> connectionListener = new ArrayList<>();

    private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.d(TAG, "BlueDroid.mReceiver.onReceive()");
            String action = intent.getAction();

            if(BluetoothDevice.ACTION_FOUND.equals(action))
            {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Device newDevice = new Device(device.getName(), device.getAddress(), false);
                mDevices.add(newDevice);
                mAdapter.notifyDataSetChanged();
                fireOnDeviceFound(newDevice);
            }
            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
            {
                mContext.unregisterReceiver(mReceiver);
                fireOnDiscoveryFinished();
                if(mDevices.size() == 0)
                {
                    fireOnNoDevicesFound();
                }
            }
        }
    };

    private BluetoothAdapter mBtAdapter;
    private BlueService mBtService;
    private Device mCurrentDevice;
    private boolean isServiceRunning = false;
    private boolean isConnecting = false;
    private boolean isConnected = false;

    private final Handler mHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            Log.d(TAG, "BlueDroid.mHandler.handleMessage(" + msg.what + ")");
            switch(msg.what)
            {
                case BlueService.MESSAGE_WRITE:
                    break;
                case BlueService.MESSAGE_READ:
                    byte data = (byte)(int)msg.obj;
                    fireOnDataReceived(data);
                    break;
                case BlueService.MESSAGE_DEVICE_NAME:
                    fireOnDeviceConnected();
                    isConnected = true;
                    break;
                case BlueService.MESSAGE_STATE_CHANGE:
                    if(isConnected && msg.arg1 != BlueService.STATE_CONNECTED)
                    {
                        isConnected = false;
                        fireOnDeviceDisconnected();
                        mCurrentDevice = null;
                    }
                    if(!isConnecting && msg.arg1 == BlueService.STATE_CONNECTING)
                    {
                        isConnecting = true;
                        fireOnDeviceConnecting();
                    }
                    else if(isConnecting)
                    {
                        isConnecting = false;
                        if(msg.arg1 != BlueService.STATE_CONNECTED)
                        {
                            fireOnDeviceConnectionFailed();
                            mCurrentDevice = null;
                        }
                    }
                    break;
            }
        }
    };

    /**
     * Cria uma intância da classe BlueDroid.
     */
    public BlueDroid(Context context, ConnectionDevice device, ConnectionSecure type)
    {
        mContext = context;
        mConnectionDevice = device;
        mConnectionSecure = type;
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Obtém o adapter da lista de dispositivos Bluetooth encontrados.
     */
    public BaseAdapter getAdapter()
    {
        return mAdapter;
    }

    /**
     * Obtém a lista de dispositivos Bluetooth encontrados.
     */
    public List<Device> getDevices()
    {
        return mDevices;
    }

    /**
     * Verifica se o adaptador Bluetooth está disponível.
     */
    public boolean isAvailable()
    {
        Log.d(TAG, "BlueDroid.isAvailable()");
        try
        {
            return mBtAdapter != null;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Obtém o dispositivo atualmente conectado.
     */
    public Device getCurrentDevice()
    {
        return mCurrentDevice;
    }

    /**
     * Verifica se o serviço está disponível.
     */
    public boolean isServiceAvailable()
    {
        Log.d(TAG, "BlueDroid.isServiceAvailable()");
        return mBtService != null;
    }

    /**
     * Verifica se o adaptador Bluetooth está ativado.
     */
    public boolean isEnabled()
    {
        Log.d(TAG, "BlueDroid.isEnabled()");
        Log.d(TAG, mBtAdapter.getAddress());
        return mBtAdapter.getAddress() != null && mBtAdapter.isEnabled();
    }

    /**
     * Verifica se o serviço está rodando.
     */
    public boolean isServiceRunning()
    {
        return isServiceRunning;
    }

    /**
     * Inicia a descoberta de dispositivos Bluetooth.
     */
    public boolean startDiscovery()
    {
        Log.d(TAG, "BlueDroid.startDiscovery()");
        return mBtAdapter.startDiscovery();
    }

    /**
     * Verifica se a descoberta de dispositivos está sendo executada.
     */
    public boolean isDiscovering()
    {
        Log.d(TAG, "BlueDroid.isDiscovering()");
        return mBtAdapter.isDiscovering();
    }

    /**
     * Cancela a descoberta de dispositivos Bluetooth.
     */
    public boolean cancelDiscovery()
    {
        Log.d(TAG, "BlueDroid.cancelDiscovery()");
        return mBtAdapter.cancelDiscovery();
    }

    /**
     * Obtém o adaptador Bluetooth.
     */
    public BluetoothAdapter getBluetoothAdapter()
    {
        Log.d(TAG, "BlueDroid.getBluetoothAdapter()");
        return mBtAdapter;
    }

    private void setupService()
    {
        Log.d(TAG, "BlueDroid.setupService()");
        mBtService = new BlueService(mHandler);
    }

    private void startService()
    {
        Log.d(TAG, "BlueDroid.startService()");
        if(isServiceAvailable())
        {
            if(mBtService.getState() == BlueService.STATE_NONE)
            {
                isServiceRunning = true;
                mBtService.start(mConnectionDevice == ConnectionDevice.ANDROID,
                        mConnectionSecure == ConnectionSecure.SECURE);
            }
        }
    }

    /**
     * Encerra a conexão.
     */
    public void stop()
    {
        Log.d(TAG, "BlueDroid.stop()");
        mCurrentDevice = null;
        if(isServiceAvailable())
        {
            isServiceRunning = false;
            mBtService.stop();
        }

        new Handler().postDelayed(new Runnable()
        {
            public void run()
            {
                if(isServiceAvailable())
                {
                    isServiceRunning = false;
                    mBtService.stop();
                }
            }
        }, 500);
    }

    /**
     * Conecta a um dispositivo Bluetooth.
     */
    public void connect(Device device)
    {
        if(device != null)
        {
            mCurrentDevice = device;
            connect(device.getAddress());
        }
    }

    private void connect(String address)
    {
        Log.d(TAG, "BlueDroid.connect(" + address + ")");
        if(isConnecting)
        {
            return;
        }
        if(!isServiceAvailable())
        {
            setupService();
        }

        startService();

        if(BluetoothAdapter.checkBluetoothAddress(address))
        {
            BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
            mBtService.connect(device);
        }
    }

    /**
     * Verifica se está conectado a um dispositivo.
     */
    public boolean isConnected()
    {
        Log.d(TAG, "BlueDroid.isConnected()");
        return isConnected && mConnectionDevice != null;
    }

    /**
     * Verifica se está conectando a um dispositivo.
     */
    public boolean isConnecting()
    {
        Log.d(TAG, "BlueDroid.isConnecting()");
        return isConnecting;
    }

    /**
     * Habilia o adaptador Bluetooth.
     */
    public void enable()
    {
        Log.d(TAG, "BlueDroid.enable()");
        mBtAdapter.enable();
    }

    /**
     * Desconecta com o dispositivo Bluetooth conectado.
     */
    public void disconnect()
    {
        Log.d(TAG, "BlueDroid.disconnect()");
        mCurrentDevice = null;

        if(isServiceAvailable())
        {
            isServiceRunning = false;
            mBtService.stop();
            if(mBtService.getState() == BlueService.STATE_NONE)
            {
                isServiceRunning = true;
                mBtService.start(mConnectionDevice == ConnectionDevice.ANDROID,
                        mConnectionSecure == ConnectionSecure.SECURE);
            }
        }
    }

    /**
     * Obtém o estado atual da conexão.
     */
    public int getState()
    {
        return mBtService.getState();
    }

    /**
     * Envia dados para um dispositivo Bluetooth.
     */
    public void send(byte[] data, LineBreakType lbt)
    {
        send(data, 0, data.length, lbt);
    }

    /**
     * Envia dados para um dispositivo Bluetooth.
     */
    public void send(byte[] data, int off, int len, LineBreakType lbt)
    {
        if(lbt.value == LineBreakType.NONE.value)
        {
            send(data, off, len);
        }
        else
        {
            byte[] tmp = new byte[len + 2];
            System.arraycopy(data, off, tmp, 0, len);
            tmp[tmp.length - 1] = 0x0D; // CR
            tmp[tmp.length - 2] = 0x0A; // LF
            if(lbt.value == LineBreakType.LF.value)
            {
                tmp[tmp.length - 1] = 0x0A; // LF
                send(tmp, 0, len + 1);
            }
            else if(lbt.value == LineBreakType.CR.value)
            {
                send(tmp, 0, len + 1);
            }
            else if(lbt.value == LineBreakType.CRLF.value)
            {
                send(tmp);
            }
        }
    }

    /**
     * Envia dados para um dispositivo Bluetooth.
     */
    public void send(byte[] data, int off, int len)
    {
        mBtService.write(data, off, len);
    }

    /**
     * Envia dados para um dispositivo Bluetooth.
     */
    public void send(byte[] data)
    {
        mBtService.write(data);
    }

    /**
     * Envia um simples byte para um dispositivo Bluetooth.
     */
    public void send(int b)
    {
        mBtService.write(b);
    }

    public void checkDiscoveryPermissionRequest(int requestCode, String permissions[], int[] grantResults)
    {
        switch(requestCode)
        {
            case REQUEST_COARSE_LOCATION_PERMISSIONS:
            {
                if(grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    doDiscovery(null);
                }
                else
                {
                    fireOnDiscoveryFailed();
                }

                return;
            }
        }
    }

    /**
     * Executa a descoberta de dispositivos Bluetooth.
     */
    public void doDiscovery(Activity activity)
    {
        Log.d(TAG, "BlueDroid.doDiscovery()");

        final int hasPermission = ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION);

        if(hasPermission != PackageManager.PERMISSION_GRANTED)
        {
            if(activity != null)
            {
                ActivityCompat.requestPermissions(activity,
                        new String[]{
                                android.Manifest.permission.ACCESS_COARSE_LOCATION },
                        REQUEST_COARSE_LOCATION_PERMISSIONS);
            }

            return;
        }

        mCurrentDevice = null;
        mDevices.clear();
        mAdapter.notifyDataSetChanged();

        fireOnDiscoveryStarted();

        if(isDiscovering())
        {
            mContext.unregisterReceiver(mReceiver);
            cancelDiscovery();
        }

        mContext.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        mContext.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

        startDiscovery();
    }

    protected void fireOnDiscoveryStarted()
    {
        for(DiscoveryListener listener : discoveryListener) listener.onDiscoveryStarted();
    }

    protected void fireOnDiscoveryFinished()
    {
        for(DiscoveryListener listener : discoveryListener) listener.onDiscoveryFinished();
    }

    protected void fireOnNoDevicesFound()
    {
        for(DiscoveryListener listener : discoveryListener) listener.onNoDevicesFound();
    }

    protected void fireOnDeviceFound(Device dev)
    {
        for(DiscoveryListener listener : discoveryListener) listener.onDeviceFound(dev);
    }

    protected void fireOnDiscoveryFailed()
    {
        for(DiscoveryListener listener : discoveryListener) listener.onDiscoveryFailed();
    }

    public void addDiscoveryListener(DiscoveryListener discoveryListener)
    {
        this.discoveryListener.add(discoveryListener);
    }

    public void removeDiscoveryListener(DiscoveryListener discoveryListener)
    {
        this.discoveryListener.remove(discoveryListener);
    }

    public void clearDiscoveryListener()
    {
        this.discoveryListener.clear();
    }

    protected void fireOnDataReceived(byte data)
    {
        for(DataReceivedListener listener : dataReceivedListener) listener.onDataReceived(data);
    }

    public void addDataReceivedListener(DataReceivedListener dataReceivedListener)
    {
        this.dataReceivedListener.add(dataReceivedListener);
    }

    public void removeDataReceivedListener(DataReceivedListener dataReceivedListener)
    {
        this.dataReceivedListener.remove(dataReceivedListener);
    }

    public void clearDataReceivedListener()
    {
        this.dataReceivedListener.clear();
    }

    protected void fireOnDeviceConnecting()
    {
        for(ConnectionListener listener : connectionListener) listener.onDeviceConnecting();
    }

    protected void fireOnDeviceConnected()
    {
        for(ConnectionListener listener : connectionListener) listener.onDeviceConnected();
    }

    protected void fireOnDeviceDisconnected()
    {
        for(ConnectionListener listener : connectionListener) listener.onDeviceDisconnected();
    }

    protected void fireOnDeviceConnectionFailed()
    {
        for(ConnectionListener listener : connectionListener) listener.onDeviceConnectionFailed();
    }

    public void addConnectionListener(ConnectionListener connectionListener)
    {
        this.connectionListener.add(connectionListener);
    }

    public void removeConnectionListener(ConnectionListener connectionListener)
    {
        this.connectionListener.remove(connectionListener);
    }

    public void clearConnectionListener()
    {
        this.connectionListener.clear();
    }

    public interface DiscoveryListener
    {
        void onDiscoveryStarted();

        void onDiscoveryFinished();

        void onNoDevicesFound();

        void onDeviceFound(Device device);

        void onDiscoveryFailed();
    }

    public interface DataReceivedListener
    {
        void onDataReceived(byte data);
    }

    public interface ConnectionListener
    {
        void onDeviceConnecting();

        void onDeviceConnected();

        void onDeviceDisconnected();

        void onDeviceConnectionFailed();
    }

    private class BlueDroidAdapter extends BaseAdapter
    {
        @Override
        public int getCount()
        {
            return getDevices().size();
        }

        @Override
        public Object getItem(int position)
        {
            return getDevices().get(position);
        }

        @Override
        public long getItemId(int position)
        {
            return 0;
        }

        @Override
        public View getView(int position, View v, ViewGroup parent)
        {
            if(v == null)
            {
                v = LayoutInflater.from(mContext).inflate(R.layout.device_item, parent, false);
            }

            Device device = getDevices().get(position);

            v.setTag(device);
            ((TextView)v.findViewById(R.id.bt_device_name)).setText(device.getName());
            ((TextView)v.findViewById(R.id.bt_device_address)).setText(device.getAddress());

            return v;
        }
    }
}
