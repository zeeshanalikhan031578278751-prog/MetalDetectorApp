package com.example.metaldetector;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.animation.ValueAnimator;

/**
 * سادہ میٹل ڈیٹیکٹر:
 * - Start/Stop بٹن
 * - میگنیٹک فیلڈ میگنی ٹیوڈ دکھاتا ہے (µT)
 * - حد (threshold) کراس ہونے پر ہلکی وائبریشن + پلْس اینیمیشن
 */
public class MainActivity extends Activity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor magneticSensor;

    private TextView magneticFieldText;
    private Button toggleButton;

    private boolean detecting = false;

    // reading کو ہموار کرنے کیلئے ایک سادہ low-pass فلٹر
    private static final float ALPHA = 0.15f;
    private float[] last = new float[]{0f, 0f, 0f};

    // threshold اور وائبریشن سیٹنگز
    private static final float THRESHOLD_MICRO_TESLA = 60f; // چاہیں تو ایڈجسٹ کریں
    private static final long VIBRATE_MS = 40;

    private Vibrator vibrator;
    private ValueAnimator pulseAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // activity_main.xml لازماً res/layout میں ہو

        magneticFieldText = findViewById(R.id.magneticFieldText);
        toggleButton = findViewById(R.id.toggleButton);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // پلْس اینیمیشن (ہائی ریڈنگ پر شروع/بند ہوگی)
        final View pulseTarget = magneticFieldText;
        pulseAnimator = ValueAnimator.ofFloat(1f, 1.12f);
        pulseAnimator.setDuration(350);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.addUpdateListener(anim -> {
            float s = (float) anim.getAnimatedValue();
            pulseTarget.setScaleX(s);
            pulseTarget.setScaleY(s);
        });

        toggleButton.setOnClickListener(v -> {
            if (detecting) {
                stopDetection();
            } else {
                startDetection();
            }
        });

        // اگر سینسر دستیاب نہیں تو پیغام
        if (magneticSensor == null) {
            magneticFieldText.setText("Magnetometer not available");
            toggleButton.setEnabled(false);
        }
    }

    private void startDetection() {
        if (magneticSensor == null) return;
        detecting = true;
        toggleButton.setText("Stop Detection");
        // سینسر فریکوئنسی: UI اپ ڈیٹ کیلئے GAME کافی ہے
        sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    private void stopDetection() {
        detecting = false;
        toggleButton.setText("Start Detection");
        sensorManager.unregisterListener(this);
        if (pulseAnimator.isRunning()) pulseAnimator.cancel();
        magneticFieldText.setScaleX(1f);
        magneticFieldText.setScaleY(1f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (detecting && magneticSensor != null) {
            sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (magneticSensor != null) sensorManager.unregisterListener(this);
        if (pulseAnimator.isRunning()) pulseAnimator.cancel();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_MAGNETIC_FIELD) return;

        // low-pass filter
        last[0] = last[0] + ALPHA * (event.values[0] - last[0]);
        last[1] = last[1] + ALPHA * (event.values[1] - last[1]);
        last[2] = last[2] + ALPHA * (event.values[2] - last[2]);

        float x = last[0];
        float y = last[1];
        float z = last[2];

        double magnitude = Math.sqrt(x * x + y * y + z * z);
        String text = String.format("Magnetic Field Strength: %.1f µT", magnitude);
        magneticFieldText.setText(text);

        if (magnitude >= THRESHOLD_MICRO_TESLA) {
            vibrateOnce();
            if (!pulseAnimator.isRunning()) pulseAnimator.start();
        } else {
            if (pulseAnimator.isRunning()) pulseAnimator.cancel();
            magneticFieldText.setScaleX(1f);
            magneticFieldText.setScaleY(1f);
        }
    }

    private void vibrateOnce() {
        if (vibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(VIBRATE_MS);
            }
        } catch (SecurityException ignore) {
            // اگر Manifest میں VIBRATE موجود نہ ہو
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // not used
    }
}
