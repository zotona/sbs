package com.frma.sbs;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * helper methods.
 */
public class Service extends IntentService {
    static final String SBS_CTRL = "com.frma.sbs.action.SBS_CTRL";
    static final String SBS_SET = "com.frma.sbs.action.SBS_SET";
    static final String SBS_NEW_STATUS = "com.frma.sbs.action.STATUS";
    private DisplayMetrics mDisplayMetrics;
    private boolean mSavedOn = false;
    private int mSavedZoom = 100;
    private int mSavedImgDistance = 60;

    public static void setSBS(Context context, boolean on, int zoom, int imgDistance) {
        Intent intent = new Intent(context, Service.class);
        intent.setAction(SBS_SET);
        intent.putExtra("ON", on);
        intent.putExtra("ZOOM", zoom);
        intent.putExtra("IMGDIST", imgDistance);
        context.startService(intent);
    }
    public static void ctrlSBS(Context context, String action) {
        Intent intent = new Intent(context, Service.class);
        intent.setAction(SBS_CTRL);
        intent.putExtra("CMD", action);
        context.startService(intent);
    }
    public Service() {
        super("Service");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        WindowManager window = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = window.getDefaultDisplay();
        mDisplayMetrics = new DisplayMetrics();
        display.getMetrics(mDisplayMetrics);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (SBS_CTRL.equals(action)) {
                String cmd = intent.getStringExtra("CMD");
                handleSBSControl(cmd);
            } else if (SBS_SET.equals(action)) {
                boolean on = intent.getBooleanExtra("ON", false);
                int zoom = intent.getIntExtra("ZOOM", 100);
                int imgDistance = intent.getIntExtra("IMGDIST", 60);
                handleSBSSet(on, zoom, imgDistance);
            }
        }
    }

    private void handleSBSControl(String cmd) {
        int rv = 0;
        try {
            if (cmd.equals("install")) {
                rv = runAndCheckSBSCmd("install");
            } else if (cmd.equals("uninstall")) {
                rv = runAndCheckSBSCmd("uninstall");
            } else if (cmd.equals("enablenext")) {
                rv = runAndCheckSBSCmd("enable");
            } else if (cmd.equals("enablepermanent")) {
                rv = runAndCheckSBSCmd("enablepermanent");
            } else if (cmd.equals("disable")) {
                rv = runAndCheckSBSCmd("disable");
            } else if (cmd.equals("reboot")) {
                rv = runAndCheckSBSCmd("reboot");
                while(true);
            }
        } catch(SBSException e) {

        }
        sendNewStatusIntent();
    }
    private void handleSBSSet(boolean on, int zoom, int imgDistance) {
        try {
            mSavedOn = on;
            mSavedZoom = zoom;
            mSavedImgDistance = imgDistance;
            int rv = runAndCheckSBSCmd(String.format("set %d %d %d", on ? 1 : 0, zoom,
                    (int) (imgDistance / 25.4 * mDisplayMetrics.xdpi)));
        } catch (SBSException e) {
            e.printStackTrace();
        }
        sendNewStatusIntent();
    }

    private void sendNewStatusIntent() {


        boolean installed = false;
        boolean enabled = false;
        boolean permanent = false;
        boolean loaded = false;
        boolean active = false;
        int zoom = mSavedZoom;
        int imgDistance = mSavedImgDistance;

        Intent intent = new Intent(SBS_NEW_STATUS);

        int rv = 0;
        rv = runSBSCmd("isinstalled");
        if(rv == 0 || rv == 1) {
            installed = (rv == 0);
            rv = runSBSCmd("isenabled");
        }
        if(rv == 0 || rv == 1) {
            enabled = (rv == 0);
            rv = runSBSCmd("ispermanent");
        }
        if(rv == 0 || rv == 1) {
            permanent = (rv == 0);
            rv = runSBSCmd("isloaded");
        }
        if(rv == 0 || rv == 1) {
            loaded = (rv == 0);
            rv = 0;
        }
        intent.putExtra("ERROR", rv);
        if(rv == 0) {
            intent.putExtra("INSTALLED", installed);
            intent.putExtra("ENABLED", enabled);
            intent.putExtra("PERMANENT", permanent);
            intent.putExtra("LOADED", loaded);
            intent.putExtra("ACTIVE", active);
            intent.putExtra("ZOOM", zoom);
            intent.putExtra("IMGDIST", imgDistance);
        }
        else {
            intent.putExtra("ERROR_MSG", "rv=" + rv);
        }
        getApplicationContext().sendBroadcast(intent);
    }

    private int runAsRoot(String cmd) {
        int rv = -1;
        logi("RunAsRoot: " + cmd);
        try {
            Process process =
                    Runtime.getRuntime().exec(new String[] {"su", "-mm", "-c", cmd});
            rv = process.waitFor();
            logi("runAsRoot returned " + rv);
        } catch (Exception e) {
            logi("Failed to run as root with exception: " + e.getMessage());
            Toast.makeText(this, "Failed to run as root", Toast.LENGTH_LONG).show();
        }
        return rv;
    }
    private int runSBSCmd(String cmd) {
        String path = getFilesDir().getAbsolutePath();
        return runAsRoot(path + "/sbs.sh " + cmd);
    }
    private int runAndCheckSBSCmd(String cmd) throws SBSException {
        int rv = runSBSCmd(cmd);
        if(rv != 0) {
            throw new SBSException("SBS Command failed with error " + rv, rv);
        }
        return rv;
    }
    private void logd(String msg) {
        Log.d("SBS", msg);
    }
    private void logi(String msg) {
        Log.i("SBS", msg);
    }
    private void loge(String msg)
    {
        Log.e("SBS", msg);
    }
}
