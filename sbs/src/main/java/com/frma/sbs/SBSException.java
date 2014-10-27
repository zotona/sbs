package com.frma.sbs;

/**
 * Created by ahmet on 26.10.2014.
 */
public class SBSException extends Exception {
    public int mErrcode;
    SBSException(String msg, int errcode) {
        super(msg);
        mErrcode = errcode;
    }
    public int getErrCode() {
        return mErrcode;
    }
}