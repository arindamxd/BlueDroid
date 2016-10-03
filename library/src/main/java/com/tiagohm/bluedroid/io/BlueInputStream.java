package com.tiagohm.bluedroid.io;

import com.tiagohm.bluedroid.BlueDroid;

import java.io.IOException;
import java.io.InputStream;

public class BlueInputStream extends InputStream {

    private final BlueDroid bt;
    private final byte[] mBuffer;
    private int start = 0, end = 0;
    private int size = 0;
    private volatile boolean closed = false;

    private final BlueDroid.DataReceivedListener onDataReceived = new BlueDroid.DataReceivedListener() {
        @Override
        public void onDataReceived( byte data ) {
            if( closed ) {
                return;
            }

            if( size >= mBuffer.length ) {
                return;
            }

            if( end >= mBuffer.length ) {
                end = 0;
            }

            mBuffer[end++] = data;
            size++;
        }
    };

    public BlueInputStream( BlueDroid bt ) {
        this( bt, 1024 );
    }

    public BlueInputStream( BlueDroid bt, int capacity ) {
        this.bt = bt;
        mBuffer = new byte[capacity];
        bt.addDataReceivedListener( onDataReceived );
    }

    @Override
    public synchronized int available() {
        return size;
    }

    @Override
    public long skip( long n ) {
        throw new UnsupportedOperationException( "skip" );
    }

    @Override
    public int read() throws IOException {
        if( closed ) {
            throw new IOException( "closed" );
        }

        if( size <= 0 ) {
            return -1;
        }

        if( start >= mBuffer.length ) {
            start = 0;
        }

        size--;
        return mBuffer[start++];
    }

    @Override
    public void close() throws IOException {
        closed = true;
        size = start = end = 0;
        bt.removeDataReceivedListener( onDataReceived );
    }
}
