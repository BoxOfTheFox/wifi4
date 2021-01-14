package com.example.wifi4;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SendGyroscopeServer extends AppCompatActivity implements SensorEventListener {
    @BindView(R.id.accelerometer_x_axis)
    TextView xAxisView;

    @BindView(R.id.accelerometer_y_axis)
    TextView yAxisView;

    @BindView(R.id.accelerometer_z_axis)
    TextView zAxisView;

    @BindView(R.id.device1_ip)
    TextView device1View;

    @BindView(R.id.device2_ip)
    TextView device2View;

    private SensorManager sensorManager;
    private Sensor sensor;

    private static final int SERVER_PORT = 9700;
    private static final String TAG = "wifi, SendGyroscopeServer";
    private ServerSocket serverSocket;
    private ScheduledExecutorService messageThread;

    private int xAxis;
    private int yAxis;
    private int zAxis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_accelerometer);

        ButterKnife.bind(this);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        Intent intent = getIntent();
        String localIp = intent.getStringExtra("Device1_IP");
        String str = "Your device: " + localIp;
        device1View.setText(str);
        String secondDeviceIp = intent.getStringExtra("Device2_IP");
        str = "Connected to: " + secondDeviceIp;
        device2View.setText(str);

        Executors.newSingleThreadScheduledExecutor().submit(this::runServer);
        messageThread = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        xAxis = (int) event.values[0];
        String str = "Sent X axis rotation: " + xAxis + " rad/s";
        xAxisView.setText(str);
        yAxis = (int) event.values[1];
        str = "Sent Y axis rotation: " + yAxis + " rad/s";
        yAxisView.setText(str);
        zAxis = (int) event.values[2];
        str = "Sent Z axis rotation: " + zAxis + " rad/s";
        zAxisView.setText(str);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private PrintWriter output;

    void sendMessage() {
        String message = String.valueOf(xAxis) + ' ' + yAxis + ' ' + zAxis + '\n';
        if (!message.isEmpty()) {
            Executors.newSingleThreadExecutor().submit(() -> {
                output.write(message);
                output.flush();
            });
        }
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            Socket acceptSocket = serverSocket.accept();
            output = new PrintWriter(acceptSocket.getOutputStream());
            messageThread.scheduleWithFixedDelay(this::sendMessage, 0, 1, TimeUnit.MILLISECONDS);
        }catch (ConnectException c){
            runOnUiThread(() -> Toast.makeText(this,"Couldn't connect, trying again...",Toast.LENGTH_SHORT));
            Executors.newSingleThreadScheduledExecutor().schedule(this::runServer, 1, TimeUnit.SECONDS);
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        messageThread.shutdownNow();
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}