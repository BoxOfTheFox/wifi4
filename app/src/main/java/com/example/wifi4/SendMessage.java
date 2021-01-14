package com.example.wifi4;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class SendMessage extends AppCompatActivity {
    @BindView(R.id.Device1_ip)
    TextView device1View;

    @BindView(R.id.Device2_ip)
    TextView device2View;

    @BindView(R.id.received_message)
    TextView messageView;

    @BindView(R.id.input_space)
    EditText messageText;

    private String secondDeviceIp;
    private static final int SERVER_PORT = 9700;
    private static final String TAG = "wifi, SendMessage";
    private ServerSocket serverSocket;
    private ScheduledExecutorService messageThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_message);

        ButterKnife.bind(this);

        Intent intent = getIntent();
        String localIp = intent.getStringExtra("Device1_IP");
        String str = "Your device: " + localIp;
        device1View.setText(str);
        secondDeviceIp = intent.getStringExtra("Device2_IP");
        str = "Connected to: " + secondDeviceIp;
        device2View.setText(str);

        if (intent.getStringExtra("Device_Type").equals("SERVER")) {
            Executors.newSingleThreadScheduledExecutor().submit(this::runServer);
        } else {
            Executors.newSingleThreadScheduledExecutor().schedule(this::runClient, 1, TimeUnit.SECONDS);
        }
        messageThread = Executors.newSingleThreadScheduledExecutor();
    }

    private PrintWriter output;
    private BufferedReader input;

    @OnClick(R.id.send_button)
    public void send() {
        Log.e(TAG, "send button");
        String message = messageText.getText().toString().trim() + '\n';
        if (!message.isEmpty()) {
            Executors.newSingleThreadExecutor().submit(() -> {
                Log.i(TAG, "send " + message);
                output.write(message);
                output.flush();
            });
        }
        messageText.getText().clear();
    }

    void getMessage() {
        try {
            final String message = input.readLine();
            if (message != null) {
                Log.e(TAG, "received " + message);
                runOnUiThread(() -> {
                    String str = "Received message: " + message;
                    messageView.setText(str);
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void runServer() {
        Log.e(TAG, "server");
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            Socket acceptSocket = serverSocket.accept();
            output = new PrintWriter(acceptSocket.getOutputStream());
            input = new BufferedReader(new InputStreamReader(acceptSocket.getInputStream()));
            messageThread.scheduleWithFixedDelay(this::getMessage, 0, 1, TimeUnit.MILLISECONDS);
        }catch (ConnectException c){
            runOnUiThread(() -> Toast.makeText(this,"Couldn't connect, trying again...",Toast.LENGTH_SHORT));
            Executors.newSingleThreadScheduledExecutor().schedule(this::runServer, 1, TimeUnit.SECONDS);
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void runClient() {
        Log.e(TAG, "client");
        try {
            Socket clientSocket = new Socket(secondDeviceIp, SERVER_PORT);
            output = new PrintWriter(clientSocket.getOutputStream());
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
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}