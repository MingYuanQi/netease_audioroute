package com.netease.nertc.audiocall.bluetooth;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.netease.lava.webrtc.Logging;
import com.netease.nertc.audiocall.DemoAudioManager;
import com.netease.nertc.audiocall.utils.SystemPermissionUtils;


public abstract class DemoBluetoothManager {
    private static final String TAG = "AbsBluetoothManager";
    public enum State {
        UNINITIALIZED,
        HEADSET_UNAVAILABLE,
        HEADSET_AVAILABLE,
        SCO_DISCONNECTING,
        SCO_CONNECTING,
        SCO_CONNECTED
    }

    public static DemoBluetoothManager create(Context context, DemoAudioManager manager, int bluetoothSCOTimeoutMs) {
        Logging.i(TAG, "create bluetooth manager");

        //https://developer.android.com/guide/topics/connectivity/bluetooth/permissions
        int targetVersion = context.getApplicationInfo().targetSdkVersion;
        // Android 12 校验 BLUETOOTH_CONNECT 权限
        boolean hasBluetoothPermission = SystemPermissionUtils.checkBluetoothScoConnectPermission(context);
        if (!hasBluetoothPermission) {
            Logging.e(TAG, "missing  permission , create FakeBluetoothManager");
            Logging.e(TAG, "has bluetooth permission: " + SystemPermissionUtils.checkBluetoothPermission(context));
            Logging.e(TAG, "has bluetoothConnect permission：" + SystemPermissionUtils.checkBluetoothConnectPermission(context));
            Logging.e(TAG, "targetVersion：" + targetVersion + ", sdk int: " + Build.VERSION.SDK_INT);
            return new FakeBluetoothManager(context, manager);
        }

        return new BluetoothManager(context, manager, bluetoothSCOTimeoutMs);
    }

    protected final Context mContext;
    protected final DemoAudioManager DemoAudioDeviceManager;
    protected final AudioManager mAudioManager;
    protected final Handler mHandler;
    protected State mBluetoothState;
    protected volatile boolean mBlueToothSCO;

    public DemoBluetoothManager(Context context, DemoAudioManager manager) {
        mContext = context;
        DemoAudioDeviceManager = manager;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        mBluetoothState = State.UNINITIALIZED;
    }

    public abstract void start();

    public abstract void stop();

    public abstract void setAudioBlueToothSCO(boolean blueToothSCO);

    public boolean blueToothIsSCO() {
        return mBlueToothSCO;
    }

    public void stopScoAudio() {
    }

    public void updateDevice() {
    }

    public boolean startScoAudio() {
        return true;
    }

    public State getState() {
        return mBluetoothState;
    }
}
