package com.paulingalls.motiontracker;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.atap.tango.ux.TangoUx;
import com.google.atap.tango.ux.TangoUxLayout;
import com.google.atap.tango.ux.UxExceptionEvent;
import com.google.atap.tango.ux.UxExceptionEventListener;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    Tango mTango;
    TangoConfig mConfig;
    TangoUx mTangoUx;
    TangoUxLayout mTangoUxLayout;

    private TextView myXTextView;
    private TextView myYTextView;
    private TextView myZTextView;

    private TextView myDeltaXTextView;
    private TextView myDeltaYTextView;
    private TextView myDeltaZTextView;

    private EditText myIpAddressEditText;
    private Button myConnectButton;
    private String myIPAddress = "";

    private Boolean myIsConnectedFlag;
    private TimerTask myTimerTask;
    private Timer myTimer;

    private OkHttpClient myHttpClient;

    private Float myCurrentXCoordinate;
    private Float myLastXCoordinate = 0f;
    private Float myCurrentYCoordinate;
    private Float myLastYCoordinate = 0f;
    private Float myCurrentZCoordinate;
    private Float myLastZCoordinate = 0f;

    private UxExceptionEventListener mUxExceptionEventListener = new UxExceptionEventListener() {
        @Override
        public void onUxExceptionEvent(UxExceptionEvent event) {
            if (event.getType() == UxExceptionEvent.TYPE_LYING_ON_SURFACE) {
                boolean isDeviceLyingOnSurface = event.getStatus() == UxExceptionEvent.STATUS_DETECTED;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mTangoUx = new TangoUx(getApplicationContext());
        mTangoUxLayout = (TangoUxLayout) findViewById(R.id.layout_tango);
        mTangoUx.setLayout(mTangoUxLayout);
        mTangoUx.setUxExceptionEventListener(mUxExceptionEventListener);

        myXTextView = (TextView) findViewById(R.id.myXTextView);
        myYTextView = (TextView) findViewById(R.id.myYTextView);
        myZTextView = (TextView) findViewById(R.id.myZTextView);

        myDeltaXTextView = (TextView) findViewById(R.id.myDeltaXTextView);
        myDeltaYTextView = (TextView) findViewById(R.id.myDeltaYTextView);
        myDeltaZTextView = (TextView) findViewById(R.id.myDeltaZTextView);

        myIpAddressEditText = (EditText) findViewById(R.id.myIpAddressTextEdit);

        myHttpClient = new OkHttpClient();

        myIsConnectedFlag = false;
        myConnectButton = (Button) findViewById(R.id.myConnectButton);

        myTimerTask = new TimerTask() {
            @Override
            public void run() {
                if (myIsConnectedFlag) {
                    final Integer theDeltaX = Math.round( 1000f * (myCurrentXCoordinate - myLastXCoordinate));
                    final Integer theDeltaY = Math.round( 1000f * (myCurrentYCoordinate - myLastYCoordinate));
                    final Integer theDeltaZ = Math.round( 1000f * (myCurrentZCoordinate - myLastZCoordinate));

                    myLastXCoordinate = myCurrentXCoordinate;
                    myLastYCoordinate = myCurrentYCoordinate;
                    myLastZCoordinate = myCurrentZCoordinate;

                    sendRequest(theDeltaX, theDeltaY, theDeltaZ);

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            myXTextView.setText(myCurrentXCoordinate.toString());
                            myYTextView.setText(myCurrentYCoordinate.toString());
                            myZTextView.setText(myCurrentZCoordinate.toString());

                            myDeltaXTextView.setText(theDeltaX.toString());
                            myDeltaYTextView.setText(theDeltaY.toString());
                            myDeltaZTextView.setText(theDeltaZ.toString());
                        }
                    });
                }
            }
        };
        myTimer = new Timer("PositionPollTimer");
        myTimer.schedule(myTimerTask, 400, 200);


        myConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                myIPAddress = myIpAddressEditText.getText().toString();
                myIpAddressEditText.setEnabled(false);

                myIsConnectedFlag = true;
            }
        });
    }

    protected void sendRequest(Integer theDeltaX, Integer theDeltaY, Integer theDeltaZ)
    {
        if (myIPAddress == null || myIPAddress.length() <= 0)
        {
            return;
        }

        // http://66.169.193.216/motor_update?up=1%2C22&down=0%2C0&left=1%2C123&right=1%2C2&front=0%2C0&back=1%2C12
        String theUrl = "http://" + myIPAddress + "/motor_update?";
        if (theDeltaX == 0 && theDeltaY == 0 && theDeltaZ == 0)
        {
            theUrl += "right=0,0&left=0,0&front=0,0&back=0,0&down=0,0&up=0,0";
        }
        else
        {
            if (theDeltaX > 0)
            {
                theUrl += "right=0,0&left=1," + Integer.valueOf(500 + theDeltaX).toString() + "&";
            }
            else
            {
                theUrl += "left=0,0&right=1," + Integer.valueOf(500 + Math.abs(theDeltaX)).toString() + "&";
            }
            if (theDeltaY > 0)
            {
                theUrl += "front=0,0&back=1," + Integer.valueOf(500 + theDeltaY).toString() + "&";
            }
            else
            {
                theUrl += "back=0,0&front=1," + Integer.valueOf(500 + Math.abs(theDeltaY)).toString() + "&";
            }
            if (theDeltaZ > 0)
            {
                theUrl += "down=0,0&up=1," + Integer.valueOf(500 + theDeltaZ).toString();
            }
            else
            {
                theUrl += "up=0,0&down=1," + Integer.valueOf(500 + Math.abs(theDeltaZ)).toString();
            }
        }


        Log.i("URL", theUrl);

        Request theRequest = new Request.Builder().url(theUrl).build();
        try {
            Response theResponse = myHttpClient.newCall(theRequest).execute();
            ResponseBody theBody = theResponse.body();
            if (theBody != null)
            {
                Log.i("HTTP", theBody.string());
            }
            else
            {
                Log.i("HTTP", "null response");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    };

    protected void onResume() {
        super.onResume();

        // Initialize Tango Service as a normal Android Service, since we call mTango.disconnect()
        // in onPause, this will unbind Tango Service, so every time when onResume gets called, we
        // should create a new Tango object.
        mTango = new Tango(MainActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready, this Runnable
            // will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only when there is no UI
            // thread changes involved.
            @Override
            public void run() {
                synchronized (MainActivity.this) {
                    mConfig = setupTangoConfig(mTango);

                    try {
                        setTangoListeners();
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    } catch (SecurityException e) {
                        Log.e(TAG, getString(R.string.permission_motion_tracking), e);
                    }
                    try {
                        TangoUx.StartParams params = new TangoUx.StartParams();
                        mTangoUx.start(params);
                        mTango.connect(mConfig);
                    } catch (TangoOutOfDateException e) {
                        if (mTangoUx != null) {
                            mTangoUx.showTangoOutOfDate();
                        }
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error) + ": " + e.getMessage(), e);
                    }
                }
            }
        });

    }

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Create a new Tango Configuration and enable the HelloMotionTrackingActivity API.
        TangoConfig config = new TangoConfig();
        config = tango.getConfig(config.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);

        // Tango service should automatically attempt to recover when it enters an invalid state.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);
        return config;
    }

    @Override
    protected void onPause() {
        super.onPause();
        synchronized (this) {
            try {
                mTango.disconnect();
                mTangoUx.stop();
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }
    }

    /**
     * Set up the callback listeners for the Tango service, then begin using the Motion
     * Tracking API. This is called in response to the user clicking the 'Start' Button.
     */
    private void setTangoListeners() {
        // Lock configuration and connect to Tango
        // Select coordinate frame pair
        final ArrayList<TangoCoordinateFramePair> framePairs =
                new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        // Listen for new Tango data
        mTango.connectListener(framePairs, new Tango.OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                if (mTangoUx != null) {
                    mTangoUx.updatePoseStatus(pose.statusCode);
                }
                logPose(pose);
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData theXyzIj) {
                if (mTangoUx != null) {
                    mTangoUx.updateXyzCount(theXyzIj.xyzCount);
                }
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                if (mTangoUx != null) {
                    mTangoUx.updateTangoEvent(event);
                }
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData tangoPointCloudData) {

            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // We are not using onFrameAvailable for this application.
            }
        });
    }

    /**
     * Log the Position and Orientation of the given pose in the Logcat as information.
     *
     * @param pose the pose to log.
     */
    private void logPose(TangoPoseData pose) {

        final float translation[] = pose.getTranslationAsFloats();

        myCurrentXCoordinate = new Float(translation[0]);
        myCurrentYCoordinate = new Float(translation[1]);
        myCurrentZCoordinate = new Float(translation[2]);
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
