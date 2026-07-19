// ICanService.aidl
package com.aaos.cand;

import com.aaos.cand.ICanCallback;
import com.aaos.cand.CanData;
import com.aaos.cand.UdsResult;

interface ICanService {
    void registerCallback(in ICanCallback callback);
    void unregisterCallback(in ICanCallback callback);
    CanData getLastCanData();
    void sendLedCommand(int ledCommand, int modeCommand);
    String readVin();
    UdsResult readStatusUds();
    UdsResult readSensorUds();
    UdsResult readVinUds();
    UdsResult writeLedUds(int value);
    UdsResult writeModeUds(int value);
}