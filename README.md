# FaceBlurring

An Android application that records video and blurs face with javaCV (OpenCV, FFmpeg libraries)

### Installation

1) Install Android Studio [here](https://developer.android.com/studio/index.html)

2) While the Android Studio download completes, verify which version of the JDK you have: open a command line and type java -version. If the JDK is not available or the version is lower than 1.8, download the Java SE Development Kit 8 with your computer version [here](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) 

3) To install additional tools: 
   Open Android Studio, and go to Configure > SDK Manager > Launch Standalone SDK Manager

### Import project
Check out [this video](https://www.youtube.com/watch?v=E0MjorpDWxc)

### Expected behavior of this app

1) Enter the app: three buttons are displayed: "Record in app", "FaceBlur" and "Record with camera app"

2) Click on "Record in app": a video preview (with a circle displaying where to place the head) and button "start" are displayed. Clicking on the button will start recording video and clicking on it again will stop recording video. After done recording video, the video will be saved to DCIM/stream.mp4

3) Click on "FaceBlur", a button and a text are displayed, clicking on the button will start reading DCIM/sample_video.mp4, blur the face and save the new video to DCIM/result.mp4

4) Click on "Record with camera app", a button and a video view are displayed. Clicking on the video will open the Camera APP. After taking a video with Camera APP, the video will be saved to DCIM/sample_video.mp4. And the video will be displayed in the app as well. Also if there is already a file called sample_video.mp4 in DCIM folder, the app might alert you that it failed in capturing a video, because an alert is thrown whenever there is a real error or when there is a file in existing path. It will still does the job for you but you need to bare with the error message.

### Directory structure
~~~
app/ - app directory
  build.gradle - specifies Java, JavaCV and SDK versions
  src/main/ - Main application directory
    AndroidManifest.xml - describes the fundamental characteristics 
                          of the app and each of its components
    java/edu/uw/steele_lab/faceblurring/ - java code folder
      MainActivity.java - "home page" of the app, for choosing
                          1 of 3 specific activities
      record/ - folder contains code for specific activities
        CameraActivity.java - record video with default camera app, 
                              and save to DCIM/sample_video.mp4
        FaceBlurActivity.java - read video DCIM/sample_video.mp4, 
                                blur the face and save to result.mp4
        RecordActivity.java - record video in FaceBlurring app, 
                              and save to DCIM/stream.mp4
    res - resource folder
      drawable/ - drawable folder
        circle.xml - a circle drawable used in RecordActivity.java
                     to display where to place head
      layout/ - layout folder
        activity_camera.xml - layout xml for CameraActivity.java
        activity_faceblur.xml - layout xml for FaceBlurActivity.java
        activity_main.xml - layout xml for MainActivity.java
        activity_record.xml - layout xml for RecordActivity.java
        overlay.xml - layout xml used to overlay a circle 
                      in activity_record.xml
      mipmap*/ - folders contain icons in various resolutions
      raw/ - folder contains xml files for multiple trained CascadeClassifiers
             (in use: haarcascade_frontalface_default.xml)
      values/ - folder contains
        strings.xml - contains declaration for global string variables
        dimens.xml - defines margins
        styles.xml - defines app themes, etc
      
~~~

### Reference
RecordActivity.java: [code used](https://github.com/bytedeco/javacv/blob/master/samples/RecordActivity.java)

### Issues needs to be solved
1) RecordActivity.java currently can only record videos with low resolution and frame rates

2) FaceBlurActivity.java reads a video and writes to a new one, but apparently the resolution of the new saved video is lower than the original one
