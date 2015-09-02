package com.hoho.android.usbserial.examples;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;


/**
 * Created by Nuwan Prabhath on 9/1/2015.
 */
public class UsbDataReceiver  extends ResultReceiver {

    private Receiver mReceiver;

    public UsbDataReceiver(Handler handler) {
        super(handler);
    }

    public void setReceiver(Receiver receiver) {
        mReceiver = receiver;
    }

    public interface Receiver {
        public void onReceiveResult(int resultCode, Bundle resultData);
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (mReceiver != null) {
            mReceiver.onReceiveResult(resultCode, resultData);
        }
    }
}
