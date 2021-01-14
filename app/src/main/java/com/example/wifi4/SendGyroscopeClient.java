package com.example.wifi4;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SendGyroscopeClient extends AppCompatActivity {
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

    private String secondDeviceIp;
    private static final int SERVER_PORT = 9700;
    private static final String TAG = "wifi, SendGyroscopeClient";
    private ScheduledExecutorService messageThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_accelerometer);

        ButterKnife.bind(this);

        Intent intent = getIntent();
        String localIp = intent.getStringExtra("Device1_IP");
        String str = "Your device: " + localIp;
        device1View.setText(str);
        secondDeviceIp = intent.getStringExtra("Device2_IP");
        str = "Connected to: " + secondDeviceIp;
        device2View.setText(str);

        Executors.newSingleThreadScheduledExecutor().schedule(this::runClient, 1, TimeUnit.SECONDS);
        messageThread = Executors.newSingleThreadScheduledExecutor();
    }

    private BufferedReader input;

    void getMessage() {
        try {
            final String message = input.readLine();
            if (message != null) {
                String[] axis = message.split(" ");
                runOnUiThread(() -> {
                    String str = "Received X axis rotation: " + axis[0] + " rad/s";
                    xAxisView.setText(str);
                    str = "Received Y axis rotation: " + axis[1] + " rad/s";
                    yAxisView.setText(str);
                    str = "Received Z axis rotation: " + axis[2] + " rad/s";
                    zAxisView.setText(str);
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void runClient() {
        Log.e(TAG, "client");
        try {
            Socket clientSocket = new Socket(secondDeviceIp, SERVER_PORT);
            input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            messageThread.scheduleWithFixedDelay(this::getMessage, 0, 1, TimeUnit.MILLISECONDS);
        } catch (ConnectException c){
            runOnUiThread(() -> Toast.makeText(this,"Couldn't connect, trying again...",Toast.LENGTH_SHORT));
            Executors.newSingleThreadScheduledExecutor().schedule(this::runClient, 1, TimeUnit.SECONDS);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        messageThread.shutdownNow();
    }
}