package com.tiagohm.bluedroid.app;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tiagohm.bluedroid.BlueDroid;
import com.tiagohm.bluedroid.ConnectionDevice;
import com.tiagohm.bluedroid.ConnectionSecure;
import com.tiagohm.bluedroid.Device;
import com.tiagohm.bluedroid.LineBreakType;

import java.nio.charset.Charset;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1234;

    private BlueDroid bt;
    //Armazena o texto recebido de um dispositivo bluetooth.
    private StringBuilder textoRecebido = new StringBuilder();

    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        bt = new BlueDroid( this, ConnectionDevice.OTHER, ConnectionSecure.SECURE );

        //Encerra a aplicação caso o dispositivo não oferça suporte a Bluetooth.
        if( !bt.isAvailable() )
        {
            finish();
            return;
        }

        bt.addDiscoveryListener( new BlueDroid.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted()
            {
                Toast.makeText( MainActivity.this, "Descoberta iniciada", Toast.LENGTH_SHORT ).show();
            }

            @Override
            public void onDiscoveryFinished()
            {
                Toast.makeText( MainActivity.this, "Descoberta finalizada", Toast.LENGTH_SHORT ).show();
            }

            @Override
            public void onNoDevicesFound()
            {
                Toast.makeText( MainActivity.this, "Nenhum dispositivo encontrado", Toast.LENGTH_SHORT ).show();
            }

            @Override
            public void onDeviceFound( Device device )
            {
                Toast.makeText( MainActivity.this, "Encontrado: " + device.getName(), Toast.LENGTH_SHORT ).show();
            }
        } );

        bt.addConnectionListener( new BlueDroid.ConnectionListener() {
            @Override
            public void onDeviceConnecting()
            {
                Toast.makeText( MainActivity.this, "Conectando...", Toast.LENGTH_SHORT ).show();
            }

            @Override
            public void onDeviceConnected()
            {
                Toast.makeText( MainActivity.this, "Conectado", Toast.LENGTH_SHORT ).show();
            }

            @Override
            public void onDeviceDisconnected()
            {
                Toast.makeText( MainActivity.this, "Desconectado", Toast.LENGTH_SHORT ).show();
            }

            @Override
            public void onDeviceConnectionFailed()
            {
                Toast.makeText( MainActivity.this, "Falha ao conectar", Toast.LENGTH_SHORT ).show();
            }
        } );

        bt.addDataReceivedListener( new BlueDroid.DataReceivedListener() {
            @Override
            public void onDataReceived( byte data )
            {
                textoRecebido.append( (char)data );
                ((TextView)findViewById( R.id.received_text )).setText( textoRecebido.toString() );
            }
        } );

        ((ListView)findViewById( R.id.device_list )).setAdapter( bt.getAdapter() );
        ((ListView)findViewById( R.id.device_list )).setOnItemClickListener( new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick( AdapterView<?> parent, View view, int position, long id )
            {
                if( !bt.isConnected() )
                {
                    Device device = (Device)view.getTag();
                    bt.connect( device );
                }
            }
        } );

        findViewById( R.id.btnProcurar ).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v )
            {
                bt.doDiscovery();
            }
        } );

        findViewById( R.id.btnDesconectar ).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v )
            {
                bt.disconnect();
            }
        } );

        findViewById( R.id.btnEnviar ).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v )
            {
                String text = ((EditText)findViewById( R.id.send_text )).getText().toString();
                if( text.length() > 0 )
                {
                    bt.send( text.getBytes( Charset.forName( "US-ASCII" ) ), LineBreakType.UNIX );
                }
            }
        } );
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        if( !bt.isEnabled() )
        {
            Toast.makeText( MainActivity.this, "Bluetooth desabilitado", Toast.LENGTH_SHORT ).show();
            Intent i = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
            startActivityForResult( i, REQUEST_ENABLE_BT );
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        bt.stop();
    }

    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data )
    {
        super.onActivityResult( requestCode, resultCode, data );
    }
}
