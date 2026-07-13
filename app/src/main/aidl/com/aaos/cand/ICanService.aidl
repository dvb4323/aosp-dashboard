// ICanService.aidl
package com.aaos.cand;

import com.aaos.cand.ICanCallback;
import com.aaos.cand.CanData;

interface ICanService {
    void registerCallback(in ICanCallback callback);
    void unregisterCallback(in ICanCallback callback);
    CanData getLastCanData();
    void sendLedCommand(int ledCommand, int modeCommand);
}