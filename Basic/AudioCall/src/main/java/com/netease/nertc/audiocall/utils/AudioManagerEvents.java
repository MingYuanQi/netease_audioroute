package com.netease.nertc.audiocall.utils;

import java.util.Set;

public interface AudioManagerEvents {
    void onAudioDeviceChanged(int selectedAudioDevice, Set<Integer> availableAudioDevices, boolean hasExternalMic);
}
