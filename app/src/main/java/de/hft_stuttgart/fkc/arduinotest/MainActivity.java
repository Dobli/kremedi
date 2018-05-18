package de.hft_stuttgart.fkc.arduinotest;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    /****************************/
    /* ENUMS                */
    /****************************/

    // define Units to be able to choose them later
    private enum AngleUnit {
        DEGREE(360, 60, "°", "′","″"),
        GON(400, 100, "g", "c", "");

        private static final double TICKS_PER_ROUND = 179488.0; // default: 2000

        private double wholeRound;

        private double factor;

        private double unitPerTick;

        private String symbol;

        private String symbolFirstFactor;

        private String symbolSecondFactor;

        AngleUnit(double wholeRound, double factor, String symbol, String symbolFirstFactor, String symbolSecondFactor) {
            this.wholeRound = wholeRound;
            this.factor = factor;
            this.unitPerTick = wholeRound / TICKS_PER_ROUND;
            this.symbol = symbol;
            this.symbolFirstFactor = symbolFirstFactor;
            this.symbolSecondFactor = symbolSecondFactor;
        }
    }

    /****************************/
    /* CONSTANTS                */
    /****************************/

    private static final String TAG = "MAIN_ACTIVITY";


    //private static final double DEGREES_PER_TICK = (360.0 / TICKS_PER_ROUND);

    //private static final double GONGS_PER_TICK = (400.0 / TICKS_PER_ROUND);

    /****************************/
    /* Arduino CONSTANTS        */
    /****************************/

    private static final int VENDOR_ID_ARDUINO = 0x2a03;

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    private final int BAUD_RATE = 115200;


    /****************************/
    /* Member Variables
    /****************************/

    private PendingIntent mPermissionIntent;

    private AngleUnit mUnit = AngleUnit.DEGREE;

    /****************************/
    /* Arduino Variables
    /****************************/

    private UsbManager mUsbManager;
    private UsbDevice mDevice;
    private UsbDeviceConnection mUsbConnection;
    private UsbSerialDevice mSerialDevice;

    private boolean mDeviceConnected = false;

    /****************************/
    /* UI Elements
    /****************************/

    Button mBtnStart;
    Button mBtnReset;
    TextView mCounter;
    TextView mBaseUnitValue;
    TextView mFirstSubUnitValue;
    TextView mSecondSubUnitValue;
    TextView mBaseUnit;
    TextView mFirstSubUnit;
    TextView mSecondSubUnit;
    SwitchCompat mUnitSwitch;
    TextView mRadiant;

    /****************************/
    /* Android Lifecycle methods
    /****************************/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        /*UI Elements*/
        mBtnStart = (Button) findViewById(R.id.btn_start);
        mBtnReset = (Button) findViewById(R.id.btn_reset);
        mCounter = (TextView) findViewById(R.id.tv_counter);
        mBaseUnitValue = (TextView) findViewById(R.id.tv_degree);
        mFirstSubUnitValue = (TextView) findViewById(R.id.tv_minutes);
        mSecondSubUnitValue = (TextView) findViewById(R.id.tv_seconds);
        mBaseUnit = (TextView) findViewById(R.id.tv_baseUnit);
        mFirstSubUnit = (TextView) findViewById(R.id.tv_firstSubUnit);
        mSecondSubUnit = (TextView) findViewById(R.id.tv_secondSubUnit);
        mUnitSwitch = (SwitchCompat) findViewById(R.id.sw_unit);
        mRadiant = (TextView) findViewById(R.id.tv_radiant);

        // setup to catch intent which gets called on connection of arduino
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                ACTION_USB_PERMISSION), 0);


        mBtnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkUsbDevices();
            }
        });

        mBtnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Send Reset command
                String send = "Reset";
                sendData(send.getBytes());
            }
        });
        mBtnReset.setEnabled(false);

        mUnitSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b) {
                    //Set unit to gon and hide second sub unit
                    mUnit = AngleUnit.GON;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mSecondSubUnit.setVisibility(View.GONE);
                            mSecondSubUnitValue.setVisibility(View.GONE);
                        }
                    });

                } else {
                    //Set unit to degrees and show second sub unit
                    mUnit = AngleUnit.DEGREE;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mSecondSubUnit.setVisibility(View.VISIBLE);
                            mSecondSubUnitValue.setVisibility(View.VISIBLE);
                        }
                    });
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mBaseUnit.setText(mUnit.symbol);
                        mFirstSubUnit.setText(mUnit.symbolFirstFactor);
                        mSecondSubUnit.setText(mUnit.symbolSecondFactor);
                    }
                });
            }
        });

        //Arduino
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(ACTION_USB_DETACHED);
        filter.addAction(ACTION_USB_ATTACHED);

        registerReceiver(broadcastReceiver, filter);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDeviceConnected=false;
        unregisterReceiver(broadcastReceiver);
    }

    /****************************/
    /* UI Methods          */
    /****************************/

    private CharSequence mOutputCache = "";

    private void tvAppend(CharSequence text) {
        final String ftext = text.toString();

        if(ftext.contains("\n")){
            //split
            String[] str = ftext.split("\n", -2);
            final String output = (mOutputCache + str[0]).trim();
            mOutputCache = str[1];
            final double baseUnit = convertToBaseUnit(output);
            final double radiant = convertToRadiant(baseUnit);
            final double firstSubUnit = calcFirstSubUnit(baseUnit);
            final double secondSubUnit = calcSecondSubUnit(firstSubUnit);

            runOnUiThread(new Runnable() {
                @Override public void run() {
                    mCounter.setText(output);
                    mBaseUnitValue.setText(createBaseUnitText((int) baseUnit));
                    mFirstSubUnitValue.setText(createBaseUnitText((long) firstSubUnit));
                    mSecondSubUnitValue.setText(createBaseUnitText(secondSubUnit));
                    mRadiant.setText(String.format("%.4f", radiant));
                }
            });
        } else {
            mOutputCache = mOutputCache + ftext;
        }


    }

    private double convertToBaseUnit(String output) {
        double degree;
        try{
            int outInt = Integer.parseInt(output);
            degree = ((double) outInt) * mUnit.unitPerTick;
        } catch (NumberFormatException e){
            degree = 0.0;
        }
        return degree;
    }

    private double cutDecimalBefore(double decimal){
        return (long) decimal;
    }

    private double cutDecimalAfter(double decimal){
        return decimal - (long) decimal;
    }

    private double calcFirstSubUnit(double baseUnit){
        return (cutDecimalBefore(baseUnit * mUnit.factor) % mUnit.factor) + cutDecimalAfter(baseUnit*mUnit.factor);
    }

    private double calcSecondSubUnit(double firstSubUnit){
        return (cutDecimalBefore(firstSubUnit * mUnit.factor) % mUnit.factor) + cutDecimalAfter(firstSubUnit*mUnit.factor);
    }

    private String createBaseUnitText(double degrees){
        return String.format("%.0f",degrees);
    }

    private double convertToRadiant(double degree){
        return ((2*Math.PI)/mUnit.wholeRound)*degree;
    }


    /****************************/
    /* Arduino Methods          */
    /****************************/

    /**
     * Method to send data to Arduino
     * @param data bytes you want to send
     */
    private void sendData(byte[] data){
        if(mSerialDevice!=null){
            mSerialDevice.write(data);
        }
    }

    /**
     * Callback which will be executed if data is read from Arduino
     */
    UsbSerialInterface.UsbReadCallback showReadData = new UsbSerialInterface.UsbReadCallback() {
        //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            try {
                String data = new String(arg0, "UTF-8");
                data.concat("/n");
                tvAppend(data);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

    /**
     * Check if an arduino can be found as usb device, if found ask for access permissions and set as mDevice, else set mDevice to null
     */
    public boolean checkUsbDevices(){
        boolean requestetDevices = false;
        HashMap<String, UsbDevice> usbDevices = mUsbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                mDevice = entry.getValue();
                int deviceVID = mDevice.getVendorId();
                //Arduino Vendor ID
                if (deviceVID == VENDOR_ID_ARDUINO) {
                    Log.d("SERIAL", "ARDUINO FOUND");
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                            new Intent(ACTION_USB_PERMISSION), 0);
                    mUsbManager.requestPermission(mDevice, pi);
                    keep = false;
                    requestetDevices = true;
                    Toast.makeText(this, "Permission is "+mUsbManager.hasPermission(mDevice)
                            , Toast.LENGTH_SHORT).show();
                    mDeviceConnected=true;
                    mBtnReset.setEnabled(true);
                } else {
                    Log.d("SERIAL", "ARDUINO NOT FOUND");
                    mUsbConnection = null;
                    mDevice = null;
                }

                if (!keep)
                    break;
            }
        } else {
            Toast.makeText(this, "Device List is empty", Toast.LENGTH_SHORT).show();
        }
        return requestetDevices;
    }

    /****************************/
    /* Brodacast receivers
    /****************************/

    /**
     * Brodcast receiver which handles USB brodcasts
     * It handles USB attachments (ACTION_USB_ATTACHED) by using @checkUsbDevices() to check if arduino is connected and asks for permissions
     * For USB detachments (ACTION_USB_DETACHED) the connection will be closed.
     * If access got granted to a usb device (ACTION_USB_PERMISSION) the serial connection will be established and connection settings will get set
     */
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()){
                case ACTION_USB_PERMISSION:
                    Toast.makeText(MainActivity.this, "USB Permission granted" , Toast.LENGTH_SHORT).show();
                    boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                    if (granted) { // user accepted usb connection, try to open the device
                        mUsbConnection = mUsbManager.openDevice(mDevice);
                        mSerialDevice = UsbSerialDevice.createUsbSerialDevice(mDevice, mUsbConnection);
                        if (mSerialDevice != null) {
                            if (mSerialDevice.open()) { //Set Serial Connection Parameters.
                                mSerialDevice.setBaudRate(BAUD_RATE);
                                mSerialDevice.setDataBits(UsbSerialInterface.DATA_BITS_8);
                                mSerialDevice.setStopBits(UsbSerialInterface.STOP_BITS_1);
                                mSerialDevice.setParity(UsbSerialInterface.PARITY_NONE);
                                mSerialDevice.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                                mSerialDevice.read(showReadData);
                                Toast.makeText(MainActivity.this, "Connection opened", Toast.LENGTH_SHORT).show();
                            } else {
                                Log.d("SERIAL", "PORT NOT OPEN");
                            }
                        } else {
                            Log.d("SERIAL", "PORT IS NULL");
                        }
                    } else {
                        Log.d("SERIAL", "PERM NOT GRANTED");
                    }
                    break;
                case ACTION_USB_ATTACHED:
                    if(!mDeviceConnected)
                        checkUsbDevices();
                    break;
                case ACTION_USB_DETACHED:
                    mDeviceConnected = false;
                    mSerialDevice.close();
                    mBtnReset.setEnabled(false);
                    break;
            }
        }
    };

}

