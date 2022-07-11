package com.example.vrsystemclient;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends Activity {
    public static TextureView mTextureView;
    public static TextView connText;
    private View mControlsView;
    private EditText mEditText;
    private Button buttonTeacher;
    private Button buttonStudent;
    private Button buttonPlay;
    private Button buttonPause;
    private Button buttonTearDown;


    public RTSPNetwork network;
    public static FunctionThread funcNet;
    public static MovementThread moveThread;
    public static InputStream traceInput;

    // OpenGL variables
    public static File rootDir;
    public static OpenGLThread mOpenGLThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        //set full screen
        mControlsView=getWindow().getDecorView();

        connText = (TextView) findViewById(R.id.conn);
        mEditText = (EditText) findViewById(R.id.ipAddr);
        mTextureView = (TextureView) findViewById(R.id.view);
        buttonTeacher = (Button) findViewById(R.id.buttonTeacher);
        buttonStudent = (Button) findViewById(R.id.buttonStudent);
        buttonPlay = (Button) findViewById(R.id.buttonPlay);
        buttonPause = (Button) findViewById(R.id.buttonPause);
        buttonTearDown = (Button) findViewById(R.id.buttonTeardown);

        buttonPlay.setVisibility(View.GONE);
        buttonPause.setVisibility(View.GONE);
        buttonTearDown.setVisibility(View.GONE);

        connText.bringToFront();

        buttonTeacher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Utils.teacherFlag = true;
                startPlayback();
            }
        });

        buttonStudent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Utils.teacherFlag = false;
                startPlayback();
            }
        });

        buttonPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                network.handler.sendEmptyMessage(Config.RTSP_PLAY);
            }
        });

        buttonPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                network.handler.sendEmptyMessage(Config.RTSP_PAUSE);
            }
        });

        buttonTearDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                network.handler.sendEmptyMessage(Config.RTSP_TEARDOWN);
            }
        });
    }

    public void startPlayback(){
        // hide the navigation bar
        String inputAddr = mEditText.getText().toString();
        if(!inputAddr.isEmpty())
            Config.SERVER_IP = mEditText.getText().toString();
        buttonTeacher.setVisibility(View.GONE);
        buttonStudent.setVisibility(View.GONE);
        mEditText.setVisibility(View.GONE);
        //buttonPlay.setVisibility(View.VISIBLE);
        //buttonPause.setVisibility(View.VISIBLE);
        //buttonTearDown.setVisibility(View.VISIBLE);
        int uiOptions = View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        mControlsView.setSystemUiVisibility(uiOptions);

        Config.screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        Config.screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
        System.out.println("width: "+Config.screenWidth+" height: "+Config.screenHeight);

        rootDir = getExternalFilesDir(null);

        readTileTable();
        readIDTable();

        traceInput = getResources().openRawResource(R.raw.officetrace);

        // start opengl thread
        mOpenGLThread =new OpenGLThread("opengl thread",Config.nDecoders, mTextureView.getSurfaceTexture(), getApplicationContext());
        mOpenGLThread.start(); // mOpenGLThread are ready  receive msg after this point
        mOpenGLThread.handler.sendEmptyMessage(Config.MSG_SETUP);

        network = new RTSPNetwork("RTSP handler");
        network.start();
        network.handler.sendEmptyMessage(Config.RTSP_SETUP);

        funcNet = new FunctionThread("Functional thread");
        funcNet.start();
        funcNet.handler.sendEmptyMessage(Config.CONNECT);

        try {
            Thread.sleep(Config.SETUP_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if(Utils.teacherFlag) {
            moveThread = new MovementThread("Movement thread");
            moveThread.start();
        }
    }

    private void readIDTable() {
        try{
            InputStream inputStream = getResources().openRawResource(R.raw.id2pose);
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));

            String st;
            int cnt = 0;
            while ((st = br.readLine()) != null) {
                String[] strs = st.split(" ");
                int videoID = Integer.parseInt(strs[0]);
                String pose = strs[1];
                int tileID = Integer.parseInt(pose.split(",")[2]);
                //Utils.videoID2tileID.put(videoID, tileID);
                Utils.pose2VideoID.put(pose, videoID);
                Utils.videoID2pose.put(videoID,pose);
                cnt++;
            }
            br.close();
            // print the hashmap
            //System.out.println(table);
            System.out.println("Tile ID Table read done, total id count:" + cnt);
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }

    public void readTileTable(){
        //File file = new File(rootDir+"/tile_table_1row_4col.txt");
        try{
            InputStream inputStream = getResources().openRawResource(R.raw.tile_table_1row_4col);
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            //BufferedReader br = new BufferedReader(new FileReader(file));

            String st;
            int cnt = 0;
            String coor = null;
            HashMap<String, ArrayList<Integer>> table = new HashMap<>();
            while ((st = br.readLine()) != null) {
                if(cnt%2 == 0){
                    coor = st;
                }
                else{
                    ArrayList<Integer> tiles = new ArrayList<>();
                    String[] strTiles = st.split(",");
                    for(int i=0;i<strTiles.length;i++){
                        tiles.add((int)Double.parseDouble(strTiles[i]));
                    }
                    table.put(coor,tiles);
                }
                cnt++;
            }

            // print the hashmap
            //System.out.println(table);
            Utils.tileTable = table;
            System.out.println("Tile Table read done.");
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }
}