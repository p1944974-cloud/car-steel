
package com.carsteel.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends Activity {

    private WebView webView;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private OutputStream outputStream;

    private static final int REQUEST_BLUETOOTH = 10;

    private final UUID SPP_UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());

        webView.addJavascriptInterface(this, "Android");

        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @JavascriptInterface
    public void connectBluetooth() {
        runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_BLUETOOTH
                );
                return;
            }

            new Thread(() -> connectToHC06()).start();
        });
    }

    private void connectToHC06() {
        try {
            if (bluetoothAdapter == null ||
                !bluetoothAdapter.isEnabled()) {
                updateStatus("ACTIVA EL BLUETOOTH");
                return;
            }

            BluetoothDevice device = null;

            for (BluetoothDevice paired :
                    bluetoothAdapter.getBondedDevices()) {
                if (paired.getName() != null &&
                    paired.getName().contains("HC-06")) {
                    device = paired;
                    break;
                }
            }

            if (device == null) {
                updateStatus("VINCULA EL HC-06 PRIMERO");
                return;
            }

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            outputStream = socket.getOutputStream();

            updateStatus("BLUETOOTH CONECTADO");

        } catch (Exception e) {
            updateStatus("ERROR DE CONEXION");
            closeConnection();
        }
    }

    @JavascriptInterface
    public void sendCommand(String command) {
        new Thread(() -> {
            try {
                if (outputStream != null) {
                    outputStream.write(command.getBytes());
                    outputStream.flush();
                }
            } catch (Exception e) {
                updateStatus("CONEXION PERDIDA");
                closeConnection();
            }
        }).start();
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> webView.evaluateJavascript(
            "document.getElementById('status').textContent='" +
            message + "'", null
        ));
    }

    private void closeConnection() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}

        socket = null;
        outputStream = null;
    }

    @Override
    protected void onDestroy() {
        closeConnection();
        super.onDestroy();
    }
}
