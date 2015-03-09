package com.frma.sbs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class SBSException extends Exception {
    public int mErrcode;
    SBSException(String msg, int errcode) {
        super(msg);
        mErrcode = errcode;
    }
    public int getErrCode() {
        return mErrcode;
    }
}

public class MainActivity extends Activity implements
        View.OnClickListener,
        CompoundButton.OnCheckedChangeListener,
        SeekBar.OnSeekBarChangeListener
{
    private Button mInstallBtn;
    private Button mRebootBtn;
    private CheckBox mLoadNextCB;
    private CheckBox mPermanentCB;
    private TextView mCurrentStatusTV;
    private ToggleButton mActivateTB;
    private SeekBar mZoomSeekBar;
    private TextView mZoomFactor;
    private SeekBar mImgDistSeekBar;
    private TextView mImgDistValue;

    private ProgressDialog mProgress;

    private boolean mInstalled;
    private boolean mEnabled;
    private boolean mPermanent;
    private boolean mLoaded;
    private boolean mActive;
    private int mZoom;
    private int mImgDist;
    private BroadcastReceiver mBReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get screen width and height
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        mInstallBtn = (Button)findViewById(R.id.install);
        mRebootBtn =  (Button)findViewById(R.id.reboot);
        mLoadNextCB = (CheckBox)findViewById(R.id.loadNextBoot);
        mPermanentCB = (CheckBox)findViewById(R.id.enablePermanent);
        mCurrentStatusTV = (TextView)findViewById(R.id.activeStatus);
        mActivateTB = (ToggleButton)findViewById(R.id.activateTB);
        mZoomSeekBar = (SeekBar) findViewById(R.id.zoomSeekBar);
        mZoomFactor = (TextView) findViewById(R.id.zoomValue);

        mImgDistSeekBar = (SeekBar) findViewById(R.id.marginSeekBar);
        mImgDistValue = (TextView) findViewById(R.id.marginValue);

        mInstallBtn.setOnClickListener(this);
        mRebootBtn.setOnClickListener(this);

        mLoadNextCB.setOnCheckedChangeListener(this);
        mPermanentCB.setOnCheckedChangeListener(this);

        mActivateTB.setOnCheckedChangeListener(this);

        mZoomSeekBar.setOnSeekBarChangeListener(this);

        mImgDistSeekBar.setOnSeekBarChangeListener(this);
        mImgDistSeekBar.setMax((int) ((float) displayMetrics.heightPixels / displayMetrics.ydpi * 25.4));

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Configuring...");
        copyAssets("armeabi");

    }

    @Override
    protected void onResume() {
        super.onResume();
        mBReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(Service.SBS_NEW_STATUS.equals(intent.getAction())) {
                    handleStatusIntent(intent);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Service.SBS_NEW_STATUS);
        registerReceiver(mBReceiver, filter);
        mProgress.show();
        Service.ctrlSBS(this, "ping");
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBReceiver);
    }

    private boolean mInItemUpdate = false;

    private void handleStatusIntent(Intent intent) {
        mProgress.dismiss();
        logi("Handle status intent");
        mInstalled = intent.getBooleanExtra("INSTALLED", false);
        mEnabled   = intent.getBooleanExtra("ENABLED", false);
        mPermanent = intent.getBooleanExtra("PERMANENT", false);
        mLoaded    = intent.getBooleanExtra("LOADED", false);
        mActive    = intent.getBooleanExtra("ACTIVE", false);
        mZoom      = intent.getIntExtra("ZOOM", 100);
        mImgDist   = intent.getIntExtra("IMGDIST", 60);

        updateUI();
    }

    private void updateUI() {
        mInItemUpdate = true;
        if (mInstalled)
            mInstallBtn.setText("Uninstall");
        else
            mInstallBtn.setText("Install");
        mLoadNextCB.setChecked(mEnabled);
        mPermanentCB.setChecked(mPermanent);
        String statusText = "SBS ";
        statusText += (mInstalled ?
                        ("is installed" + (mLoaded ? " and loaded": " but not loaded")) :
                               "is not installed");
        mCurrentStatusTV.setText(statusText);
        mZoomSeekBar.setEnabled(mLoaded);
        mZoomSeekBar.setProgress(mZoom);
        mImgDistSeekBar.setEnabled(mLoaded);
        mImgDistSeekBar.setProgress(mImgDist);
        mActivateTB.setEnabled(mLoaded);
        mInItemUpdate = false;
    }
    @Override
    public void onClick(View v) {
        if(v.equals(mInstallBtn)) {
            if (mInstalled)
                showUninstallDlg();
            else
                showInstallDlg();
        }
        else if(v.equals(mRebootBtn)) {
            showRebootDlg();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if(mInItemUpdate)
            return;
        if(buttonView.equals(mLoadNextCB)) {
            mProgress.show();
            if (mEnabled) {
                Service.ctrlSBS(this, "disable");
            } else {
                Service.ctrlSBS(this, "enablenext");
            }
        }
        if(buttonView.equals(mPermanentCB)) {
            mProgress.show();
            if (mPermanent) {
                Service.ctrlSBS(this, "disable");
            } else {
                Service.ctrlSBS(this, "enablepermanent");
            }
        }
        if(buttonView.equals(mActivateTB)) {
            mProgress.show();
            Service.setSBS(this, isChecked, mZoom, mImgDist);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
        if (seekBar == mImgDistSeekBar) {
            mImgDistValue.setText("" + progress + " mm");
        }
        else if(seekBar == mZoomSeekBar) {
            mZoomFactor.setText("" + 100*progress/255 + "%");
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if(mInItemUpdate)
            return;
        if (seekBar == mImgDistSeekBar) {
            mProgress.show();
            Service.setSBS(this, mActive, mZoom, seekBar.getProgress());
        }
        else if(seekBar == mZoomSeekBar) {
            mProgress.show();
            Service.setSBS(this, mActive, seekBar.getProgress(), mImgDist);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void showUninstallDlg() {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Uninstall and reboot device ?")
                .setMessage("Do you want to uninstall SBS support and restart the device")
                .setPositiveButton("Go", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mProgress.show();
                        Service.ctrlSBS(MainActivity.this, "uninstall");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void showInstallDlg() {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Install and restart UI ?")
                .setMessage("Do you want to install SBS support and restart the device")
                .setPositiveButton("Go", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mProgress.show();
                        Service.ctrlSBS(MainActivity.this, "install");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void showRebootDlg() {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Reboot device ?")
                .setMessage("Are you sure you want to reboot this device")
                .setPositiveButton("Go", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mProgress.show();
                        Service.ctrlSBS(MainActivity.this, "reboot");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    // Asset stuff
    private void copyAssets(String path) {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list(path);
        } catch (IOException e) {
            loge("Failed to get asset file list.");
        }
        for(String filename : files) {
            InputStream in = null;
            OutputStream out = null;
            try {

                in = assetManager.open(path + "/" + filename);
                File outFile = new File(getFilesDir(), filename);
                out = new FileOutputStream(outFile);
                logd("copy " + outFile);
                copyFile(in, out);
                in.close();
                in = null;
                out.flush();
                out.close();
                out = null;
                Runtime.getRuntime().exec( "chmod 755 " + outFile.getAbsolutePath());
            } catch(IOException e) {
                loge("Failed to copy asset file: " + filename);
            }
        }
    }
    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    // Logg stuff

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

;
;
    private void sendReport() {
        final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
        emailIntent.setType("plain/text");
        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL,
                new String[] { "fredrik.markstrom@gmail.com" });
        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "A log from SBS");
        Uri uri = Uri.parse("file:///"+getFilesDir()+"/sbs-log.txt");
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
        emailIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                "This is a log from SBS");
        this.startActivity(Intent.createChooser(emailIntent, "Sending email..."));

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.sendLog) {
            sendReport();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
