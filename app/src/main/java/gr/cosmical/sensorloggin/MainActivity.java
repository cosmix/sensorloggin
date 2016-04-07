package gr.cosmical.sensorloggin;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private Messenger mService = null;
    private boolean mBound = false;


    // ServiceMessenger

    //setting reply messenger and handler
    Messenger theSM = new Messenger(new ServiceMsgHandler());

    class ServiceMsgHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {

            // deserialize the data.
            HashMap<Integer, Object> sensorData = (HashMap<Integer, Object>) msg.getData().getSerializable("sensorUpdates");

            if (sensorData != null) {
                for (Map.Entry<Integer, Object> datum : sensorData.entrySet()) {


                    if (datum.getValue().getClass() != SensorEvent.class)
                        return;


                    StringBuilder sb = new StringBuilder();
                    float[] values = ((SensorEvent) datum.getValue()).values;
                    for (int x = 0; x < values.length; x++) {
                        if (x > 0)
                            sb.append("\n");

                        sb.append(values[x]);
                    }

                    switch (datum.getKey()) {

                        case Sensor.TYPE_ACCELEROMETER:

                            ((TextView) findViewById(R.id.accelValues)).setText(sb.toString());
                            break;
                        case Sensor.TYPE_GYROSCOPE:
                            ((TextView) findViewById(R.id.gyroValues)).setText(sb.toString());
                            break;
                    }
                }
            }


            super.handleMessage(msg);
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = new Messenger(service);
            mBound = true;
            Message msg = Message.obtain(null, 2, 1, 0);
            msg.replyTo = theSM;
            try {
                if (mService != null)
                    mService.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };


    private void manageStartStopServiceButton() {
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);

        if (isServiceRunning(SensorLoggingService.class)) {
            fab.setImageResource(getResources().getIdentifier("ic_fiber_manual_record_white_24dp", "drawable", getPackageName()));
        } else {
            fab.setImageResource(getResources().getIdentifier("ic_stop_white_24dp", "drawable", getPackageName()));
        }
    }

    private void startStopService() {

        MainActivity that = this;
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);

        if (isServiceRunning(SensorLoggingService.class)) {
            Intent theIntent = new Intent(that, SensorLoggingService.class);
            stopService(theIntent);
            if (mBound) {
                unbindService(mConnection);
                mBound = false;
            }


            fab.setImageResource(getResources().getIdentifier("ic_fiber_manual_record_white_24dp", "drawable", getPackageName()));

        } else {
            Intent theIntent = new Intent(that, SensorLoggingService.class);
            startService(theIntent);
            if (!mBound) {
                bindService(theIntent, mConnection, Context.BIND_AUTO_CREATE);
            }

            fab.setImageResource(getResources().getIdentifier("ic_stop_white_24dp", "drawable", getPackageName()));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        manageStartStopServiceButton();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startStopService();

            }
        });


        View.OnClickListener ocl = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBound)
                    return;

                WalkDriveMode mode;
                if (v.getId() == R.id.walkBtn) {
                    mode = WalkDriveMode.WALK;
                } else {
                    mode = WalkDriveMode.DRIVE;
                }

                Message msg = Message.obtain(null, 0, mode.ordinal(), 0);
                try {
                    mService.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };


        Button walkButton = (Button) findViewById(R.id.walkBtn);
        if (walkButton != null) {
            walkButton.setOnClickListener(ocl);
        }

        Button driveButton = (Button) findViewById(R.id.incarBtn);
        if (driveButton != null) {
            driveButton.setOnClickListener(ocl);
        }
    }


    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent theIntent = new Intent(this, SensorLoggingService.class);
        stopService(theIntent);
        if (mBound && mConnection != null) {
            unbindService(mConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        manageStartStopServiceButton();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
