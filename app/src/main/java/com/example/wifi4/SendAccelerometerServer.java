package com.example.wifi4;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
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

public class SendAccelerometerServer extends AppCompatActivity implements SensorEventListener {
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
    private static final String TAG = "wifi, SendAccelerometerServer";
    private ServerSocket serverSocket;
    private ScheduledExecutorService messageThread;

    float[] gravity;
    long timeStamp;

    private float xAxis;
    private float yAxis;
    private float zAxis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_accelerometer);

        ButterKnife.bind(this);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        Intent intent = getIntent();
        String localIp = intent.getStringExtra("Device1_IP");
        String str = "Your device: " + localIp;
        device1View.setText(str);
        String secondDeviceIp = intent.getStringExtra("Device2_IP");
        str = "Connected to: " + secondDeviceIp;
        device2View.setText(str);

        gravity = new float[3];

        Executors.newSingleThreadScheduledExecutor().submit(this::runServer);
        messageThread = Executors.newSingleThreadScheduledExecutor();
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
    public void onSensorChanged(SensorEvent event) {
        // In this example, alpha is calculated as t / (t + dT),
        // where t is the low-pass filter's time-constant and
        // dT is the event delivery rate.
        if (timeStamp != 0) {
            float alpha = (float) 100 / (float) (100 + (event.timestamp - timeStamp)/1000000);

            // Isolate the force of gravity with the low-pass filter.
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

            xAxis = event.values[0] - gravity[0];
            String str = "Sent X axis: " + String.format(Locale.getDefault(),"%.2f", xAxis);
            xAxisView.setText(str);
            yAxis = event.values[1] - gravity[1];
            str = "Sent Y axis: " + String.format(Locale.getDefault(),"%.2f", yAxis);
            yAxisView.setText(str);
            zAxis = event.values[2] - gravity[2];
            str = "Sent Z axis: " + String.format(Locale.getDefault(),"%.2f", zAxis);
            zAxisView.setText(str);
        }
        timeStamp = event.timestamp;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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
}