package com.example.metaldetector;

import android.app.Activity;
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
import android.media.ToneGenerator;
import android.media.AudioManager;

public class MainActivity extends Activity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor magneticSensor;
    private TextView textView;
    private Button toggleButton;

    private boolean detecting = false;

    // Low-pass filter (ہلچل کم کرنے کے لیے)
    private float alpha = 0.1f;
    private double filtered = 0.0;

    private long lastVibe = 0L;
    private ToneGenerator toneGen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);
        toggleButton = findViewById(R.id.toggleButton);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);

        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!detecting) startDetecting();
                else stopDetecting();
            }
        });
    }

    private void startDetecting() {
        if (magneticSensor != null) {
            sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI);
            detecting = true;
            toggleButton.setText("Stop Detection");
        } else {
            textView.setText("Magnetometer not available");
        }
    }

    private void stopDetecting() {
        detecting = false;
        try { sensorManager.unregisterListener(this); } catch (Exception ignored) {}
        toggleButton.setText("Start Detection");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (detecting) sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!detecting) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double magnitude = Math.sqrt(x*x + y*y + z*z);

        // Low-pass filter
        filtered = alpha * magnitude + (1 - alpha) * filtered;

        textView.setText(String.format("Magnetic Field Strength: %.1f µT", filtered));

        // Threshold — اس سے اوپر وائبریٹ/بیپ
        double threshold = 80.0;
        if (filtered > threshold) {
            long now = System.currentTimeMillis();
            if (now - lastVibe > 300) {
                Vibrator vb = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vb != null) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        vb.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vb.vibrate(120);
                    }
                }
                try { toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120); } catch (Exception ignored) {}
                lastVibe = now;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
