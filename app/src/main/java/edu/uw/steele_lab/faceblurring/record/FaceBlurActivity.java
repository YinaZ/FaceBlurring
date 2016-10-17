package edu.uw.steele_lab.faceblurring.record;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.*;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_objdetect;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.w3c.dom.Text;

import edu.uw.steele_lab.faceblurring.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import static org.bytedeco.javacpp.opencv_imgproc.COLOR_BGRA2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.ellipse;
import static org.bytedeco.javacpp.opencv_imgproc.equalizeHist;
import static org.bytedeco.javacpp.opencv_objdetect.CV_HAAR_SCALE_IMAGE;


/**
 * Created by zhuyina on 10/4/16.
 */
public class FaceBlurActivity extends Activity {
    private final static String CLASS_LABEL = "FaceBlurActivity";
    private final static String LOG_TAG = CLASS_LABEL;

    private OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
    private int counter = 0;
    private opencv_objdetect.CascadeClassifier faceDetector;
    private opencv_core.RectVector past_faces;
    private File ffmpeg_link = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "stream.mp4");
    private File return_link = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "result.mp4");
    private FFmpegFrameRecorder recorder;
    private int sampleAudioRateInHz = 44100;
    private int imageWidth = 660;
    private int imageHeight = 480;
    private int frameRate = 30;
    private ProgressBar mProgress;
    private Button mButton;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_faceblur);
//        mProgress = (ProgressBar) findViewById(R.id.progressBar);
        textView = (TextView) findViewById(R.id.textView);
        mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textView.setText("Please Wait...");
                try {
                    Init_Classifier();
                } catch (java.lang.Exception e){
                    Log.i(LOG_TAG, "classifer initialize failure");
                }
                readAndBlur();
                textView.setText("Done!");
            }
        });

//        mProgress.setProgress(100);

    }

    public void readAndBlur() {

        // Load the video to videoGrabber
        FrameGrabber videoGrabber = new FFmpegFrameGrabber(ffmpeg_link);

        //recorder = new FFmpegFrameRecorder(getFilePath(), channel);
//        recorder = new FFmpegFrameRecorder(return_link, imageHeight, imageWidth, videoGrabber.getAudioChannels());
//        recorder.setVideoCodec(videoGrabber.getVideoCodec());
//        recorder.setFormat("mp4");
//        recorder.setFrameRate(videoGrabber.getFrameRate());
//        recorder.setSampleFormat(videoGrabber.getSampleFormat());
//        recorder.setSampleRate(videoGrabber.getSampleRate());
        recorder = new FFmpegFrameRecorder(return_link, imageHeight, imageWidth, 1);

        recorder.setFormat("mp4");
        recorder.setSampleRate(sampleAudioRateInHz);
        // Set in the surface changed method
        recorder.setFrameRate(frameRate);

        Log.i(LOG_TAG, "recorder initialize success");

        Log.d("bharat", " Audio channels = " + videoGrabber.getAudioChannels());

        try
        {
            videoGrabber.setFormat("mp4");
            videoGrabber.start();
        }catch (org.bytedeco.javacv.FrameGrabber.Exception e){
            Log.e("javacv", "Failed to start grabber" + e);
        }

        try
        {
            recorder.start();
        }catch (org.bytedeco.javacv.FFmpegFrameRecorder.Exception e){
            Log.e("javacv", "Failed to start recorder" + e);
        }


        Frame vFrame = null;

        do{
            try{
                // Grab an image Frame from the video file
                vFrame = videoGrabber.grabFrame();
                if(vFrame != null){
                    try{
//                        Bitmap input = converterToBitmap.convert(vFrame);
//                        // Convert our bitmap to a Mat so the detector can use it
//                        Mat inputMat = new Mat(input.getWidth(), input.getHeight(), CV_8UC1);
//                        bitmapToMat(input, inputMat);
                        Frame newFrame = null;
                        if (vFrame.image != null) {
//                            newFrame = ProcessVideo(convertToBGR(vFrame));
                            newFrame = ProcessVideo(vFrame);
                        }
                        recorder.setTimestamp(videoGrabber.getTimestamp());
                        recorder.record(newFrame);
                    } catch (Exception e){
                        Log.e("javacv", "video record frame failed: "+ e);
                    }


                }
            } catch (org.bytedeco.javacv.FrameGrabber.Exception e){
                Log.e("javacv", "video grabFrame failed: "+ e);
            }

        } while(vFrame != null);

        try
        {
            videoGrabber.stop();
        }catch (org.bytedeco.javacv.FrameGrabber.Exception e)
        {
            Log.e("javacv", "failed to stop video grabber", e);
        }

        try{
            recorder.stop();
            recorder.release();
        } catch (org.bytedeco.javacv.FrameRecorder.Exception e){

            Log.e("javacv", "failed to stop video recorder", e);
        }
    }

    public Frame ProcessVideo (Frame frame) throws Exception {
        // Convert frame to Mat
        // Do the detection
        opencv_core.Mat processedMat = FaceDetector(converterToMat.convert(frame));
//        Mat processedMat = converterToMat.convert(frame);
        // Convert processedMat back to a Frame
        frame = converterToMat.convert(processedMat);
        return frame;
    }

    public void Init_Classifier () throws Exception {

        InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_default);
        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
        File mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
        FileOutputStream os = new FileOutputStream(mCascadeFile);

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        is.close();
        os.close();

        faceDetector = new opencv_objdetect.CascadeClassifier(mCascadeFile.getAbsolutePath());
        assert ( !faceDetector.empty() );
    }

    public opencv_core.Mat FaceDetector (opencv_core.Mat image) throws Exception {
        opencv_core.Mat videoMatGray = new opencv_core.Mat();
        cvtColor(image, videoMatGray, COLOR_BGRA2GRAY);

//        cvtColor(image, image, COLOR_YUV2BGR_NV21);
        equalizeHist(videoMatGray, videoMatGray);
        int j = videoMatGray.channels();

        opencv_core.RectVector faces = new opencv_core.RectVector();
//        faceDetector.detectMultiScale(videoMatGray, faces);

        faceDetector.detectMultiScale( videoMatGray, faces, 1.1, 3, 0|CV_HAAR_SCALE_IMAGE, new opencv_core.Size(30, 30), new opencv_core.Size(150, 150));

        if (faces.size() == 0 && counter < 3) {
            faces = past_faces;
            counter++;
        } else {
            past_faces = faces;
            counter = 0;
        }
        for (int i = 0; i < faces.size() && i < 1; i++) {
            opencv_core.Rect face_i = faces.get(i);
            // And finally write all we've found out to the original image!
            // First of all draw a green rectangle around the detected face:
            // rectangle(image, face_i, new Scalar(0, 255, 0, 255));
            opencv_core.Size size = new opencv_core.Size((int)(face_i.width() *0.5), (int)(face_i.height() *0.5));

            opencv_core.Point center = new opencv_core.Point((int)Math.ceil(face_i.x() + face_i.width()*0.5), (int)Math.ceil(face_i.y() + face_i.height()*0.5));
            ellipse( image, center, size, 0, 0, 360, new opencv_core.Scalar(0, 255, 0, 255), -1, 8, 0 );
        }


        return image;
    }
}
