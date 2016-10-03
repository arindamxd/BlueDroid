/*
 * Copyright 2016 tiagohm
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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BlueService {

    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    private static final String TAG = "TAG";
    private static final String NAME_SECURE = "Bluetooth Secure";

    private static final UUID UUID_ANDROID_DEVICE =
            UUID.fromString( "fa87c0d0-afac-11de-8a39-0800200c9a66" );
    private static final UUID UUID_OTHER_DEVICE =
            UUID.fromString( "00001101-0000-1000-8000-00805F9B34FB" );

    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;

    private AcceptThread mSecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    private boolean isAndroid;
    private boolean isSecure = true;

    public BlueService( Handler handler ) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    public synchronized int getState() {
        Log.d( "TAG", "BlueService.getState()=" + mState );
        return mState;
    }

    private synchronized void setState( int state ) {
        Log.d( "TAG", "BlueService.setState(" + state + ")" );
        mState = state;
        mHandler.obtainMessage( MESSAGE_STATE_CHANGE, state, -1 ).sendToTarget();
    }

    public synchronized void start( boolean android, boolean secure ) {
        Log.d( "TAG", "BlueService.start(" + android + ", " + secure + ")" );
        isAndroid = android;
        isSecure = secure;

        if( mConnectThread != null ) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if( mConnectedThread != null ) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState( STATE_LISTEN );

        if( mSecureAcceptThread == null ) {
            mSecureAcceptThread = new AcceptThread( isAndroid, isSecure );
            mSecureAcceptThread.start();
        }
    }

    public synchronized void connect( BluetoothDevice device ) {
        Log.d( "TAG", "BlueService.connect()" );
        if( mState == STATE_CONNECTING ) {
            if( mConnectThread != null ) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        if( mConnectedThread != null ) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectThread = new ConnectThread( device );
        mConnectThread.start();
        setState( STATE_CONNECTING );
    }

    public synchronized void connected( BluetoothSocket socket, BluetoothDevice device, final String socketType ) {
        Log.d( "TAG", "BlueService.connected()" );
        if( mConnectThread != null ) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if( mConnectedThread != null ) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if( mSecureAcceptThread != null ) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        mConnectedThread = new ConnectedThread( socket, socketType );
        mConnectedThread.start();

        Message msg = mHandler.obtainMessage( MESSAGE_DEVICE_NAME );
        mHandler.sendMessage( msg );

        setState( STATE_CONNECTED );
    }

    public synchronized void stop() {
        Log.d( "TAG", "BlueService.stop()" );
        if( mConnectThread != null ) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if( mConnectedThread != null ) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if( mSecureAcceptThread != null ) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread.kill();
            mSecureAcceptThread = null;
        }
        setState( STATE_NONE );
    }

    public void write( int b ) {
        ConnectedThread r;
        synchronized( this ) {
            if( mState != STATE_CONNECTED ) return;
            r = mConnectedThread;
        }
        r.write( b );
    }

    public void write( byte[] out ) {
        ConnectedThread r;
        synchronized( this ) {
            if( mState != STATE_CONNECTED ) return;
            r = mConnectedThread;
        }
        r.write( out );
    }

    public void write( byte[] out, int off, int len ) {
        ConnectedThread r;
        synchronized( this ) {
            if( mState != STATE_CONNECTED ) return;
            r = mConnectedThread;
        }
        r.write( out, off, len );
    }

    private void connectionFailed() {
        Log.d( "TAG", "BlueService.connectionFailed()" );
        BlueService.this.start( isAndroid, isSecure );
    }

    private void connectionLost() {
        Log.d( "TAG", "BlueService.connectionLost()" );
        BlueService.this.start( isAndroid, isSecure );
    }

    private class AcceptThread extends Thread {
        boolean isRunning = true;
        private BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread( boolean isAndroid, boolean secure ) {
            BluetoothServerSocket tmp = null;

            try {
                if( secure ) {
                    if( isAndroid )
                        tmp = mAdapter.listenUsingRfcommWithServiceRecord( NAME_SECURE, UUID_ANDROID_DEVICE );
                    else
                        tmp = mAdapter.listenUsingRfcommWithServiceRecord( NAME_SECURE, UUID_OTHER_DEVICE );
                } else {
                    if( isAndroid )
                        tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord( NAME_SECURE, UUID_ANDROID_DEVICE );
                    else
                        tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord( NAME_SECURE, UUID_OTHER_DEVICE );
                }
            } catch( IOException e ) {
                e.printStackTrace();
            }

            mmServerSocket = tmp;
        }

        public void run() {
            Log.d( TAG, "BlueService$AcceptThread.run()" );
            setName( "AcceptThread" + mSocketType );
            BluetoothSocket socket;

            while( mState != STATE_CONNECTED && isRunning ) {
                try {
                    socket = mmServerSocket.accept();
                } catch( Exception e ) {
                    break;
                }

                //Se uma conexão foi aceita.
                if( socket != null ) {
                    synchronized( BlueService.this ) {
                        switch( mState ) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                connected( socket, socket.getRemoteDevice(), mSocketType );
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                try {
                                    socket.close();
                                } catch( IOException e ) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                }
            }
        }

        public void cancel() {
            Log.d( TAG, "BlueService$AcceptThread.cancel()" );
            try {
                mmServerSocket.close();
                mmServerSocket = null;
            } catch( Exception e ) {
                e.printStackTrace();
            }
        }

        public void kill() {
            Log.d( TAG, "BlueService$AcceptThread.kill()" );
            isRunning = false;
        }
    }


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread( BluetoothDevice device ) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            try {
                if( isSecure ) {
                    if( isAndroid ) {
                        tmp = device.createRfcommSocketToServiceRecord( UUID_ANDROID_DEVICE );
                    } else {
                        tmp = device.createRfcommSocketToServiceRecord( UUID_OTHER_DEVICE );
                    }
                } else {
                    if( isAndroid ) {
                        tmp = device.createInsecureRfcommSocketToServiceRecord( UUID_ANDROID_DEVICE );
                    } else {
                        tmp = device.createInsecureRfcommSocketToServiceRecord( UUID_OTHER_DEVICE );
                    }
                }
            } catch( IOException e ) {
                e.printStackTrace();
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.d( TAG, "BlueService$ConnectThread.run()" );
            mAdapter.cancelDiscovery();

            try {
                mmSocket.connect();
            } catch( IOException e ) {
                try {
                    mmSocket.close();
                } catch( IOException e2 ) {
                    e2.printStackTrace();
                }
                connectionFailed();
                return;
            }

            synchronized( BlueService.this ) {
                mConnectThread = null;
            }

            connected( mmSocket, mmDevice, mSocketType );
        }

        public void cancel() {
            Log.d( TAG, "BlueService$ConnectThread.cancel()" );
            try {
                mmSocket.close();
            } catch( IOException e ) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread( BluetoothSocket socket, String socketType ) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch( IOException e ) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.d( TAG, "BlueService$ConnectedThread.run()" );
            while( true ) {
                try {
                    int data = mmInStream.read();
                    mHandler.obtainMessage( MESSAGE_READ, data ).sendToTarget();
                } catch( IOException e ) {
                    connectionLost();
                    BlueService.this.start( isAndroid, isSecure );
                    break;
                }
            }
        }

        public void write( int b ) {
            try {
                mmOutStream.write( b );
                mHandler.obtainMessage( MESSAGE_WRITE, -1, -1, b ).sendToTarget();
            } catch( IOException e ) {
                e.printStackTrace();
            }
        }

        public void write( byte[] buffer ) {
            write( buffer, 0, buffer.length );
        }

        public void write( byte[] buffer, int offset, int length ) {
            try {
                mmOutStream.write( buffer, offset, length );
                mHandler.obtainMessage( MESSAGE_WRITE, -1, -1, buffer ).sendToTarget();
            } catch( IOException e ) {
                e.printStackTrace();
            }
        }

        public void cancel() {
            Log.d( TAG, "BlueService$ConnectedThread.cancel()" );
            try {
                mmSocket.close();
            } catch( IOException e ) {
                e.printStackTrace();
            }
        }
    }
}
