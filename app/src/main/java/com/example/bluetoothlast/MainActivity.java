package com.example.bluetoothlast;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String CHANNEL_ID = "notification_channel";
    private NotificationManager mNotificationManager;
    private static final int NOTIFICATION_ID = 0;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    TextView mTvBluetoothStatus;
    TextView mTvReceiveData;
    Button mBtnBluetoothOn;
    Button mBtnBluetoothOff;
    Button mBtnConnect;
    Button mBtnClear;

    BluetoothAdapter mBluetoothAdapter;
    Set<BluetoothDevice> mPairedDevices;
    List<String> mListPairedDevices;

    Handler mBluetoothHandler;
    ConnectedBluetoothThread mThreadConnectedBluetooth;
    BluetoothDevice mBluetoothDevice;
    BluetoothSocket mBluetoothSocket;

    int count = 0;
    String last_Str = "";
    String combinedText = "";

    final static int BT_REQUEST_ENABLE = 1;
    final static int BT_MESSAGE_READ = 2;
    final static int BT_CONNECTING_STATUS = 3;

    final static UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    final private String[] permissions = {
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.POST_NOTIFICATIONS
    };

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        editor = sharedPreferences.edit();
        requestPermissions();
        mTvBluetoothStatus = (TextView) findViewById(R.id.tvBluetoothStatus);
        mTvReceiveData = (TextView) findViewById(R.id.tvReceiveData);
        mBtnBluetoothOn = (Button) findViewById(R.id.btnBluetoothOn);
        mBtnBluetoothOff = (Button) findViewById(R.id.btnBluetoothOff);
        mBtnConnect = (Button) findViewById(R.id.btnConnect);
        mBtnClear = (Button) findViewById(R.id.btnClear);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mBtnBluetoothOn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothOn();
            }
        });
        mBtnBluetoothOff.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothOff();
            }
        });
        mBtnConnect.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                listPairedDevices();
            }
        });

        mBtnClear.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothClear();
            }
        });

        mBluetoothHandler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(Message msg) {
                if (msg.what == BT_MESSAGE_READ) {
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    String time = getTime();
                    if(last_Str.equals(readMessage)) {
                        count++;
                    }
                    String myMessage = returnString(readMessage);

                    if (myMessage != "none") {
                        if(count >= 4) {
                            String currentText = mTvReceiveData.getText().toString();
                            combinedText = time + " : " + myMessage + "\n" + currentText;
                            mTvReceiveData.setText(combinedText);
                            createNotificationChannel();
                            sendNotification(Integer.parseInt(String.valueOf(readMessage.charAt(0))), myMessage);
                            count = 0;
                        }
                        last_Str = readMessage;
                    }
                }
            }
        };
    }

    public void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            List<String> notGrantedPermissions = new ArrayList<>();

            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    notGrantedPermissions.add(permission);
                }
            }

            if (!notGrantedPermissions.isEmpty()) {
                // 아직 부여되지 않은 권한이 있으므로 권한 요청 대화상자 표시
                ActivityCompat.requestPermissions(this, notGrantedPermissions.toArray(new String[0]), BT_REQUEST_ENABLE);
            }
        }
    }

    ActivityResultLauncher<Intent> ARLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    switch (result.getResultCode()) {
                        case RESULT_OK:
                            Toast.makeText(getApplicationContext(), "블루투스 활성화", Toast.LENGTH_LONG).show();
                            mTvBluetoothStatus.setText("블루투스 : 활성화");
                            break;
                        case RESULT_CANCELED:
                            Toast.makeText(getApplicationContext(), "취소", Toast.LENGTH_LONG).show();
                            mTvBluetoothStatus.setText("블루투스 : 비활성화");
                            break;
                    }
                }
            }
    ) ;

    private String returnString(String msg) {
        int number = 3;
        String myString = "none";
        try{
            number = Integer.parseInt(String.valueOf(msg.charAt(0)));
        }
        catch (NumberFormatException ex){
            ex.printStackTrace();
        }
        switch (number) {
            case 0: myString = "bee";
            break;
            case 1: myString = "butterfly";
            break;
            case 2: myString = "moth";
            break;
            case 3: myString = "none";
            break;
            case 4: myString = "stink";
            break;
        }
        return myString;
    }
    // 마지막 알람 보낸 시간을 저장하는 메소드
    private void saveLastNotificationTime(int i, long time) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("lastNotificationTime" + i, time);
        editor.apply();
    }
    private String getTime() {
        long now = System.currentTimeMillis();
        Date date = new Date(now);
        SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm:ss");
        return dateFormat.format(date);
    }
    public void createNotificationChannel() {
        mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    CHANNEL_ID, "Test Notification", mNotificationManager.IMPORTANCE_HIGH);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.enableVibration(true);
            notificationChannel.setDescription("Notification from stm");
            mNotificationManager.createNotificationChannel(notificationChannel);
        }
    }
    private NotificationCompat.Builder getNotificationBuilder(String msg) {
        NotificationCompat.Builder notifyBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Notified!\n")
                .setContentText("Detected "+ msg)
                .setSmallIcon(R.drawable.ic_android);
        return notifyBuilder;
    }

    public void sendNotification(int i, String message) {
        // SharedPreferences에서 마지막 알람 보낸 시간을 가져옴
        long lastNotificationTime = sharedPreferences.getLong("lastNotificationTime" + i, 0);

        // 현재 시간을 가져옴
        long currentTimeMillis = System.currentTimeMillis();
        // 30분(1800000 밀리초) 이상 경과한 경우에만 알람을 보냄
        if (currentTimeMillis - lastNotificationTime >= 60000) {
            // 알람 보내는 코드                            1800000 - 30분
            NotificationCompat.Builder notifyBuilder = getNotificationBuilder(message);
            mNotificationManager.notify(NOTIFICATION_ID, notifyBuilder.build());
            // 알람을 보낸 시간을 저장
            saveLastNotificationTime(i, currentTimeMillis);
        }
    }

    void bluetoothClear() {
        combinedText = "";
    }

    void bluetoothOn() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_LONG).show();
        } else {
            if (mBluetoothAdapter.isEnabled()) {
                Toast.makeText(getApplicationContext(), "블루투스가 이미 활성화 되어 있습니다.", Toast.LENGTH_LONG).show();
                mTvBluetoothStatus.setText("블루투스 : 활성화");
            } else {
                requestPermissions();
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                ARLauncher.launch(intent);
            }
        }
    }

    @SuppressLint("MissingPermission")
    void bluetoothOff() {
        if (mBluetoothAdapter.isEnabled()) {
            requestPermissions();
            mBluetoothAdapter.disable();
            Toast.makeText(getApplicationContext(), "블루투스가 비활성화 되었습니다.", Toast.LENGTH_SHORT).show();
            mTvBluetoothStatus.setText("블루투스 : 비활성화");
            // api33이상에서 bluetoothadapter의 disable은 사용할 수 없다.
        } else {
            Toast.makeText(getApplicationContext(), "블루투스가 이미 비활성화 되어 있습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission")
    void listPairedDevices() {
        if (mBluetoothAdapter.isEnabled()) {
            requestPermissions();
            mPairedDevices = mBluetoothAdapter.getBondedDevices();

            if (mPairedDevices.size() > 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("장치 선택");

                mListPairedDevices = new ArrayList<String>();
                for (BluetoothDevice device : mPairedDevices) {
                    mListPairedDevices.add(device.getName());
                }
                final CharSequence[] items = mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);
                mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        connectSelectedDevice(items[item].toString());
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();
            } else {
                Toast.makeText(getApplicationContext(), "페어링된 장치가 없습니다.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "블루투스가 비활성화 되어 있습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission")
    void connectSelectedDevice(String selectedDeviceName) {
        for (BluetoothDevice tempDevice : mPairedDevices) {
            requestPermissions();
            if (selectedDeviceName.equals(tempDevice.getName())) {
                mBluetoothDevice = tempDevice;
                break;
            }
        }
        mBluetoothAdapter.cancelDiscovery();
        try {
            mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID);
            mBluetoothSocket.connect();
            mThreadConnectedBluetooth = new ConnectedBluetoothThread(mBluetoothSocket);
            mThreadConnectedBluetooth.start();
            mBluetoothHandler.obtainMessage(BT_CONNECTING_STATUS, 1, -1).sendToTarget();
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "블루투스 연결 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
        }
    }

    private class ConnectedBluetoothThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedBluetoothThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "소켓 연결 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = mmInStream.available();
                    if (bytes != 0) {
                        SystemClock.sleep(100);
                        bytes = mmInStream.available();
                        bytes = mmInStream.read(buffer, 0, bytes);
                        mBluetoothHandler.obtainMessage(BT_MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }
        public void write(String str) {
            byte[] bytes = str.getBytes();
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "데이터 전송 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "소켓 해제 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }
        }
    }
}