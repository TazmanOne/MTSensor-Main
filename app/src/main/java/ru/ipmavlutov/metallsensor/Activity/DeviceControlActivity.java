package ru.ipmavlutov.metallsensor.Activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import ru.ipmavlutov.metallsensor.DeviceConnector;
import ru.ipmavlutov.metallsensor.DeviceData;
import ru.ipmavlutov.metallsensor.Graphs.Graph;
import ru.ipmavlutov.metallsensor.R;

public class DeviceControlActivity extends MainActivity {
    private static final String DEVICE_NAME = "DEVICE_NAME";
    private static String MSG_NOT_CONNECTED;
    private static String MSG_CONNECTING;
    private static String MSG_CONNECTED;
    public String MAC_ADDRESS;


    public TextView temperature_value;
    public TextView signal_value;
    public TextView super_signal_value;


    private static DeviceConnector connector;
    private static BluetoothResponseHandler mHandler;
    private boolean change_menu;


    private EditText editText;
    public double Z;
    private String p0_value;
    private TextView current_value;
    private TextView test_view;

    DBHelper dbHelper;
    Timer tm;
    MyTimerTask myTT;
    private String DB_PATH;
    private static String DB_NAME;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor sharedPreferencesEditor;
    private static final int PREFERENCE_MODE_PRIVATE = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (mHandler == null) mHandler = new BluetoothResponseHandler(this);
        else mHandler.setTarget(this);
        MSG_NOT_CONNECTED = getString(R.string.msg_not_connected);
        MSG_CONNECTING = getString(R.string.msg_connecting);
        MSG_CONNECTED = getString(R.string.msg_connected);
        change_menu = false;
        DB_NAME = "myDB";

        Z = 96.0;

        try {
            writeToSD();
        } catch (IOException e) {
            e.printStackTrace();
        }


        dbHelper = new DBHelper(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            DB_PATH = getBaseContext().getFilesDir().getAbsolutePath().replace("files", "databases") + File.separator;
        } else {
            DB_PATH = getBaseContext().getFilesDir().getPath() + getBaseContext().getPackageName() + "/databases/";
        }


        if (isConnected() && (savedInstanceState != null)) {
            setDeviceName(savedInstanceState.getString(DEVICE_NAME));
        } else getSupportActionBar().setSubtitle(MSG_NOT_CONNECTED);


    }

    private boolean isConnected() {
        return (connector != null) && (connector.getState() == DeviceConnector.STATE_CONNECTED);
    }

    // ==========================================================================
    private void stopConnection() {
        if (connector != null) {
            connector.stop();
            connector = null;
        }
    }

    // ==========================================================================
    private void writeToSD() throws IOException {
        File sd = Environment.getExternalStorageDirectory();

        if (sd.canWrite()) {
            String currentDBPath = DB_NAME;
            String backupDBPath = "backup_name.db";
            File currentDB = new File(DB_PATH, currentDBPath);
            File backupDB = new File(sd, backupDBPath);

            if (currentDB.exists()) {
                FileChannel src = new FileInputStream(currentDB).getChannel();
                FileChannel dst = new FileOutputStream(backupDB).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();
            }
        }
    }

    /**
     * Список устройств для подключения
     */
    private void startDeviceListActivity() {
        stopConnection();
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }

    // ============================================================================
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == RESULT_OK) {


                    String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    MAC_ADDRESS = address;

                    BluetoothDevice device = btAdapter.getRemoteDevice(address);
                    if (super.isAdapterReady() && (connector == null)) setupConnector(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                super.pendingRequestEnableBt = false;
                if (resultCode != RESULT_OK) {

                }
                break;
        }
    }

    // ==========================================================================
    private void setupConnector(BluetoothDevice connectedDevice) {
        stopConnection();
        try {
            String emptyName = getString(R.string.empty_device_name);
            DeviceData data = new DeviceData(connectedDevice, emptyName);
            connector = new DeviceConnector(data, mHandler);
            connector.connect();
        } catch (IllegalArgumentException | IOException ignored) {

        }
    }

    // ==========================================================================
    void setDeviceName(String deviceName) {
        getSupportActionBar().setSubtitle(deviceName);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (change_menu) {
            getMenuInflater().inflate(R.menu.menu_device_control, menu);


        } else {
            getMenuInflater().inflate(R.menu.menu_main, menu);

        }
        return true;
    }


    // ==========================================================================

    /**
     * Обработчик приёма данных от bluetooth-потока
     */

    public class BluetoothResponseHandler extends Handler {


        private WeakReference<DeviceControlActivity> mActivity;


        public BluetoothResponseHandler(DeviceControlActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        public void setTarget(DeviceControlActivity target) {
            mActivity.clear();
            mActivity = new WeakReference<>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            DeviceControlActivity activity = mActivity.get();
            if (activity != null) {


                switch (msg.what) {

                    case MESSAGE_STATE_CHANGE:


                        final ActionBar bar = activity.getSupportActionBar();


                        switch (msg.arg1) {


                            case DeviceConnector.STATE_CONNECTED:

                                assert bar != null;
                                bar.setSubtitle(MSG_CONNECTED);

                                change_activity();
                                tm = new Timer();
                                myTT = new MyTimerTask();

                                tm.schedule(myTT, 3000, 1200000);


                            case DeviceConnector.STATE_CONNECTING:
                                assert bar != null;
                                bar.setSubtitle(MSG_CONNECTING);
                                break;
                            case DeviceConnector.STATE_NONE:

                                 assert bar != null;
                                bar.setSubtitle(MSG_NOT_CONNECTED);
                                if (change_menu) {
                                    myTT.cancel();
                                    tm.cancel();
                                }
                                change_menu = false;
                                invalidateOptionsMenu();
                                setContentView(R.layout.activity_main);


                                break;

                        }
                        break;


                    case MESSAGE_READ:
                        /*final String readMessage = (String) msg.obj;
                        if (readMessage != null) {
                            activity.appendLog(readMessage, false, false, activity.needClean);
                        }*/
                        break;

                    case MESSAGE_DEVICE_NAME:
                        activity.setDeviceName((String) msg.obj);
                        break;

                    case MESSAGE_WRITE:
                        // stub
                        break;

                    case MESSAGE_TOAST:
                        Toast.makeText(DeviceControlActivity.this, "Устройство " + msg.obj + " отключено", Toast.LENGTH_LONG).show();
                        setContentView(R.layout.activity_main);


                        break;
                    case DATA:
                        byte[] a = (byte[]) msg.obj;
                        if (a.length == 9) {
                            byte[] temperature = Arrays.copyOfRange(a, 0, 2);//[0,1]
                            byte[] signal = Arrays.copyOfRange(a, 2, 4);//[2,3]
                            byte[] super_signal = Arrays.copyOfRange(a, 4, 6);//[4,5]
                            //FF=[6,7],/r/n=[8,9]

                            //температура датчика
                            int temperature_result = FindTemperature(GetTemperature(temperature));
                            temperature_value.setText(String.valueOf(temperature_result) + "  " + "\u00b0" + "C");
                            get_temperature = temperature_result;

                            //значение сигнала
                            double signal_result = FindRudeSignal(Correction(GetSignal(signal), temperature_result), Z);
                            signal_value.setText(Double.toString(signal_result) + " " + "мг");
                            get_signal = signal_result;

                            //значение супер сигнала
                            double super_signal_result = FindSuperSignal(Correction(GetSuperSignal(super_signal), temperature_result), Z);
                            super_signal_value.setText(Double.toString(super_signal_result) + " " + "мг");
                            get_super_signal = super_signal_result;
                            break;

                        }
                }
            }
        }


        private void change_activity() {
            setContentView(R.layout.activity_work);
            sharedPreferences = getPreferences(PREFERENCE_MODE_PRIVATE);
            Z = sharedPreferences.getFloat("Z", (float) Z);





            temperature_value = (TextView) findViewById(R.id.temperature_value);
            signal_value = (TextView) findViewById(R.id.signal_value);
            editText = (EditText) findViewById(R.id.editText);
            Button abs_btn = (Button) findViewById(R.id.setabsolute_btn);

            super_signal_value = (TextView) findViewById(R.id.textView6);

            current_value = (TextView) findViewById(R.id.currentvalue_value);

            if (Z == 0.0) {
                Z=96.0;
                current_value.setText(Double.toString(Z));

            } else {
                current_value.setText(Double.toString(Z));
            }


            test_view = (TextView) findViewById(R.id.test_view);
            test_view.setMovementMethod(new ScrollingMovementMethod());
            Button test_btn = (Button) findViewById(R.id.test_btn);
            Button test_clear = (Button) findViewById(R.id.test_clear_btn);


            View.OnClickListener abs_click = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sharedPreferences = getPreferences(PREFERENCE_MODE_PRIVATE);
                    sharedPreferencesEditor = sharedPreferences.edit();
                    switch (v.getId()) {
                        case R.id.setabsolute_btn:
                            p0_value = editText.getText().toString();

                            if (p0_value.isEmpty()) {
                                Z = sharedPreferences.getFloat("Z", (float) Z);

                                current_value.setText(Double.toString(Z));
                            } else {
                                Z = Double.parseDouble(p0_value);
                                current_value.setText(Double.toString(Z));
                                sharedPreferencesEditor.putFloat("Z", (float) Z);
                            }
                            break;
                        case R.id.test_btn:
                            test1();
                            break;
                        case R.id.test_clear_btn:
                            test_view.setText("");
                            break;

                    }
                    sharedPreferencesEditor.apply();
                }
            };
            abs_btn.setOnClickListener(abs_click);
            test_btn.setOnClickListener(abs_click);
            test_clear.setOnClickListener(abs_click);

            TextView z_result = (TextView) findViewById(R.id.absolute_label);


            change_menu = true;
            invalidateOptionsMenu();
            supportInvalidateOptionsMenu();


        }

        private double GetSuperSignal(byte[] super_signal_array) {
            double super_signal;
            ByteBuffer bb = ByteBuffer.allocate(2);
            bb.order(ByteOrder.BIG_ENDIAN);
            bb.put(super_signal_array[0]);
            bb.put(super_signal_array[1]);
            super_signal = bb.getShort(0);
            return super_signal;
        }

        private double GetSignal(byte[] signal_array) {
            double signal;
            ByteBuffer bb = ByteBuffer.allocate(2);
            bb.order(ByteOrder.BIG_ENDIAN);
            bb.put(signal_array[0]);
            bb.put(signal_array[1]);
            signal = bb.getShort(0);
            return signal;
        }

        private short GetTemperature(byte[] temperature_array) {
            short temperature;
            ByteBuffer bb = ByteBuffer.allocate(2);
            bb.order(ByteOrder.BIG_ENDIAN);
            bb.put(temperature_array[0]);
            bb.put(temperature_array[1]);
            temperature = bb.getShort(0);
            return temperature;
        }
    }


    // ==========================================================================


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_search:
                if (super.isAdapterReady()) {
                    if (isConnected()) stopConnection();
                    else startDeviceListActivity();
                } else {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
                return true;
            case R.id.menu_graph:
                Intent openGraph = new Intent(getBaseContext(), Graph.class);
                startActivity(openGraph);


            default:
                return super.onOptionsItemSelected(item);
        }
    }
    // ============================================================================


    /**
     * ********************************************************
     */
    public int temperature_code[] =
            {
                    0x01F7, 0x01F6, 0x01F5, 0x01F4, 0x01F3, 0x01F2, 0x01F1, 0x01F0, 0x01EF, 0x01EE,
                    0x01ED, 0x01EC, 0x01EA, 0x01E9, 0x01E8, 0x01E6, 0x01E5, 0x01E3, 0x01E2, 0x01E0,
                    0x01DE, 0x01DC, 0x01DB, 0x01D9, 0x01D6, 0x01D4, 0x01D2, 0x01D0, 0x01CD, 0x01CA,
                    0x01C7, 0x01C5, 0x01C2, 0x01BF, 0x01BC, 0x00B9, 0x01B6, 0x01B3, 0x01AF, 0x01AB,
                    0x01A7, 0x01A4, 0x01A0, 0x019C, 0x0198, 0x0193, 0x0190, 0x018B, 0x0187, 0x0182,
                    0x017D, 0x0179, 0x0174, 0x0170, 0x016A, 0x0165, 0x0160, 0x015B, 0x0156, 0x0151,
                    0x014B, 0x0146, 0x0141, 0x013C, 0x0136, 0x0130, 0x012B, 0x0126, 0x0121, 0x011B,
                    0x0115, 0x0110, 0x010B, 0x0106, 0x0100, 0x00FA, 0x00F6, 0x00F1, 0x00EB, 0x00E6,
                    0x00E0, 0x00DC, 0x00D7, 0x00D2, 0x00CD, 0x00C8, 0x00C3, 0x00BF, 0x00BA, 0x00B6,
                    0x00B1, 0x00AD, 0x00A9, 0x00A4, 0x00A0, 0x009C, 0x0098, 0x0094, 0x0091, 0x008D,
                    0x0089, 0x0085, 0x0082, 0x007F, 0x007B, 0x0078, 0x0075, 0x0072, 0x006F, 0x006C,
                    0x0068, 0x0066, 0x0063, 0x0060, 0x005E, 0x005B, 0x0059, 0x0057, 0x0054, 0x0052,
                    0x004F, 0x004D, 0x004B, 0x0049, 0x0047, 0x0045, 0x0043, 0x0042, 0x0040, 0x003E,
                    0x003C, 0x003B, 0x0039, 0x0038, 0x0036, 0x0035, 0x0033, 0x0032, 0x0031, 0x002F,
                    0x002E
            };
    public int temperature;

    public int FindTemperature(int thermo) {
        int index;


        if (thermo >= 0x01F8) {
            return 127;
        } else if (thermo >= 0x01A8) {
            index = 40;
        } else if (thermo >= 0x0116) {
            index = 70;
        } else if (thermo >= 0x008A) {
            index = 100;
        } else if (thermo >= 0x002E) {
            index = 140;
        } else {
            return (thermo + 54);
        }
        while((temperature_code[index--]) < thermo);
        temperature = ((index) - 39);
        return temperature;
    }

    /**
     * ***************Signal*****************************************
     */


    double m;
    double dp;
    double result;

    public double FindRudeSignal(double P,double Z) {

        dp = P - Z;//104
        if (dp < 3) {
            m = 0;
        } else {
            if (dp < 9) {//8
                m = 0.075 * dp;

            } else {
                if (dp < 27) {
                    m = 0.0473 * dp + 0.2229;
                } else {
                    if (dp < 51) {
                        m = 0.10416 * dp - 1.3121;
                    } else {
                        m = 0.375 * dp - 15.125;
                    }
                }
            }
        }

        result = new BigDecimal(m).setScale(1, RoundingMode.UP).doubleValue();
        return (result);
    }

    /**
     * ********************************************************
     */
    /**
     * ***************SuperSignal*****************************************
     */

   // final int SP0 = 533;
    double super_result;

    public double FindSuperSignal(double P,double Z) {

        dp = P-Z;
        if (dp < 85) {
            m = 0;
        } else {
            if (dp < 270) {
                m = 0.00486 * dp + 0.187;
            } else {
                if (dp < 510) {
                    m = 0.010476 * dp - 1.3123;
                } else {
                    m = 0.0375 * dp - 15.125;
                }
            }
        }

        super_result = new BigDecimal(m).setScale(1, RoundingMode.UP).doubleValue();
        return (super_result);
    }

    /**
     * ********************************************************
     */

    double correct_signal;
    double test_var_corr;

    /**
     * ***************Correction*****************************************
     */
    public double Correction(double P, int temperature) {
        double correction;
        if (temperature < 76) {
            correction = 0.45 * temperature - 11.25;


        } else {
            correction = 0.29 * temperature + 0.96;

        }
        correct_signal = P - correction;
        return correct_signal;
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
        try {
            writeToSD();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public void test1() {
        int P = 0, T = 0;
        double test_var = 0.0;

        StringBuilder stringBuilder = new StringBuilder();
        int test_temperature[] = {
                100, 98, 89, 84, 76, 74, 71, 69, 68, 66, 64, 60, 58, 55, 52, 50,
                47, 44, 42, 40, 39, 37, 31, 27, 25, 23, 21, 19, 17, 15, 12, 10,
                8, 5, 3, 1, -2, -5, -7, -9, -11, -12, -15, -18, -20};
        int test_signal[] = {
                126, 125, 123, 120, 119, 118, 117, 116, 115, 114, 113, 112, 111,
                110, 108, 107, 106, 105, 104, 103, 102, 101, 99, 97, 96, 95, 94,
                93, 92, 91, 90, 89, 88, 87, 86, 85, 84, 83, 82, 81, 80, 79, 78, 77,
                76};
        for (int i = 0; i < 45; i++) {
            T = test_temperature[i];
            P = test_signal[i];
            test_var = FindRudeSignal(test_correction(P, T),Z);
            double test_var1 = test_var_corr;

            stringBuilder.append("T=").append(test_temperature[i]).append("; S=").append(test_signal[i]).append("; C=").append(test_var1).append("; SC=").append(test_var).append("\n");

        }
        test_view.setText(stringBuilder);


    }

    public double test_correction(double P, double T) {
        double correction;
        if (T < 76) {
            correction = 0.45 * T - 11.25;
            test_var_corr = correction;

        } else {
            correction = 0.29 * T + 0.96;
            test_var_corr = correction;

        }
        correct_signal = P - correction;
        test_var_corr = new BigDecimal(test_var_corr).setScale(2, RoundingMode.UP).doubleValue();


        return correct_signal;
    }

    public static class DBHelper extends SQLiteOpenHelper {


        private static final String LOG_TAG = "DB";

        public  DBHelper(Context context) {
            // конструктор суперкласса
            super(context, DB_NAME, null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.d(LOG_TAG, "--- onCreate database ---");
            // создаем таблицу с полями

            db.execSQL("create table my_table ("
                    + "id integer primary key autoincrement,"
                    + "date numeric,"
                    + "temperature real,"
                    + "signal real,"
                    + "super_signal real,"
                    + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }

    }

    public class MyTimerTask extends TimerTask {

        private static final String LOG_TAG = "TimerTask DB";


        @Override
        public void run() {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            Log.d(LOG_TAG, "--- Insert in my_table: ---");
            // подготовим данные для вставки в виде пар: наименование столбца - значение
            ContentValues cv = new ContentValues();

            cv.put("date", new SimpleDateFormat("dd:MM:yyyy HH:mm").format(Calendar.getInstance().getTime()));
            cv.put("temperature", DeviceControlActivity.get_temperature);
            cv.put("signal", DeviceControlActivity.get_signal);
            cv.put("super_signal", DeviceControlActivity.get_super_signal);
            // вставляем запись и получаем ее ID
            long rowID = db.insert("my_table", null, cv);
            Log.d(LOG_TAG, "row inserted, ID = " + rowID + " " + cv);

        }

    }
}





