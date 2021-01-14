package com.example.wifi4;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION = 1001;
    private static final int DISCOVERY_PORT = 8888;
    DatagramSocket socket = null;
    private static final String TAG = "wifi, MainActivity";
    private String SERVER_IP;
    private final Map<String, String> devices = new HashMap<>();
    IntentFilter intentFilter = new IntentFilter();
    private String localIp;
    private static final String MESSAGE = "MESSAGE";
    private static final String ACCELEROMETER = "ACCELEROMETER";
    private static final String GYROSCOPE = "GYROSCOPE";
    private String connectThreadMethodType;

    private ScheduledExecutorService broadcastThread;
    private ExecutorService scanThread;

    @BindView(R.id.deviceIp)
    TextView ipView;

    @BindView(R.id.scanInfo)
    TextView infoView;

    @BindView(R.id.deviceListView)
    ListView deviceListView;

    private String getLocalIpAddress() throws UnknownHostException {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipInt = wifiInfo.getIpAddress();
        return InetAddress.getByAddress(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()).getHostAddress();
    }

    InetAddress getBroadcastAddress() throws IOException {
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = wifi.getDhcpInfo();
        // handle null somehow

        int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++)
            quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
        return InetAddress.getByAddress(quads);
    }

    private boolean init() {
        // Device capability definition check
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            Toast.makeText(this, "Wi-Fi is not supported by this device.", Toast.LENGTH_SHORT).show();
            return false;
        }
        // Hardware capability check
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Toast.makeText(this, "Cannot get Wi-Fi system service.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!wifiManager.isWifiEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Toast.makeText(this, "Please turn on WiFi", Toast.LENGTH_LONG).show();
                Intent panelIntent = new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
                startActivityForResult(panelIntent, 545);
            } else {
                wifiManager.setWifiEnabled(true);
            }
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

        if (!init()) {
            finish();
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION);
        }

        try {
            socket = new DatagramSocket(DISCOVERY_PORT);
            socket.setBroadcast(true);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        try {
            localIp = getLocalIpAddress();
            String str = "Device IP: " + localIp;
            ipView.setText(str);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        broadcastThread = Executors.newSingleThreadScheduledExecutor();
        broadcastThread.scheduleAtFixedRate(this::broadcastThreadMethod, 0, 1, TimeUnit.SECONDS);
        scanThread = Executors.newSingleThreadExecutor();
        scanThread.submit(this::scanThreadMethod);
    }

    private void broadcastThreadMethod() {
        if (socket == null || socket.isClosed()) {
            try {
                socket = new DatagramSocket(DISCOVERY_PORT);
                socket.setBroadcast(true);
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
        byte[] sendData = (Build.BRAND + ' ' + Build.MODEL).getBytes();
        try {
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, getBroadcastAddress(), DISCOVERY_PORT);
            socket.send(sendPacket);
            Log.d(TAG, "Broadcast packet sent to: " + getBroadcastAddress().getHostAddress());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void scanThreadMethod() {
        if (socket == null || socket.isClosed()) {
            try {
                socket = new DatagramSocket(DISCOVERY_PORT);
                socket.setBroadcast(true);
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        long start = System.currentTimeMillis();
        byte[] buf = new byte[1024];

        try {
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String s = new String(packet.getData(), 0, packet.getLength());
                Log.d(TAG, "Packet received after "
                        + (System.currentTimeMillis() - start) + " " + s);

                //Packet received
                Log.i(TAG, "Packet received from: " + packet.getAddress().getHostAddress());
                String data = new String(packet.getData()).trim();
                if (data.contains(MESSAGE)) {
                    Intent intent = new Intent(this, SendMessage.class);
                    intent.putExtra("Device1_IP", localIp);
                    intent.putExtra("Device2_IP", packet.getAddress().getHostAddress());
                    intent.putExtra("Device_Type", "CLIENT");
                    startActivity(intent);
                    continue;
                } else if (data.contains(ACCELEROMETER)) {
                    Intent intent = new Intent(this, SendAccelerometerClient.class);
                    intent.putExtra("Device1_IP", localIp);
                    intent.putExtra("Device2_IP", packet.getAddress().getHostAddress());
                    intent.putExtra("Device_Type", "CLIENT");
                    startActivity(intent);
                    continue;
                } else if (data.contains(GYROSCOPE)) {
                    Intent intent = new Intent(this, SendGyroscopeClient.class);
                    intent.putExtra("Device1_IP", localIp);
                    intent.putExtra("Device2_IP", packet.getAddress().getHostAddress());
                    intent.putExtra("Device_Type", "CLIENT");
                    startActivity(intent);
                    continue;
                }
                Log.i(TAG, "Packet received; data: " + data);
                runOnUiThread(() -> {
                    devices.put(packet.getAddress().getHostAddress(), data);
                    ArrayList<String> adapterData = new ArrayList<>();
                    ArrayList<String> deviceIp = new ArrayList<>();

                    String str;
                    if (devices.size() > 1) {
                        str = "Found devices!";
                    } else {
                        str = "Looking for devices...";
                    }
                    infoView.setText(str);

                    for (String ip : devices.keySet()) {
                        if (!ip.equals(localIp)) {
                            adapterData.add(ip + "      " + devices.get(ip));
                            deviceIp.add(ip);
                        }
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.row, adapterData);
                    deviceListView.setAdapter(adapter);

                    deviceListView.setOnItemClickListener((parent, view, position, id) -> {
                        SERVER_IP = deviceIp.get(position);
                        //Creating the instance of PopupMenu
                        PopupMenu popup = new PopupMenu(MainActivity.this, deviceListView.getChildAt(position));
                        //Inflating the Popup using xml file
                        popup.getMenuInflater().inflate(R.menu.poupup_menu, popup.getMenu());
                        popup.setOnMenuItemClickListener((item) -> {
                            SensorManager sensorManager;
                            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                            if (item.getTitle().equals(getResources().getString(R.string.send_message))) {
                                connectThreadMethodType = MESSAGE;
                                Executors.newSingleThreadExecutor().submit(this::connectThreadMethod);
                                Intent intent = new Intent(this, SendMessage.class);
                                intent.putExtra("Device1_IP", localIp);
                                intent.putExtra("Device2_IP", SERVER_IP);
                                intent.putExtra("Device_Type", "SERVER");
                                startActivity(intent);
                            } else if (item.getTitle().equals(getResources().getString(R.string.send_accelerometer_data))) {
                                if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
                                    connectThreadMethodType = ACCELEROMETER;
                                    Executors.newSingleThreadExecutor().submit(this::connectThreadMethod);
                                    Intent intent = new Intent(this, SendAccelerometerServer.class);
                                    intent.putExtra("Device1_IP", localIp);
                                    intent.putExtra("Device2_IP", SERVER_IP);
                                    intent.putExtra("Device_Type", "SERVER");
                                    startActivity(intent);
                                } else {
                                    Toast.makeText(this, "Device doesn't have accelerometer", Toast.LENGTH_SHORT).show();
                                }
                            } else if (item.getTitle().equals(getResources().getString(R.string.send_magnetometer_data))) {
                                /*List<Sensor> deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
                                for (Sensor eee : deviceSensors){
                                    Log.e(TAG,eee.getName());
                                }*/
                                if (sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
                                    connectThreadMethodType = GYROSCOPE;
                                    Executors.newSingleThreadExecutor().submit(this::connectThreadMethod);
                                    Intent intent = new Intent(this, SendGyroscopeServer.class);
                                    intent.putExtra("Device1_IP", localIp);
                                    intent.putExtra("Device2_IP", SERVER_IP);
                                    intent.putExtra("Device_Type", "SERVER");
                                    startActivity(intent);
                                } else {
                                    Toast.makeText(this, "Device doesn't have gyroscope", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(MainActivity.this, "WIP", Toast.LENGTH_SHORT).show();
                            }

                            return true;
                        });
                        popup.show();//showing popup menu
                    });
                });
            }
        } catch (IOException e) {
            Log.d(TAG, "Receive timed out");
        }
    }

    private void connectThreadMethod() {
        byte[] sendData = (connectThreadMethodType).getBytes();
        try {
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(SERVER_IP), DISCOVERY_PORT);
            socket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        broadcastThread.shutdownNow();
        scanThread.shutdownNow();
        socket.close();
    }
}