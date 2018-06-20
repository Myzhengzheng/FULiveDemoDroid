package com.faceunity.fulivedemo;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.faceunity.fulivedemo.encoder.TextureMovieEncoder;
import com.faceunity.fulivedemo.ui.RecordBtn;

import java.io.File;

/**
 * Base Activity, 主要封装FUBeautyActivity与FUEffectActivity的公用界面与方法
 * CameraRenderer相关回调实现
 * Created by tujh on 2018/1/31.
 */
public abstract class FUBaseUIActivity extends AppCompatActivity
        implements View.OnClickListener,
        CameraRenderer.OnCameraRendererStatusListener,
        SensorEventListener {
    public final static String TAG = FUBaseUIActivity.class.getSimpleName();

    protected GLSurfaceView mGLSurfaceView;
    protected CameraRenderer mCameraRenderer;
    protected boolean isDoubleInputType = true;
    protected RadioGroup mInputTypeRadioGroup;
    private CheckBox mDebugBox;
    protected TextView mDebugText;
    protected TextView mIsTrackingText;
    protected TextView mEffectDescription;
    protected RecordBtn mTakePicBtn;
    protected ViewStub mBottomViewStub;
    protected CheckBox mHeightCheckBox;

    private SensorManager mSensorManager;
    private Sensor mSensor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fu_base);

        //屏幕保持常量以及设置亮度
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = 0.7f;
        getWindow().setAttributes(params);

        mGLSurfaceView = (GLSurfaceView) findViewById(R.id.fu_base_gl_surface);
        mGLSurfaceView.setEGLContextClientVersion(2);
        mCameraRenderer = new CameraRenderer(this, mGLSurfaceView, this);
        mGLSurfaceView.setRenderer(mCameraRenderer);
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mInputTypeRadioGroup = (RadioGroup) findViewById(R.id.fu_base_input_type_radio_group);

        mDebugBox = (CheckBox) findViewById(R.id.fu_base_debug);
        mDebugText = (TextView) findViewById(R.id.fu_base_debug_text);
        mDebugBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDebugText.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });

        mIsTrackingText = (TextView) findViewById(R.id.fu_base_is_tracking_text);
        mEffectDescription = (TextView) findViewById(R.id.fu_base_effect_description);
        mTakePicBtn = (RecordBtn) findViewById(R.id.fu_base_take_pic);
        mTakePicBtn.setOnRecordListener(new RecordBtn.OnRecordListener() {
            @Override
            public void takePic() {
                mCameraRenderer.takePic();
            }

            @Override
            public void startRecord() {
                mCameraRenderer.startRecording(new TextureMovieEncoder.OnEncoderStatusUpdateListener() {
                    @Override
                    public void onStartSuccess() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.e(TAG, "start encoder success");
                                mTakePicBtn.setSecond(0);
                            }
                        });
                    }

                    @Override
                    public void onStopSuccess(final File outFile) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.e(TAG, "stop encoder success");
                                Toast.makeText(FUBaseUIActivity.this, "保存视频成功！", Toast.LENGTH_SHORT).show();
                                mTakePicBtn.setSecond(0);
                                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outFile)));
                            }
                        });
                    }

                    @Override
                    public void onTimestampListener(long timestamp) {
                        mTakePicBtn.setSecond(timestamp);
                    }
                });
            }

            @Override
            public void stopRecord() {
                mCameraRenderer.stopRecording();
            }
        });
        mHeightCheckBox = (CheckBox) findViewById(R.id.fu_base_height);

        mBottomViewStub = (ViewStub) findViewById(R.id.fu_base_bottom);
        mBottomViewStub.setInflatedId(R.id.fu_base_bottom);
        onCreate();
        mCameraRenderer.onCreate();
    }

    protected abstract void onCreate();

    @Override
    protected void onResume() {
        super.onResume();
        mCameraRenderer.onResume();
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraRenderer.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraRenderer.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fu_base_back:
                onBackPressed();
                break;
            case R.id.fu_base_camera_change:
                mCameraRenderer.changeCamera();
                break;
        }
    }

    private Runnable effectDescriptionHide = new Runnable() {
        @Override
        public void run() {
            mEffectDescription.setText("");
            mEffectDescription.setVisibility(View.INVISIBLE);
        }
    };

    protected void showDescription(String str, int time) {
        if (TextUtils.isEmpty(str)) {
            return;
        }
        mEffectDescription.removeCallbacks(effectDescriptionHide);
        mEffectDescription.setVisibility(View.VISIBLE);
        mEffectDescription.setText(str);
        mEffectDescription.postDelayed(effectDescriptionHide, time);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            if (Math.abs(x) > 3 || Math.abs(y) > 3) {
                if (Math.abs(x) > Math.abs(y)) {
                    if (x > 0) {
                        onSensorChanged(0);
                    } else {
                        onSensorChanged(180);
                    }
                } else {
                    if (y > 0) {
                        onSensorChanged(90);
                    } else {
                        onSensorChanged(270);
                    }
                }
            }
        }
    }

    protected abstract void onSensorChanged(int rotation);

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
