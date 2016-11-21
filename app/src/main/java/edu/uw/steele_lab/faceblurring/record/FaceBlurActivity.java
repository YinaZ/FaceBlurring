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
import org.bytedeco.javacv.OpenCVFrameConverter;

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
    private File ffmpeg_link = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "sample_video.mp4");
    private File return_link = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "result.mp4");
    private FFmpegFrameRecorder recorder;
    private int sampleAudioRateInHz = 44100;
    // image width and height should depend on the width and height of input video
    // but now they are hard coded because we need width and height information to define FFmpegFrameRecorder
    // TO DO:
    // initialize FFmpegFrameRecorder after FFmpegFrameGrabber grabs the first frame of the video
    // so that imageWidth and imageHeight can be assigned according to input video
    private int imageWidth = 1920;
    private int imageHeight = 1080;
    private int frameRate = 30;
    private Button mButton;
    private TextView textView;

    // set max and min face sizes to 0.25 of imageHeight and 0.75 of imageHeight
    // this is just a temp setting please change them when needed
    private int minLength = (int)(0.25 * (double)imageHeight);
    private int maxLength = (int)(0.75 * (double)imageHeight);
    private opencv_core.Size minFace = new opencv_core.Size(minLength, minLength);
    private opencv_core.Size maxFace = new opencv_core.Size(maxLength, maxLength);

    private OpenCVFrameConverter.ToIplImage convertToIplImage = new OpenCVFrameConverter.ToIplImage();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_faceblur);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        textView = (TextView) findViewById(R.id.textView);
        mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Init_Classifier();
                } catch (java.lang.Exception e){
                    Log.i(LOG_TAG, "classifer initialize failure");
                }
                readAndBlur();
                textView.setText("Done!");
            }
        });
    }

    public void readAndBlur() {

        // Load the video to grabber
        FrameGrabber grabber = new FFmpegFrameGrabber(ffmpeg_link);

        // declare and initialize recorder here
        recorder = new FFmpegFrameRecorder(return_link, imageHeight, imageWidth, 1);
        recorder.setFormat("mp4");
        recorder.setSampleRate(sampleAudioRateInHz);

        // Set in the surface changed method
        recorder.setFrameRate(frameRate);

        Log.i(LOG_TAG, "recorder initialize success");

        // start graber and recorder here
        try
        {
            grabber.setFormat("mp4");
            grabber.start();
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
                vFrame = grabber.grabFrame();
                if(vFrame != null){
                    try{
                        Frame newFrame = null;
                        if (vFrame.image != null) {
                            // ProcessVideo do the face blurring
                            newFrame = ProcessVideo(vFrame);
                        }
                        recorder.setTimestamp(grabber.getTimestamp());
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
            grabber.stop();
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

    public static opencv_core.IplImage rotate(opencv_core.IplImage src, int angle) {
        opencv_core.IplImage img = opencv_core.IplImage.create(src.height(), src.width(), src.depth(), src.nChannels());
        opencv_core.cvTranspose(src, img);
        opencv_core.cvFlip(img, img, angle);
        return img;
    }

    public Frame rotateWithFrame (Frame frame) {
        opencv_core.IplImage ipl = convertToIplImage.convert(frame);
        ipl = rotate(ipl, 90);
        return convertToIplImage.convert(ipl);
    }

    public Frame ProcessVideo (Frame frame) throws Exception {
        // Convert frame to Mat
        // Do the detection
        opencv_core.Mat processedMat = FaceDetector(converterToMat.convert(rotateWithFrame(frame)));
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

        faceDetector.detectMultiScale( videoMatGray, faces, 1.1, 3, 0|CV_HAAR_SCALE_IMAGE, minFace, maxFace);
        
        // TODO: replace this part of code
        // if face is not detected in the frame, use last detected face for 3 frames
        // not good practice, should be replaced!
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
