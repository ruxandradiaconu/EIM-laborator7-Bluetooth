package ro.pub.cs.systems.eim.bluetoothchatapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> chatArrayAdapter;
    private List<String> chatMessages;

    private BluetoothDevice selectedDevice;

    private EditText messageEditText;

    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    protected static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final int REQUEST_ENABLE_BT = 1;
    private final int REQUEST_PERMISSIONS = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // TODO 1: Initialize UI components
        // - Find views by ID: chatListView, messageEditText, sendButton, listDevicesButton
        // - Initialize chatMessages list and chatArrayAdapter
        // - Set adapter to chatListView

        ListView chatListView = findViewById(R.id.chatListView);
        messageEditText = findViewById(R.id.messageEditText);
        Button sendButton = findViewById(R.id.sendButton);
        Button listDevicesButton = findViewById(R.id.listDevicesButton);

        // init chatMessages and chatArrayAdapter
        chatMessages = new ArrayList<>();
        chatArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                chatMessages);

        // set adapter to chatListView
        chatListView.setAdapter(chatArrayAdapter);



        // TODO 2: Initialize Bluetooth adapter
        // - Get default Bluetooth adapter
        // - Check if Bluetooth is supported (if null, show toast and finish)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //check if is supported
        if (bluetoothAdapter == null) {
            // Dispozitivul nu suportă Bluetooth
            Toast.makeText(this, "Bluetooth nu este suportat pe acest dispozitiv",
                    Toast.LENGTH_SHORT).show();
            finish();
        }



        // TODO 3: Request necessary permissions
        // - Call requestPermissions() method
        requestPermissions();

        // TODO 4: Start server socket to listen for incoming connections
        // - Create and start AcceptThread
        acceptThread = new AcceptThread(this, bluetoothAdapter, MY_UUID);
        acceptThread.start();


        // TODO 5: Set up button listeners
        // - listDevicesButton should call listPairedDevices()
        // - sendButton should get message from EditText, send it via connectedThread,
        //   clear EditText, and add message to chat

        listDevicesButton.setOnClickListener(v -> listPairedDevices());
        sendButton.setOnClickListener(v -> {
            String message = messageEditText.getText().toString();
            if (!message.isEmpty() && connectedThread != null) {
                connectedThread.write(message.getBytes());
                messageEditText.setText("");
                addChatMessage(message);
            }
        });

    }

    public void addChatMessage(String message) {
        chatMessages.add(message);
        chatArrayAdapter.notifyDataSetChanged();
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]),
                REQUEST_PERMISSIONS);
    }

    // TODO 6: Implement the method that displays a dialog for selecting a paired device
    private void listPairedDevices() {
        // - Check BLUETOOTH_CONNECT permission
        // - Get bonded devices from bluetoothAdapter
        // - Create lists for device names and device objects
        // - Build AlertDialog with device list
        // - On item click: save selectedDevice and start ConnectThread
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // get bonded devices from bluetoothAdapter
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        List<String> deviceList = new ArrayList<>();
        final List<BluetoothDevice> devices = new ArrayList<>();

        if (!pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                deviceList.add(device.getName() + "\n" + device.getAddress());
                devices.add(device);
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Device");

        ArrayAdapter<String> deviceArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, deviceList);

        builder.setAdapter(deviceArrayAdapter, (dialog, which) -> {
            selectedDevice = devices.get(which);
            connectThread = new ConnectThread(this, bluetoothAdapter,
                    selectedDevice, MY_UUID);
            connectThread.start();
        });

        builder.show();

    }

    public void manageConnectedSocket(BluetoothSocket socket) {
        if (connectedThread != null) {
            connectedThread.cancel();
        }
        connectedThread = new ConnectedThread(this, socket);
        connectedThread.start();
    }

    // TODO 7: Handle runtime permission results
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // - Check if requestCode matches REQUEST_PERMISSIONS
        // - Verify all permissions were granted
        // - If not granted, show toast and finish
        // - If Bluetooth is not enabled, request to enable it via Intent
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean permissionGranted = true;

            // Verificăm dacă toate permisiunile au fost acordate
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    permissionGranted = false;
                    break;
                }
            }

            if (!permissionGranted) {
                Toast.makeText(this, "Permissions required for Bluetooth operation.", Toast.LENGTH_LONG).show();
                finish();
            }

            // Activăm Bluetooth dacă nu este deja activat
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    // TODO 8: Handle Bluetooth enable result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // - Check if requestCode is REQUEST_ENABLE_BT
        // - If result is not OK, show toast and finish
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode != RESULT_OK) {
            Toast.makeText(this, "Bluetooth must be enabled to continue.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    // TODO 9: Clean up threads on activity destroy
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // - Cancel acceptThread, connectThread, and connectedThread if they exist
        // Închidem AcceptThread dacă rulează
        if (acceptThread != null) acceptThread.cancel();

        // Închidem ConnectThread dacă rulează
        if (connectThread != null) connectThread.cancel();

        // Închidem ConnectedThread dacă rulează
        if (connectedThread != null) connectedThread.cancel();
    }
}
