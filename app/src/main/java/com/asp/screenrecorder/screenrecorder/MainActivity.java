package com.asp.screenrecorder.screenrecorder;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.MediaRouter;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.VideoView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 1000;
    private static final int REQUEST_PERMISSION = 1001;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private int mScreenDensity;

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaProjectionCallBack mediaProjectionCallBack;
    private MediaRecorder mediaRecorder;

    private static int DISPLAY_WIDTH = 720;
    private static int DISPLAY_HEIGHT = 1280;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    //view components
    private ToggleButton toggleButton;
    private VideoView videoView;
    private  String videoUri = "";
    private RelativeLayout rootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        //get screen
        DISPLAY_HEIGHT = metrics.heightPixels;
        DISPLAY_WIDTH = metrics.widthPixels;

        mediaRecorder = new MediaRecorder();
        mediaProjectionManager = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        //view related
        videoView = (VideoView)findViewById(R.id.videoView);
        toggleButton = (ToggleButton)findViewById(R.id.toggleButton);
        rootLayout = (RelativeLayout)findViewById(R.id.rootLayout);

        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        + ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    if(ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            ||
                            ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.RECORD_AUDIO)) {
                        toggleButton.setChecked(false);
                        Snackbar.make(rootLayout, "Permissions", Snackbar.LENGTH_INDEFINITE)
                                .setAction("ENABLE", new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        ActivityCompat.requestPermissions(MainActivity.this,
                                                new String[] {
                                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                    Manifest.permission.RECORD_AUDIO
                                        }, REQUEST_PERMISSION);
                                    }
                                }).show();
                    } else {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[] {
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.RECORD_AUDIO
                        }, REQUEST_PERMISSION);
                    }
                } else {
                    toggleScreenShare(view);
                }
            }
        });


    }

    private void toggleScreenShare(View view) {
        if(((ToggleButton)view).isChecked()) {
            initRecorder();
            recordScreen();
        } else {
            mediaRecorder.stop();
            mediaRecorder.reset();
            stopRecordScreen();
            videoView.setVisibility(View.VISIBLE);
            videoView.setVideoURI(Uri.parse(videoUri));
            videoView.start();

        }
    }



    private void initRecorder() {
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            videoUri = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) +
                    new StringBuilder("/ASP_").append(new SimpleDateFormat("dd-MM-yyyy-hh_mm_ss")
                            .format(new Date())).append("mp4").toString();
            mediaRecorder.setOutputFile(videoUri);
            mediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setVideoEncodingBitRate(512 * 1000);
            mediaRecorder.setVideoFrameRate(30);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int orientation = ORIENTATIONS.get(rotation + 90);
            mediaRecorder.setOrientationHint(orientation);
            mediaRecorder.prepare();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private void recordScreen() {
        if(mediaProjection == null) {
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
            return;
        }
        virtualDisplay = createVirtualDisplay();
        mediaRecorder.start();
    }

    private VirtualDisplay createVirtualDisplay() {
        return mediaProjection.createVirtualDisplay("MainActivity", DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaRecorder.getSurface(), null, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode != REQUEST_CODE) {
            Toast.makeText(this, "Unknown Error", Toast.LENGTH_SHORT).show();
            return;
        }

        if(resultCode != RESULT_OK) {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            toggleButton.setChecked(false);
            return;
        }

        mediaProjectionCallBack = new MediaProjectionCallBack();
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
        mediaProjection.registerCallback(mediaProjectionCallBack, null);
        virtualDisplay = createVirtualDisplay();
        mediaRecorder.start();
    }

    private class MediaProjectionCallBack extends MediaProjection.Callback {
        @Override
        public void onStop() {
            if(toggleButton.isChecked()) {
                toggleButton.setChecked(false);
                mediaRecorder.stop();
                mediaRecorder.reset();
            }

            mediaProjection = null;
            stopRecordScreen();
            super.onStop();
        }
    }

    private void destroyMediaProjection() {
        if(mediaProjection != null) {
            mediaProjection.unregisterCallback(mediaProjectionCallBack);
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private void stopRecordScreen() {
        if(virtualDisplay == null) {
            return;
        }
        virtualDisplay.release();
        destroyMediaProjection();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION: {
                if(grantResults.length > 0 && (grantResults[0] + grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                    toggleScreenShare(toggleButton);
                } else {
                    toggleButton.setChecked(false);
                    Snackbar.make(rootLayout, "Permissions", Snackbar.LENGTH_INDEFINITE)
                            .setAction("ENABLE", new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                            Manifest.permission.RECORD_AUDIO
                                    }, REQUEST_PERMISSION);
                                }
                            }).show();
                }
                return;
            }
        }
    }
}
