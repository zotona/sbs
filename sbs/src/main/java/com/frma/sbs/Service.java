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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

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
    private SharedPreferences mPrefs;

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
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (SBS_CTRL.equals(action)) {
                String cmd = intent.getStringExtra("CMD");
                handleSBSControl(cmd);
            } else if (SBS_SET.equals(action)) {
                boolean on = intent.getBooleanExtra("ON", mPrefs.getBoolean("ON", false));
                int zoom = intent.getIntExtra("ZOOM", mPrefs.getInt("zoom", 100));
                int imgDistance = intent.getIntExtra("IMGDIST", mPrefs.getInt("imgdist", 60));
                handleSBSSet(on, zoom, imgDistance);
            }
        }
    }

    private void handleSBSControl(String cmd) {
        String o;
        try {
            if (cmd.equals("install")) {
                o = runAndCheckSBSCmd("install");
            } else if (cmd.equals("uninstall")) {
                o = runAndCheckSBSCmd("uninstall");
            } else if (cmd.equals("enablenext")) {
                o = runAndCheckSBSCmd("enable");
            } else if (cmd.equals("enablepermanent")) {
                o = runAndCheckSBSCmd("enablepermanent");
            } else if (cmd.equals("disable")) {
                o = runAndCheckSBSCmd("disable");
            } else if (cmd.equals("reboot")) {
                o = runAndCheckSBSCmd("reboot");
                while(true);
            }
        } catch(SBSException e) {

        }
        sendNewStatusIntent();
    }
    private void handleSBSSet(boolean on, int zoom, int imgDistance) {
        try {
            mPrefs.edit().putInt("zoom" ,zoom).commit();
            mPrefs.edit().putInt("imgdist", imgDistance).commit();

            String o = runAndCheckSBSCmd(String.format("set %d %d %d", on ? 1 : 0, zoom,
                                        (int)(imgDistance / 25.4 * mDisplayMetrics.xdpi)));
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
        int zoom = mPrefs.getInt("zoom", 200);
        int imgDistance = mPrefs.getInt("imgdist", 60);

        Intent intent = new Intent(SBS_NEW_STATUS);

        String o = runSBSCmd("isinstalled");
        if(o.startsWith("RESULT:")) {
            installed = o.contains("YES");
            o = runSBSCmd("isenabled");
        }
        if(o.startsWith("RESULT:")) {
            enabled = o.contains("YES");
            o = runSBSCmd("ispermanent");
        }
        if(o.startsWith("RESULT:")) {
            permanent = o.contains("YES");
            o = runSBSCmd("isloaded");
        }
        if(o.startsWith("RESULT:")) {
            loaded = o.contains("YES");
        }
        if(o.startsWith("RESULT:")) {
            intent.putExtra("INSTALLED", installed);
            intent.putExtra("ENABLED", enabled);
            intent.putExtra("PERMANENT", permanent);
            intent.putExtra("LOADED", loaded);
            intent.putExtra("ACTIVE", active);
            intent.putExtra("ZOOM", zoom);
            intent.putExtra("IMGDIST", imgDistance);
        }
        else {
            intent.putExtra("ERROR_MSG", o);
        }
        getApplicationContext().sendBroadcast(intent);
    }
    public static String readAll(InputStream stream) throws IOException, UnsupportedEncodingException {
        int n = 0;
        char[] buffer = new char[1024 * 4];
        InputStreamReader reader = new InputStreamReader(stream, "UTF8");
        StringWriter writer = new StringWriter();
        while (-1 != (n = reader.read(buffer))) writer.write(buffer, 0, n);
        return writer.toString();
    }
    private String runAsRoot(String cmd) {
        String output;
        logi("RunAsRoot: " + cmd);
        try {
            Process process =
                    Runtime.getRuntime().exec(new String[] {"su", "-mm", "-c", cmd});
            output = readAll(process.getInputStream());
            int rv = process.waitFor();
            logi("Got output: " + output + " of len " + output.length());
            logi("runAsRoot returned " + rv);
        } catch (Exception e) {
            logi("Failed to run as root with exception: " + e.getMessage());
            Toast.makeText(this, "Failed to run as root", Toast.LENGTH_LONG).show();
            output = "Exception while executing external command:" + e.getMessage();
        }
        return output;
    }
    private String runSBSCmd(String cmd) {
        String path = getFilesDir().getAbsolutePath();
        return runAsRoot(path + "/sbs.sh " + cmd);
    }
    private String runAndCheckSBSCmd(String cmd) throws SBSException {
        String o = runSBSCmd(cmd);
        if(o.startsWith("ERROR:")) {
            throw new SBSException("SBS Command failed with error " + o,-1);
        }
        return o;
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
