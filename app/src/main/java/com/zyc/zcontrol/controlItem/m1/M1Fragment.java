package com.zyc.zcontrol.controlItem.m1;


import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.zyc.zcontrol.ConnectService;
import com.zyc.zcontrol.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.content.Context.BIND_AUTO_CREATE;

/**
 * A simple {@link Fragment} subclass.
 */
public class M1Fragment extends Fragment {
    public final static String Tag = "M1Fragment";

    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;

    //region 使用本地广播与service通信
    LocalBroadcastManager localBroadcastManager;
    private MsgReceiver msgReceiver;
    ConnectService mConnectService;
    //endregion

    //region 控件
    private SwipeRefreshLayout mSwipeLayout;
    TextView tvPm25;
    TextView tvFormaldehyde;
    TextView tvTemperature;
    TextView tvHumidity;
    SeekBar seekBar;
    TextView tvBrightness;

    //endregion
    TextView log;


    String device_mac = null;
    String device_name = null;

    public M1Fragment() {

        // Required empty public constructor
    }

    @SuppressLint("ValidFragment")
    public M1Fragment(String name, String mac) {
        this.device_mac = mac;
        this.device_name = name;
        // Required empty public constructor
    }

    //region Handler
    @SuppressLint("HandlerLeak")
    Handler  handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {// handler接收到消息后就会执行此方法
            switch (msg.what) {
                case 1:
                    Send("{\"mac\":\"" + device_mac + "\",\"brightness\":null}");
                    break;
                case 2:
                    Log.d(Tag, "send seekbar:" + msg.arg1);

                    Send("{\"mac\":\"" + device_mac + "\",\"brightness\":" + msg.arg1 + "}");
                    break;
            }
        }
    };

    //endregion
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        final View view = inflater.inflate(R.layout.fragment_m1, container, false);

        //region MQTT服务有关
        //region 动态注册接收mqtt服务的广播接收器,
        localBroadcastManager = LocalBroadcastManager.getInstance(getContext());
        msgReceiver = new MsgReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectService.ACTION_UDP_DATA_AVAILABLE);
        intentFilter.addAction(ConnectService.ACTION_MQTT_CONNECTED);
        intentFilter.addAction(ConnectService.ACTION_MQTT_DISCONNECTED);
        intentFilter.addAction(ConnectService.ACTION_DATA_AVAILABLE);
        localBroadcastManager.registerReceiver(msgReceiver, intentFilter);
        //endregion

        //region 启动MQTT 服务以及启动,无需再启动
        Intent intent = new Intent(getContext(), ConnectService.class);
        getActivity().bindService(intent, mMQTTServiceConnection, BIND_AUTO_CREATE);
        //endregion
        //endregion

        //region 控件初始化

        tvPm25 = view.findViewById(R.id.tv_pm25_value);
        tvFormaldehyde = view.findViewById(R.id.tv_formaldehyde_value);
        tvTemperature = view.findViewById(R.id.tv_temperature_value);
        tvHumidity = view.findViewById(R.id.tv_humidity_value);

        tvPm25.setText("---");
        tvFormaldehyde.setText("-.--");

        String exchange = getActivity().getResources().getString(R.string.m1_num_string, "--", "-");
        tvTemperature.setText(Html.fromHtml(exchange));
        exchange = getActivity().getResources().getString(R.string.m1_num_string, "--", "-").replace("℃", "%");
        tvHumidity.setText(Html.fromHtml(exchange));

        //region 拖动条 处理viewpage/SwipeRefreshLayout滑动冲突事件
        seekBar = view.findViewById(R.id.seekBarR);
        //region 处理viewpage/SwipeRefreshLayout滑动冲突事件
        seekBar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                view.getParent().requestDisallowInterceptTouchEvent(true);
                if (event.getAction() == MotionEvent.ACTION_UP) mSwipeLayout.setEnabled(true);
                else mSwipeLayout.setEnabled(false);
                return false;
            }
        });
        //endregion
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Message msg=new Message();
                msg.arg1=seekBar.getProgress();
                msg.what=2;
                handler.sendMessageDelayed(msg,1);
            }
        });

        //endregion

        tvBrightness=view.findViewById(R.id.textViewR);
        tvBrightness.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), M1PlugActivity.class);
                intent.putExtra("name", device_name);
                intent.putExtra("mac", device_mac);
                startActivity(intent);
            }
        });

        //region 更新当前状态
        mSwipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipeRefreshLayout);
        mSwipeLayout.setColorSchemeResources(R.color.colorAccent, R.color.colorPrimaryDark, R.color.colorAccent, R.color.colorPrimary);
        mSwipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                handler.sendEmptyMessageDelayed(1, 0);
                mSwipeLayout.setRefreshing(false);
            }
        });
        //endregion

        //region log 相关
        final ScrollView scrollView = (ScrollView) view.findViewById(R.id.scrollView);
        log = (TextView) view.findViewById(R.id.tv_log);
        log.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                scrollView.post(new Runnable() {
                    public void run() {
                        scrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
        DateFormat df = new SimpleDateFormat("---- yyyy/MM/dd HH:mm:ss ----");
        log.setText(df.format(new Date()));
        //endregion

        //endregion

        return view;
    }

    @Override
    public void onDestroy() {
        //注销广播
        localBroadcastManager.unregisterReceiver(msgReceiver);
        //停止服务
        getActivity().unbindService(mMQTTServiceConnection);
        super.onDestroy();
    }

    void Send(String message) {
        if (mConnectService == null) return;
        if(getActivity()==null) Log.e(Tag,"getActivity");
        boolean b = getActivity().getSharedPreferences("Setting_" + device_mac, 0).getBoolean("always_UDP", false);
        mConnectService.Send(b ? null : "device/zm1/" + device_mac + "/set", message);
    }

    //数据接收处理函数
    void Receive(String ip, int port, String message) {
        //TODO 数据接收处理
        Receive(null, message);
    }

    void Receive(String topic, String message) {
        //TODO 数据接收处理
        Log.d(Tag, "RECV DATA,topic:" + topic + ",content:" + message);

        try {
            JSONObject jsonObject = new JSONObject(message);
            String name = null;
            String mac = null;
            JSONObject jsonSetting = null;
            if (jsonObject.has("name")) name = jsonObject.getString("name");
            if (jsonObject.has("mac")) mac = jsonObject.getString("mac");
            if (jsonObject.has("setting")) jsonSetting = jsonObject.getJSONObject("setting");
            if (mac == null || !mac.equals(device_mac)) return;
            //region 解析传感器参数

            if (jsonObject.has("PM25")) {
                int PM25 = jsonObject.getInt("PM25");
                tvPm25.setText(String.valueOf(PM25));
            }

            if (jsonObject.has("formaldehyde")) {
                double formaldehyde = jsonObject.getDouble("formaldehyde");
                tvFormaldehyde.setText(String.valueOf(formaldehyde));
            }
            String exchange;
            if (jsonObject.has("temperature")) {
                double temperature = jsonObject.getDouble("temperature");
                int t = (int) (temperature * 10);
                exchange = getActivity().getResources().getString(R.string.m1_num_string, String.valueOf(t / 10), String.valueOf(t % 10));
                tvTemperature.setText(Html.fromHtml(exchange));
            }
            if (jsonObject.has("humidity")) {
                double humidity = jsonObject.getDouble("humidity");
                int h = (int) (humidity * 10);
                exchange = getActivity().getResources().getString(R.string.m1_num_string, String.valueOf(h / 10), String.valueOf(h % 10)).replace("℃","%");
                tvHumidity.setText(Html.fromHtml(exchange));
            }
            //endregion
            //region 解析on
            if (jsonObject.has("brightness")) {
                int brightness = jsonObject.getInt("brightness");
                seekBar.setProgress(brightness);
            }
            //endregion

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //region MQTT服务有关

    private final ServiceConnection mMQTTServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mConnectService = ((ConnectService.LocalBinder) service).getService();
            handler.sendEmptyMessageDelayed(1, 300);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mConnectService = null;
        }
    };

    //广播接收,用于处理接收到的数据
    public class MsgReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ConnectService.ACTION_UDP_DATA_AVAILABLE.equals(action)) {
                String ip = intent.getStringExtra(ConnectService.EXTRA_UDP_DATA_IP);
                String message = intent.getStringExtra(ConnectService.EXTRA_UDP_DATA_MESSAGE);
                int port = intent.getIntExtra(ConnectService.EXTRA_UDP_DATA_PORT, -1);
                Receive(ip, port, message);
            } else if (ConnectService.ACTION_MQTT_CONNECTED.equals(action)) {  //连接成功
                Log.d(Tag, "ACTION_MQTT_CONNECTED");
                Log("app已连接mqtt服务器");
                handler.sendEmptyMessageDelayed(1, 300);
            } else if (ConnectService.ACTION_MQTT_DISCONNECTED.equals(action)) {  //连接失败/断开
                Log.w(Tag, "ACTION_MQTT_DISCONNECTED");
                Log("已与mqtt服务器已断开");
            } else if (ConnectService.ACTION_DATA_AVAILABLE.equals(action)) {  //接收到数据
                String topic = intent.getStringExtra(ConnectService.EXTRA_DATA_TOPIC);
                String message = intent.getStringExtra(ConnectService.EXTRA_DATA_MESSAGE);
                Receive(topic, message);
            }
        }
    }
    //endregion

    void Log(String str) {
        log.setText(log.getText() + "\n" + str);
    }
}
