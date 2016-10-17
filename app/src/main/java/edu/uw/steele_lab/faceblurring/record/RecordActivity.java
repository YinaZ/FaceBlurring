package edu.uw.steele_lab.faceblurring.record;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.bytedeco.javacpp.opencv_objdetect;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameConverter;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import edu.uw.steele_lab.faceblurring.R;

import java.io.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_face.*;
import static org.bytedeco.javacpp.opencv_highgui.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.*;
import static org.bytedeco.javacpp.opencv_objdetect.*;


import java.net.URL;
import org.bytedeco.javacpp.*;


public class RecordActivity extends Activity implements OnClickListener {

    private final static String CLASS_LABEL = "RecordActivity";
    private final static String LOG_TAG = CLASS_LABEL;
    /* The number of seconds in the continuous record loop (or 0 to disable loop). */
    final int RECORD_LENGTH = 0;
    /* layout setting */
    private final int bg_screen_bx = 232;
    private final int bg_screen_by = 128;
    private final int bg_screen_width = 700;
    private final int bg_screen_height = 500;
    private final int bg_width = 1123;
    private final int bg_height = 715;
//    private final int live_width = 640;
//    private final int live_height = 480;
    private final int live_width = 480;
    private final int live_height = 640;


    long startTime = 0;
    boolean recording = false;
    volatile boolean runAudioThread = true;
    Frame[] images;
    long[] timestamps;
    ShortBuffer[] samples;
    int imagesIndex, samplesIndex;
    private PowerManager.WakeLock mWakeLock;
    private File ffmpeg_link = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "stream.mp4");
    private File return_link = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "result.mp4");
    private FFmpegFrameRecorder recorder;
    private boolean isPreviewOn = false;
    private int sampleAudioRateInHz = 44100;
    private int imageWidth = 660;
    private int imageHeight = 480;

    private int frameRate = 30;
    /* audio data getting thread */
    private AudioRecord audioRecord;
    private AudioRecordRunnable audioRecordRunnable;
    private Thread audioThread;
    /* video data getting thread */
    private Camera cameraDevice;
    private LayoutInflater controlInflater;
    private CameraView cameraView;
    private Frame yuvImage = null;
    private int screenWidth, screenHeight;
    private Button btnRecorderControl;
    private OpenCVFrameConverter.ToIplImage convertToIplImage = new OpenCVFrameConverter.ToIplImage();
    private OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
    private AndroidFrameConverter afc = new AndroidFrameConverter();
    private TextView message;
//     private FFmpegFrameFilter filter = new FFmpegFrameFilter("transpose=cclock_flip", imageWidth, imageHeight);


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // set to portrait
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_record);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
        mWakeLock.acquire();

        initLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        recording = false;

        if(cameraView != null) {
            cameraView.stopPreview();
        }

        if(cameraDevice != null) {
            cameraDevice.stopPreview();
            cameraDevice.release();
            cameraDevice = null;
        }

        if(mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if(keyCode == KeyEvent.KEYCODE_BACK) {
            if(recording) {
                stopRecording();
            }

            finish();

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void initLayout() {

        /* get size of screen */
        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        screenWidth = display.getWidth();
        screenHeight = display.getHeight();
        RelativeLayout.LayoutParams layoutParam = null;
        LayoutInflater myInflate = null;
        myInflate = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        RelativeLayout topLayout = new RelativeLayout(this);
        setContentView(topLayout);
        GridLayout preViewLayout = (GridLayout) myInflate.inflate(R.layout.activity_record, null);
        layoutParam = new RelativeLayout.LayoutParams(screenWidth, screenHeight);
        topLayout.addView(preViewLayout, layoutParam);

        /* add control button: start and stop */
        btnRecorderControl = (Button) findViewById(R.id.recorder_control);
        btnRecorderControl.setText("Start");
        btnRecorderControl.setOnClickListener(this);


        // add message
        message = (TextView) findViewById(R.id.textView1);
        message.setText("Hello!!");

        /* add camera view */
        int display_width_d = (int) (1.0 * bg_screen_width * screenWidth / bg_width);
        int display_height_d = (int) (1.0 * bg_screen_height * screenHeight / bg_height);
        int prev_rw, prev_rh;
        if(1.0 * display_width_d / display_height_d > 1.0 * live_width / live_height) {
            prev_rh = display_height_d;
            prev_rw = (int) (1.0 * display_height_d * live_width / live_height);
        } else {
            prev_rw = display_width_d;
            prev_rh = (int) (1.0 * display_width_d * live_height / live_width);
        }
        layoutParam = new RelativeLayout.LayoutParams(prev_rw, prev_rh);
        layoutParam.topMargin = (int) (1.0 * bg_screen_by * screenHeight / bg_height);
        layoutParam.leftMargin = (int) (1.0 * bg_screen_bx * screenWidth / bg_width);

        controlInflater = LayoutInflater.from(getBaseContext());
        View viewControl = controlInflater.inflate(R.layout.overlay, null);
        this.addContentView(viewControl, layoutParam);

        //test
        //initRecorder();

        cameraDevice = Camera.open();
        Log.i(LOG_TAG, "cameara open");
        cameraView = new CameraView(this, cameraDevice);
        topLayout.addView(cameraView, layoutParam);
        Log.i(LOG_TAG, "cameara preview start: OK");
    }

    //---------------------------------------
    // initialize ffmpeg_recorder
    //---------------------------------------
    private void initRecorder() {

        Log.w(LOG_TAG, "init recorder");

        if(RECORD_LENGTH > 0) {
            imagesIndex = 0;
            images = new Frame[RECORD_LENGTH * frameRate];
            timestamps = new long[images.length];
            for(int i = 0; i < images.length; i++) {
                images[i] = new Frame(imageWidth, imageHeight * 3/2, Frame.DEPTH_UBYTE, 1);
                timestamps[i] = -1;
            }
        } else if(yuvImage == null) {
            yuvImage = new Frame(imageWidth, imageHeight * 3/2, Frame.DEPTH_UBYTE, 1);
            Log.i(LOG_TAG, "create yuvImage");
        }

        Log.i(LOG_TAG, "ffmpeg_url: " + ffmpeg_link.getAbsolutePath());

//        recorder = new FFmpegFrameRecorder(ffmpeg_link, imageWidth, imageHeight, 1);

        recorder = new FFmpegFrameRecorder(ffmpeg_link, imageHeight, imageWidth, 1);

        recorder.setFormat("mp4");
        recorder.setSampleRate(sampleAudioRateInHz);
        // Set in the surface changed method
        recorder.setFrameRate(frameRate);

        Log.i(LOG_TAG, "recorder initialize success");

        audioRecordRunnable = new AudioRecordRunnable();
        audioThread = new Thread(audioRecordRunnable);
        runAudioThread = true;

        //TryProcessVideo(getFilePath());
//        filter.setPixelFormat(avutil.AV_PIX_FMT_NV21); // default camera format on Android
//        try{
//            filter.start();
//        } catch (FFmpegFrameFilter.Exception e) {
//            e.printStackTrace();
//        }

    }



    public void startRecording() {
        initRecorder();

        try {
            recorder.start();
            startTime = System.currentTimeMillis();
            recording = true;
            audioThread.start();
        } catch(FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRecording() {
        runAudioThread = false;
        try {
            audioThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        audioRecordRunnable = null;
        audioThread = null;

        if (recorder != null && recording) {
            if (RECORD_LENGTH > 0) {
                Log.v(LOG_TAG, "Writing frames");
                try {
                    int firstIndex = imagesIndex % samples.length;
                    int lastIndex = (imagesIndex - 1) % images.length;
                    if (imagesIndex <= images.length) {
                        firstIndex = 0;
                        lastIndex = imagesIndex - 1;
                    }
                    if ((startTime = timestamps[lastIndex] - RECORD_LENGTH * 1000000L) < 0) {
                        startTime = 0;
                    }
                    if (lastIndex < firstIndex) {
                        lastIndex += images.length;
                    }
                    for (int i = firstIndex; i <= lastIndex; i++) {
                        long t = timestamps[i % timestamps.length] - startTime;
                        if (t >= 0) {
                            if (t > recorder.getTimestamp()) {
                                recorder.setTimestamp(t);
                            }
                            Frame vframe = images[i % images.length];
                            try {
                                recorder.record(convertToBGR(vframe));
                            } catch (Exception e) {

                            }
                        }
                    }

                    firstIndex = samplesIndex % samples.length;
                    lastIndex = (samplesIndex - 1) % samples.length;
                    if (samplesIndex <= samples.length) {
                        firstIndex = 0;
                        lastIndex = samplesIndex - 1;
                    }
                    if (lastIndex < firstIndex) {
                        lastIndex += samples.length;
                    }
                    for (int i = firstIndex; i <= lastIndex; i++) {
                        recorder.recordSamples(samples[i % samples.length]);
                    }
                } catch (FFmpegFrameRecorder.Exception e) {
                    Log.v(LOG_TAG, e.getMessage());
                    e.printStackTrace();
                }
            }

            recording = false;
            Log.v(LOG_TAG, "Finishing recording, calling stop and release on recorder");
            try {
                recorder.stop();
                recorder.release();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            recorder = null;
        }
    }

    public static IplImage rotate(IplImage src, int angle) {
        IplImage img = IplImage.create(src.height(), src.width(), src.depth(), src.nChannels());
        opencv_core.cvTranspose(src, img);
        opencv_core.cvFlip(img, img, angle);
        return img;
    }

    public Frame convertToBGR (Frame frame) {
        // to bgr
        Mat mat = converterToMat.convert(frame);
        cvtColor(mat, mat, COLOR_YUV2BGR_NV21);
        // rotate
        frame = converterToMat.convert(mat);
        IplImage ipl = convertToIplImage.convert(frame);
        ipl = rotate(ipl, 90);

        return convertToIplImage.convert(ipl);
    }



    @Override
    public void onClick(View v) {
        if (!recording) {
            startRecording();
            Log.w(LOG_TAG, "Start Button Pushed");
            btnRecorderControl.setText("Stop");
        } else {
            // This will trigger the audio recording loop to stop and then set isRecorderStart = false;
            message.setText("Wait...");
            stopRecording();
            Log.w(LOG_TAG, "Stop Button Pushed");
            btnRecorderControl.setText("Start");
            message.setText("Done!");
        }
    }

    //---------------------------------------------
    // audio thread, gets and encodes audio data
    //---------------------------------------------
    class AudioRecordRunnable implements Runnable {

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            // Audio
            int bufferSize;
            ShortBuffer audioData;
            int bufferReadResult;

            bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz,
                                                      AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz,
                                          AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            if(RECORD_LENGTH > 0) {
                samplesIndex = 0;
                samples = new ShortBuffer[RECORD_LENGTH * sampleAudioRateInHz * 2 / bufferSize + 1];
                for(int i = 0; i < samples.length; i++) {
                    samples[i] = ShortBuffer.allocate(bufferSize);
                }
            } else {
                audioData = ShortBuffer.allocate(bufferSize);
            }

            Log.d(LOG_TAG, "audioRecord.startRecording()");
            audioRecord.startRecording();

            /* ffmpeg_audio encoding loop */
            while(runAudioThread) {
                if(RECORD_LENGTH > 0) {
                    audioData = samples[samplesIndex++ % samples.length];
                    audioData.position(0).limit(0);
                }
                //Log.v(LOG_TAG,"recording? " + recording);
                bufferReadResult = audioRecord.read(audioData.array(), 0, audioData.capacity());
                audioData.limit(bufferReadResult);
                if(bufferReadResult > 0) {
                    Log.v(LOG_TAG, "bufferReadResult: " + bufferReadResult);
                    // If "recording" isn't true when start this thread, it never get's set according to this if statement...!!!
                    // Why?  Good question...
                    if(recording) {
                        if(RECORD_LENGTH <= 0) {
                            try {
                                recorder.recordSamples(audioData);
                                //Log.v(LOG_TAG,"recording " + 1024*i + " to " + 1024*i+1024);
                            } catch(FFmpegFrameRecorder.Exception e) {
                                Log.v(LOG_TAG, e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
            Log.v(LOG_TAG, "AudioThread Finished, release audioRecord");

            /* encoding finish, release recorder */
            if(audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                Log.v(LOG_TAG, "audioRecord released");
            }
        }
    }

    //---------------------------------------------
    // camera thread, gets and encodes video data
    //---------------------------------------------
    class CameraView extends SurfaceView implements SurfaceHolder.Callback, PreviewCallback {

        private SurfaceHolder mHolder;
        private Camera mCamera;

        public CameraView(Context context, Camera camera) {
            super(context);
            Log.w("camera", "camera view");
            mCamera = camera;
            mHolder = getHolder();
            mHolder.addCallback(CameraView.this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            mCamera.setPreviewCallback(CameraView.this);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                stopPreview();
                mCamera.setPreviewDisplay(holder);
                mCamera.setDisplayOrientation(90);
            } catch(IOException exception) {
                mCamera.release();
                mCamera = null;
            }
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Camera.Parameters camParams = mCamera.getParameters();
            List<Camera.Size> sizes = camParams.getSupportedPreviewSizes();
            // Sort the list in ascending order
            Collections.sort(sizes, new Comparator<Camera.Size>() {

                public int compare(final Camera.Size a, final Camera.Size b) {
                    return a.width * a.height - b.width * b.height;
                }
            });

            // Pick the first preview size that is equal or bigger, or pick the last (biggest) option if we cannot
            // reach the initial settings of imageWidth/imageHeight.
            for(int i = 0; i < sizes.size(); i++) {
                if((sizes.get(i).width >= imageWidth && sizes.get(i).height >= imageHeight) || i == sizes.size() - 1) {
                    imageWidth = sizes.get(i).width;
                    imageHeight = sizes.get(i).height;
                    Log.v(LOG_TAG, "Changed to supported resolution: " + imageWidth + "x" + imageHeight);
                    break;
                }
            }
            camParams.setPreviewSize(imageWidth, imageHeight);

            Log.v(LOG_TAG, "Setting imageWidth: " + imageWidth + " imageHeight: " + imageHeight + " frameRate: " + frameRate);

            camParams.setPreviewFrameRate(frameRate);
            Log.v(LOG_TAG, "Preview Framerate: " + camParams.getPreviewFrameRate());

            //set NV21
            camParams.setPreviewFormat(ImageFormat.NV21);
            mCamera.setParameters(camParams);
            startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                mHolder.addCallback(null);
                mCamera.setPreviewCallback(null);
            } catch(RuntimeException e) {
                // The camera has probably just been released, ignore.
            }
        }

        public void startPreview() {
            if(!isPreviewOn && mCamera != null) {
                isPreviewOn = true;
                mCamera.startPreview();
            }
        }

        public void stopPreview() {
            if(isPreviewOn && mCamera != null) {
                isPreviewOn = false;
                mCamera.stopPreview();
            }
        }



        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if(audioRecord == null || audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                startTime = System.currentTimeMillis();
                return;
            }
            if(RECORD_LENGTH > 0) {
                int i = imagesIndex++ % images.length;
                yuvImage = images[i];
                timestamps[i] = 1000 * (System.currentTimeMillis() - startTime);
            }
            /* get video data */
            if(yuvImage != null && recording) {
                ((ByteBuffer) yuvImage.image[0].position(0)).put(data);
                //yuvImage = afc.convert(data, imageWidth, imageHeight);


//                if( mYuv != null ) mYuv.release();
//                if( mRgba != null) mRgba.release();
//                mYuv = new Mat( imageHeight + imageHeight/2, imageWidth, CV_8UC2);
//                mYuv.data().put(data);
//
//                mRgba = new Mat();
//                cvtColor( mYuv, mRgba, COLOR_YUV2BGR_NV21);
//                yuvImage = converterToMat.convert(mRgba);
//
//                int a = mRgba.channels();
//                int b = mYuv.channels();

                if(RECORD_LENGTH <= 0) {
                    try {
                        Log.v(LOG_TAG, "Writing Frame");
                        long t = 1000 * (System.currentTimeMillis() - startTime);
                        if(t > recorder.getTimestamp()) {
                            recorder.setTimestamp(t);
                        }
                        recorder.record(convertToBGR(yuvImage));
                    } catch(FFmpegFrameRecorder.Exception e) {
                        Log.v(LOG_TAG, e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}