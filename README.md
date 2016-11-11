# BlueDroid

[![](https://jitpack.io/v/tiagohm/BlueDroid.svg)](https://jitpack.io/#tiagohm/BlueDroid)

## USO

Adicione ao seu projeto:
```gradle
	allprojects {
		repositories {
			...
			maven { url "https://jitpack.io" }
		}
	}
```
```gradle
	dependencies {
	        compile 'com.github.tiagohm:BlueDroid:0.1.2'
	}
```

Adicione as permissões:
```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
```

Declare BlueDroid:
```java
BlueDroid bt = new BlueDroid(Context, ConnectionDevice, ConnectionSecure);
```

Verificar se Bluetooth está disponível:
```java
if(!bt.isAvailable()) {
  finish();
  ...
}
```

Verificar se está ativado e ativá-lo se necessário:
```java
@Override
public void onStart() {
    super.onStart();
    if(!bt.isEnabled()) {
      Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(i, REQUEST_ENABLE_BT);
    } else {
      ...
    }
}
```

Encerrá-lo ao fechar o aplicativo:
```java
@Override
  protected void onDestroy() {
    super.onDestroy();
    bt.stop();
  }
```

Procurar por dispositivos:
```java
bt.doDiscovery();
```

Exibir os dispositivos encontrados numa lista e conectar ao clicar em um item:
```java
((ListView)findViewById(R.id.device_list)).setAdapter(bt.getAdapter());
((ListView)findViewById(R.id.device_list)).setOnItemClickListener(new AdapterView.OnItemClickListener() {
  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    if(!bt.isConnected()) {
      Device device = (Device)view.getTag();
      bt.connect(device);
    }
  }
} );
```

Para desconectar:
```java
bt.disconnect();
```

Para enviar:
```java
String texto = "123456";
bt.send(texto.getBytes( Charset.forName("US-ASCII")), LineBreakType.UNIX);
```

Receber dados:
```java
bt.setDataReceivedListener(new BlueDroid.DataReceivedListener() {
  @Override
  public void onDataReceived(byte data) {
    ...
  }
} );
```

## LICENÇA
Copyright 2016 tiagohm

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
