// ICanCallback.aidl
package com.aaos.cand;

import com.aaos.cand.CanData;

oneway interface ICanCallback {
    void onCanDataChanged(in CanData data);
}