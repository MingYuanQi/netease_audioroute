package com.netease.nertc.audiocall;

import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static com.netease.nertc.audiocall.utils.DeviceUtil.audioDeviceToString;
import static com.netease.nertc.audiocall.utils.DeviceUtil.audioModeToString;
import static com.netease.yunxin.lite.audio.BluetoothManager.BLUETOOTH_SCO_TIMEOUT_MS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.Log;

import com.netease.lava.webrtc.Logging;

import com.netease.nertc.audiocall.bluetooth.DemoBluetoothManager;
import com.netease.nertc.audiocall.utils.ArrayUtils;
import com.netease.nertc.audiocall.utils.AudioDevice;
import com.netease.nertc.audiocall.utils.AudioManagerEvents;
import com.netease.nertc.audiocall.utils.Compatibility;
import com.netease.nertc.audiocall.utils.DeviceUtil;
import com.netease.nertc.audiocall.utils.SystemPermissionUtils;
import com.netease.yunxin.lite.audio.AudioDeviceCompatibility;

import java.util.HashSet;
import java.util.Set;

public class DemoAudioManager {
    public static String TAG = "DemoAudioManager";
    private static AudioManager DemoAudioManager;
    private static WiredHeadsetReceiver mWiredHeadsetReceiver;
    private final Context mContext;
    private int mSavedAudioMode = AudioManager.MODE_INVALID;
    private boolean mSavedIsSpeakerPhoneOn = false;
    private boolean mSavedIsMicrophoneMute = false;
    private DemoBluetoothManager mBluetoothManager;
    private boolean mHasWiredHeadset = false;
    private volatile int mSelectedAudioDevice = AudioDevice.NONE;
    private int mUserSelectedAudioDevice = AudioDevice.NONE;
    private int mDefaultAudioDevice = AudioDevice.NONE;
    private Set<Integer> mAudioDevices = new HashSet<>();
    private AudioManagerEvents mAudioManagerEvents;
    private boolean wiredHeadsetHasMic = false;
    private boolean bluetoothTryReconnect;
    private AudioManagerState mAudioManagerState;
    private AudioManager.OnAudioFocusChangeListener mAudioFocusChangeListener;

    public DemoAudioManager(Context context, int audioMode, AudioManagerEvents audioManagerEvents){
        mContext = context;
        DemoAudioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        mBluetoothManager = DemoBluetoothManager.create(context, this, BLUETOOTH_SCO_TIMEOUT_MS);
        mWiredHeadsetReceiver = new WiredHeadsetReceiver();
        mAudioManagerState = AudioManagerState.UNINITIALIZED;
        mAudioManagerEvents = audioManagerEvents;
        start(audioMode, AudioDevice.SPEAKER_PHONE, AUDIOFOCUS_GAIN,true);
    }
    public void start(int audioMode,
                      int defaultAudioDevice,
                      int focusMode,
                      boolean isHFP) {

        Log.i(TAG, "start , defaultAudioDevice: " + defaultAudioDevice + " , focusMode: " + focusMode + " , isHFP: " + isHFP);
        if (mAudioManagerState == AudioManagerState.RUNNING) {
            Logging.e(TAG, "AudioManager is already active");
            return;
        }
        mAudioManagerState = AudioManagerState.RUNNING;
        setmode(audioMode);
        bluetoothTryReconnect = false;
        saveAudioStatus();
        mHasWiredHeadset = hasWiredHeadset();
        registerAudioFocusRequest(true, AudioDeviceCompatibility.getStreamType(), focusMode);
        setMicrophoneMute(false);
        mUserSelectedAudioDevice = AudioDevice.NONE;
        mSelectedAudioDevice = AudioDevice.NONE;
        if (mDefaultAudioDevice == AudioDevice.NONE) {
            mDefaultAudioDevice = defaultAudioDevice;
        }
        mAudioDevices.clear();
        setAudioBlueToothSCO(isHFP);
        mBluetoothManager.start();
        mContext.registerReceiver(mWiredHeadsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
    }
    public void setAudioBlueToothSCO(boolean blueToothSCO) {
        if (mBluetoothManager == null) {
            Logging.w(TAG, "setAudioBlueToothSCO but NPL: " + blueToothSCO);
            return;
        }
        if (blueToothSCO && !SystemPermissionUtils.checkBluetoothScoConnectPermission(mContext)) {
            blueToothSCO = false;
            Logging.e(TAG, "setAudioBlueToothSCO no permission");
        }

        boolean reStartBlueTooth = (mSelectedAudioDevice == AudioDevice.BLUETOOTH) && (blueToothSCO != mBluetoothManager.blueToothIsSCO());
        mBluetoothManager.setAudioBlueToothSCO(blueToothSCO);
        if (reStartBlueTooth) {
            reconnectBlueTooth();
        }
        Logging.i(TAG, "setAudioBlueToothSCO: " + blueToothSCO + " , re start: " + reStartBlueTooth);
    }
    public void reconnectBlueTooth(){
        bluetoothTryReconnect = true;
        updateAudioDeviceState();
        bluetoothTryReconnect = false;
    }
    public void updateAudioDeviceState() {
        if (mAudioManagerState != AudioManagerState.RUNNING) {
            Logging.w(TAG, "updateAudioDeviceState , but state is :" + mAudioManagerState);
            return;
        }
        Log.i(TAG, "updateAudioDeviceState current device status: wired headset=" + mHasWiredHeadset
                + ", reconnect=" + bluetoothTryReconnect
                + ", bluetooth state=" + mBluetoothManager.getState()
                + ", available=" + audioDeviceToString(ArrayUtils.toPrimitive(mAudioDevices.toArray(new Integer[0])))
                + ", pre worked=" + audioDeviceToString(mSelectedAudioDevice)
                + ", user selected=" + audioDeviceToString(mUserSelectedAudioDevice));

        if (bluetoothTryReconnect && mBluetoothManager.getState() == DemoBluetoothManager.State.UNINITIALIZED) {
            bluetoothTryReconnect = false;
        }

        if (mBluetoothManager.getState() == DemoBluetoothManager.State.HEADSET_AVAILABLE
                || mBluetoothManager.getState() == DemoBluetoothManager.State.HEADSET_UNAVAILABLE
                || mBluetoothManager.getState() == DemoBluetoothManager.State.SCO_DISCONNECTING
                || bluetoothTryReconnect) {
            mBluetoothManager.updateDevice();
        }

        Set<Integer> newAudioDevices = new HashSet<>();

        if (mBluetoothManager.getState() == DemoBluetoothManager.State.SCO_CONNECTED
                || mBluetoothManager.getState() == DemoBluetoothManager.State.SCO_CONNECTING
                || mBluetoothManager.getState() == DemoBluetoothManager.State.HEADSET_AVAILABLE) {
            newAudioDevices.add(AudioDevice.BLUETOOTH);
        }

        if (mHasWiredHeadset) {
            newAudioDevices.add(AudioDevice.WIRED_HEADSET);
        } else {
            newAudioDevices.add(AudioDevice.SPEAKER_PHONE);
            if (hasEarpiece()) {
                newAudioDevices.add(AudioDevice.EARPIECE);
            }
        }

        boolean audioDeviceSetUpdated = !mAudioDevices.equals(newAudioDevices);
        mAudioDevices = newAudioDevices;

        int userSelectedRet = mUserSelectedAudioDevice;
        //no bluetooth headset
        if (mUserSelectedAudioDevice == AudioDevice.BLUETOOTH &&
                (mBluetoothManager.getState() == DemoBluetoothManager.State.HEADSET_UNAVAILABLE || mBluetoothManager.getState() == DemoBluetoothManager.State.UNINITIALIZED)) {
            userSelectedRet = AudioDevice.NONE;
        }
        //no wired headset
        if (!mHasWiredHeadset && mUserSelectedAudioDevice == AudioDevice.WIRED_HEADSET) {
            userSelectedRet = AudioDevice.NONE;
        }

        int newAudioDevice = mDefaultAudioDevice;
        if (userSelectedRet != AudioDevice.NONE) {
            newAudioDevice = userSelectedRet;
        } else if (mBluetoothManager.getState() != DemoBluetoothManager.State.HEADSET_UNAVAILABLE && mBluetoothManager.getState() != DemoBluetoothManager.State.UNINITIALIZED) {
            newAudioDevice = AudioDevice.BLUETOOTH;
        } else if (mHasWiredHeadset) {
            newAudioDevice = AudioDevice.WIRED_HEADSET;
        } else if(newAudioDevice == AudioDevice.SPEAKER_PHONE && mSelectedAudioDevice == AudioDevice.WIRED_HEADSET) {
            newAudioDevice = AudioDevice.EARPIECE;
        } else if(newAudioDevice == AudioDevice.SPEAKER_PHONE && mSelectedAudioDevice == AudioDevice.BLUETOOTH) {
            newAudioDevice = AudioDevice.EARPIECE;
        }

        boolean needStopBluetooth = (mBluetoothManager.getState() == DemoBluetoothManager.State.SCO_CONNECTED || mBluetoothManager.getState() == DemoBluetoothManager.State.SCO_CONNECTING)
                && (newAudioDevice != AudioDevice.NONE && newAudioDevice != AudioDevice.BLUETOOTH);

        boolean needStartBluetooth = mBluetoothManager.getState() == DemoBluetoothManager.State.HEADSET_AVAILABLE
                && (newAudioDevice == AudioDevice.NONE || newAudioDevice == AudioDevice.BLUETOOTH);

        Log.i(TAG, "updateAudioDeviceState bluetooth audio: start=" + needStartBluetooth
                + ", stop=" + needStopBluetooth
                + ", state=" + mBluetoothManager.getState()
                + ", userSelectedRet=" + audioDeviceToString(userSelectedRet));


        boolean deviceChanged = newAudioDevice != mSelectedAudioDevice || audioDeviceSetUpdated;
        if (deviceChanged) {
            setAudioDeviceInternal(newAudioDevice);
            Log.i(TAG, "updateAudioDeviceState new device status: available=" + audioDeviceToString(ArrayUtils.toPrimitive(mAudioDevices.toArray(new Integer[0])))
                    + " , selected=" + audioDeviceToString(newAudioDevice));
        }

        if (needStopBluetooth) {
            mBluetoothManager.stopScoAudio();
            mBluetoothManager.updateDevice();
        }

        if (bluetoothTryReconnect || needStartBluetooth) {
            if (!mBluetoothManager.startScoAudio()) {
                mAudioDevices.remove(AudioDevice.BLUETOOTH);
            }
        }
        boolean bluetoothReconnected = newAudioDevice == AudioDevice.BLUETOOTH && bluetoothTryReconnect;
        if (deviceChanged || bluetoothReconnected) {
            if (mAudioManagerEvents != null) {
                mAudioManagerEvents.onAudioDeviceChanged(mSelectedAudioDevice, mAudioDevices, hasExternalMic(newAudioDevice));
            }
        }
        Log.i(TAG, "updateAudioDeviceState done");
    }
    public void userSelectAudioDevice(int audioDevice){
        mUserSelectedAudioDevice = audioDevice;
        updateAudioDeviceState();
    }
    private void setAudioDeviceInternal(int device) {
        Log.i(TAG, "setAudioDeviceInternal(device=" + audioDeviceToString(device) + ")");
        switch (device) {
            case AudioDevice.SPEAKER_PHONE:
                setSpeakerphoneOn(true);
                break;
            case AudioDevice.EARPIECE:
            case AudioDevice.WIRED_HEADSET:
            case AudioDevice.BLUETOOTH:
                setSpeakerphoneOn(false);
                break;
            default:
                Log.e(TAG, "Invalid audio device selection");
                break;
        }

        mSelectedAudioDevice = device;
    }
    private boolean hasExternalMic(int selectedAudioDevice) {

        boolean hasExternalMic = false;

        if (Compatibility.runningOnMarshmallowOrHigher()) {
            AudioDeviceInfo[] devices = DemoAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            for (AudioDeviceInfo deviceInfo : devices) {
                if ((deviceInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET && selectedAudioDevice == AudioDevice.WIRED_HEADSET)
                        || (deviceInfo.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO && selectedAudioDevice == AudioDevice.BLUETOOTH)) {
                    hasExternalMic = true;
                    break;
                }
            }
        } else {
            if (selectedAudioDevice == AudioDevice.WIRED_HEADSET) {
                hasExternalMic = wiredHeadsetHasMic;
            } else if (selectedAudioDevice == AudioDevice.BLUETOOTH) {
                //对于 Android 6.0.0 以下的版本 ，如果是蓝牙的话 ， 直接返回true ， 在有些情况下可能有问题
                hasExternalMic = true;
            }
        }
        Logging.i(TAG, "hasExternalMic : " + hasExternalMic + " , selectedAudioDevice: " + DeviceUtil.audioDeviceToString(selectedAudioDevice));
        return hasExternalMic;
    }
    private void registerAudioFocusRequest(boolean register, int streamType, int focusMode) {
        if (register) {
            if (mAudioFocusChangeListener == null) {
                mAudioFocusChangeListener = focusChange -> {
                    String typeOfChange = DeviceUtil.audioFocusChangeToString(focusChange);
                    Logging.i(TAG, "onAudioFocusChange: " + typeOfChange);
                };

                int result = DemoAudioManager.requestAudioFocus(mAudioFocusChangeListener, streamType, focusMode);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Logging.i(TAG, "Audio focus request granted for " + (DeviceUtil.streamTypeToString(streamType)));
                } else {
                    Logging.e(TAG, "Audio focus request failed");
                }
            }
        } else {
            if (mAudioFocusChangeListener != null) {
                DemoAudioManager.abandonAudioFocus(mAudioFocusChangeListener);
                mAudioFocusChangeListener = null;
                Logging.i(TAG, "Abandoned audio focus ");
            }
        }
    }
    private void setmode(int audioMode){
        if (mAudioManagerState != AudioManagerState.RUNNING) {
            Log.w(TAG, "dynamic set audio mode in incorrect state: " + mAudioManagerState + " , mode: " + audioModeToString(audioMode));
            return;
        }
        DemoAudioManager.setMode(audioMode);
    }
    private void setSpeakerphoneOn(boolean on) {
        DemoAudioManager.setSpeakerphoneOn(on);
        Log.i(TAG, "setSpeakerphoneOn " + on + " ,result -> " + DemoAudioManager.isSpeakerphoneOn());
    }
    private void saveAudioStatus() {
        mSavedAudioMode = DemoAudioManager.getMode();
        mSavedIsSpeakerPhoneOn = DemoAudioManager.isSpeakerphoneOn();
        mSavedIsMicrophoneMute = DemoAudioManager.isMicrophoneMute();
        Log.i(TAG, "save system audio state[audio mode:" + audioModeToString(mSavedAudioMode)
                + ", microphone mute:" + mSavedIsMicrophoneMute
                + ", speakerphone on:" + mSavedIsSpeakerPhoneOn + "]");
    }
    private boolean hasEarpiece() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }
    @SuppressWarnings("deprecation")
    private boolean hasWiredHeadset() {
        return DemoAudioManager.isWiredHeadsetOn();
    }
    private void setMicrophoneMute(boolean on) {
        boolean wasMuted = DemoAudioManager.isMicrophoneMute();
        if (wasMuted == on) {
            return;
        }
        DemoAudioManager.setMicrophoneMute(on);
    }
    public void stop() {
        Log.i(TAG, "stop");
        mAudioManagerState = AudioManagerState.UNINITIALIZED;
        try {
            mContext.unregisterReceiver(mWiredHeadsetReceiver);
        } catch (Exception e) {
            Log.w(TAG, e.getMessage());
        }
        mBluetoothManager.stop();
        registerAudioFocusRequest(false, 0, 0);
        restoreAudioStatus();
        mSelectedAudioDevice = AudioDevice.NONE;
        mAudioManagerEvents = null;
        Log.i(TAG, "AudioManager stopped");
    }
    private void restoreAudioStatus() {
        Log.i(TAG, "restore audio status");
        setMicrophoneMute(mSavedIsMicrophoneMute);
        Log.i(TAG, "restore setMicrophoneMute done");
        if (mSelectedAudioDevice == AudioDevice.SPEAKER_PHONE
                || mSelectedAudioDevice == AudioDevice.EARPIECE) {
            setSpeakerphoneOn(mSavedIsSpeakerPhoneOn);
            Logging.i(TAG, "restore setSpeakerphoneOn done");
        }
        if (mSavedAudioMode != AudioManager.MODE_INVALID) {
            DemoAudioManager.setMode(mSavedAudioMode);
        }
        Log.i(TAG, "restore system audio state[audio mode:" + DeviceUtil.audioModeToString(mSavedAudioMode)
                + ", microphone mute:" + mSavedIsMicrophoneMute
                + ", speakerphone on:" + mSavedIsSpeakerPhoneOn + "]");
    }
    private class WiredHeadsetReceiver extends BroadcastReceiver {

        private static final int STATE_UNPLUGGED = 0;
        private static final int STATE_PLUGGED = 1;
        private static final int HAS_NO_MIC = 0;
        private static final int HAS_MIC = 1;

        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra("state", STATE_UNPLUGGED);
            int microphone = intent.getIntExtra("microphone", HAS_NO_MIC);
            String name = intent.getStringExtra("name");
            Log.i(TAG, "WiredHeadsetReceiver.onReceive: "
                    + "a=" + intent.getAction() + ", s="
                    + (state == STATE_UNPLUGGED ? "unplugged" : "plugged") + ", m="
                    + (microphone == HAS_MIC ? "mic" : "no mic") + ", n=" + name + ", sb="
                    + isInitialStickyBroadcast());
            mHasWiredHeadset = (state == STATE_PLUGGED);
            wiredHeadsetHasMic = (state == STATE_PLUGGED) && (microphone == HAS_MIC);
            updateAudioDeviceState();
        }
    }
    private enum AudioManagerState {
        UNINITIALIZED,
        RUNNING,
    }
}
