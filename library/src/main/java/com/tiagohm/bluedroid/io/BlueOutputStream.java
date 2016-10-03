package com.tiagohm.bluedroid.io;

import android.support.annotation.NonNull;

import com.tiagohm.bluedroid.BlueDroid;

import java.io.IOException;
import java.io.OutputStream;

public class BlueOutputStream extends OutputStream {

    private final BlueDroid bt;

    public BlueOutputStream( BlueDroid bt ) {
        this.bt = bt;
    }

    @Override
    public void write( @NonNull byte[] b, int off, int len ) throws IOException {
        bt.send( b, off, len );
    }

    @Override
    public void write( int b ) throws IOException {
        bt.send( b );
    }
}
