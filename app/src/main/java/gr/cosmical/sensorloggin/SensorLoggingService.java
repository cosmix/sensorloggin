package gr.cosmical.sensorloggin;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SensorLoggingService extends Service implements SensorEventListener {

    private ScheduledExecutorService ste;
    private SensorManager sm;
    private Sensor gyroSensor;
    private Sensor accSensor;

    private SensorLoggerThread logThread;

    private BlockingQueue<HashMap<Integer, Object>> dataToLog;
    private HashMap<Integer, Object> sensorData;
    private BufferedWriter buf;

    private ServiceHandler theHandler;

    private Messenger mMessenger;
    private Messenger aMessenger;

    @Override
    public void onCreate() {

        ste = Executors.newScheduledThreadPool(3);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Gyro
        gyroSensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        // Accelerometer
        accSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // setup the handler.
        theHandler = new ServiceHandler(getMainLooper());
        mMessenger = new Messenger(theHandler);

    }

    /**
     * Handler
     */

    private final class ServiceHandler extends Handler {

        public ServiceHandler(Looper theLooper) {
            super(theLooper);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case 0:
                    // new mode message
                    sensorData.put(998, msg.arg1);
                    break;
                case 2:
                    // reply messenger.
                    aMessenger = msg.replyTo;
                    break;
            }

        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    /**
     * Logger
     */
    private class SensorLoggerThread extends Thread {
        private volatile boolean shuttingDown;

        @Override
        public void run() {
            HashMap<Integer, Object> datum = null;

            try {
                while (!shuttingDown) {
                    datum = dataToLog.take();
                    appendToLog(datum);
                }
            } catch (InterruptedException | IOException ignored) {
            } finally {
                shuttingDown = true;
            }
        }

        public void shutdown() {
            shuttingDown = true;
        }
    }


    private void appendToLog(HashMap<Integer, Object> sensorData) throws IOException {

        StringBuilder sb = new StringBuilder();
        sb.append(new Date().toString());

        for (Map.Entry<Integer, Object> sensorDatum : sensorData.entrySet()) {
            sb.append(",");

            if (sensorDatum.getValue().getClass() == SensorEvent.class) {
                float[] values = ((SensorEvent) sensorDatum.getValue()).values;
                for (int x = 0; x < values.length; x++) {
                    if (x > 0)
                        sb.append("|");

                    sb.append(values[x]);
                }
            } else { // it's going to be a single value.
                sb.append(sensorDatum.getValue());
            }


        }
        sb.append("\n");
        buf.write(sb.toString());
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Log file.
        try {
            buf = new BufferedWriter(new FileWriter(getExternalFilesDir(null) + "sensorlogging-" + new Date().toString() + ".log"));
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }

        dataToLog = new LinkedBlockingDeque<>();
        sensorData = new HashMap<>();
        sensorData.put(998, WalkDriveMode.UNKNOWN);

        // Start the logger thread.
        logThread = new SensorLoggerThread();
        logThread.start();

        sm.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sm.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_NORMAL);

        // set a timer to sense and write to file.
        ste.scheduleAtFixedRate(new Runnable() {
            public void run() {

                Log.d("ServiceLoggin", ".");

                try {
                    Bundle b = new Bundle();
                    b.putSerializable("sensorUpdates", sensorData);

                    Message uiMsg = Message.obtain();
                    uiMsg.setData(b);
                    if (aMessenger != null)
                        aMessenger.send(uiMsg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                // write latest sensor data to log
                try {
                    if (sensorData.size() > 0) {
                       // Log.i("SensorLogging", "got data to write.");
                        dataToLog.put(new HashMap<>(sensorData));
                    }
                } catch (InterruptedException e) {
                    // FIXME: hey!
                    e.printStackTrace();
                }
            }
        }, 0, 1, TimeUnit.SECONDS);

        return START_NOT_STICKY;
    }


    /**
     * Sensors
     **/

    @Override
    public void onSensorChanged(SensorEvent event) {
        sensorData.put(event.sensor.getType(), event);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //TODO: should we bother with this?
    }


    @Override
    public void onDestroy() {

        Log.i("SensorLogging", "Destroying service.");

        // stop sensing and close the CSV file
        sm.unregisterListener(this);
        ste.shutdown();
        logThread.shutdown();
        try {
            buf.flush();
            buf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

}
