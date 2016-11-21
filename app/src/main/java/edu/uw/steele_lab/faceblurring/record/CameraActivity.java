package edu.uw.steele_lab.faceblurring.record;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;
import edu.uw.steele_lab.faceblurring.R;
import java.io.File;



public class CameraActivity extends Activity {

    private final int VIDEO_REQUEST_CODE = 100;
    private File ffmpeg_link = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "sample_video.mp4");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
    }

    public void captureVideo(View view){
        // In this project we use intent to capture the videos
        Intent camera_intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        // Get the path, and store into video_file
        File video_file = ffmpeg_link;
        // Convert it to uri address
        Uri video_uri = Uri.fromFile(video_file);
        // Add the address into the Intent object
        camera_intent.putExtra(MediaStore.EXTRA_OUTPUT,video_uri);
        // Specify the quality of the video (1 is the maximum)
        camera_intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY,1);
        // Start the activity, and specify the intent
        startActivityForResult(camera_intent,VIDEO_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check if the video is successfully captured
        if(requestCode == VIDEO_REQUEST_CODE){
            if(resultCode == RESULT_OK){
                Toast.makeText(getApplicationContext(),"Video Sucessfully Recorded!",Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(getApplicationContext(),"Video Recording Failed or Original file overwritten!",Toast.LENGTH_LONG).show();
            }
        }

        // Dispaly videos into the VideoView View_Of_Video
        VideoView View_Of_Video = (VideoView) findViewById(R.id.View_Of_Video);
        Uri video_uri = Uri.fromFile(ffmpeg_link);
        View_Of_Video.setVideoURI(video_uri);

        // Player controls(play, pause, stop, etc....)
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(View_Of_Video);
        View_Of_Video.setMediaController(mediaController);

        View_Of_Video.start();
    }
}
