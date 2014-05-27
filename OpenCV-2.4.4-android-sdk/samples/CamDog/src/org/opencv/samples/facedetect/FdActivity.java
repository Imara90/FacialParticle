package org.opencv.samples.facedetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.objdetect.CascadeClassifier;
//import org.opencv.samples.fd.CamShifting;



import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

@SuppressWarnings("unused")
public class FdActivity extends Activity implements CvCameraViewListener2 {

    private static final String    TAG                 = "OCVSample::Activity";
    private static final Scalar    FACE_RECT_COLOR     = new Scalar(0, 255, 0, 255);
    private static final Scalar    HUE_RECT_COLOR      = new Scalar(0, 100, 0, 255);
    private static final Scalar    EYES_RECT_COLOR     = new Scalar(255, 0, 255, 0);
    public static final int        JAVA_DETECTOR       = 0;
    public static final int        NATIVE_DETECTOR     = 1;

    private MenuItem               mItemFace50;
    private MenuItem               mItemFace40;
    private MenuItem               mItemFace30;
    private MenuItem               mItemFace20;

    private Mat                    mRgba;
    private Mat                    mGray;
    private File                   mCascadeFile;
    
    private File				   mCascadeFileeye;
    
    private DetectionBasedTracker  mNativeDetector;
    private DetectionBasedTracker  mNativeDetectoreye;

    private int                    mDetectorType       = NATIVE_DETECTOR;
    private String[]               mDetectorName;

    private float                  mRelativeFaceSize   = 0.2f;
    private int                    mAbsoluteFaceSize   = 0;

    private CameraBridgeViewBase   mOpenCvCameraView;
    
    // for tracking the face
    CamShifting cs;
    CamShifting cseyes;
    
    private boolean				   facedetected = false;
    private boolean				   facelost = false;
    private boolean				   eyesdetected = false;
    private boolean				   eyeslost = false;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("detection_based_tracker");

                    try {
                    	// initialize new camshift
                    	cs = new CamShifting();                    	
                    	
                        // load cascade file from application resources - lpbcascade is faster than haarcascade but 
                    	// not as robust
                        //InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_default);
                    	InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        //mCascadeFile = new File(cascadeDir, "haarcascade_frontalface.xml");
                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);

                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }
                    
                    try {
                        // load cascade file from application resources
                        InputStream iseye = getResources().openRawResource(R.raw.haarcascade_eye);
                        File cascadeDireye = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFileeye = new File(cascadeDireye, "haarcascade_eye.xml");
                        FileOutputStream oseye = new FileOutputStream(mCascadeFileeye);

                        byte[] buffereye = new byte[4096];
                        int bytesReadeye;
                        while ((bytesReadeye = iseye.read(buffereye)) != -1) {
                            oseye.write(buffereye, 0, bytesReadeye);
                        }
                        iseye.close();
                        oseye.close();

                        mNativeDetectoreye = new DetectionBasedTracker(mCascadeFileeye.getAbsolutePath(), 0);

                        cascadeDireye.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
    
    
    public FdActivity() {
        mDetectorName = new String[2];
        mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";

        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.face_detect_surface_view);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
            mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
        }

        MatOfRect faces = new MatOfRect();
        MatOfRect eyes = new MatOfRect();
        Rect[] facesArray = null;
        Rect[] eyesArray = null;
        RotatedRect trackeyes = null;
        RotatedRect trackface = null;

        // If no face has been detected yet, detect the face
        // TODO add a way to falsify more than 1 face
        if (mNativeDetector != null && !facedetected){
        		if (facelost)
        		{
        			// TODO check the previous region, track
        			mNativeDetector.detect(mGray, faces);
        		}
        		else
        		{
        			mNativeDetector.detect(mGray, faces);
        		}
                // check if there is a face detected and assign them to the array
                if (!faces.empty())
                {
                	facesArray = faces.toArray();  
                	facedetected = true;
                	facelost = false;
                	Core.rectangle(mRgba, facesArray[0].tl(), facesArray[0].br(), FACE_RECT_COLOR, 3);
                    // When face is detected, start tracking it using camshifting
                    cs.create_tracked_object(mRgba,facesArray,cs);
                }
        }
 
        // TODO check if we need to only capture a single face
        
        // if a face has already been detected, we should track that face until it is lost
        if (facedetected)
        {
            //track the face in the new frame
            trackface = cs.camshift_track_face(mRgba, facesArray, cs);
            
            // check whether the face is still a valid detection, else check again
            if (trackface.size.area() < 100 || trackface.size.width > 350 || trackface.size.height > 800)
            {
            	facedetected = false;
            	facelost = true;
            }
            else
            {	            
	            //outline face with rectangle
	            Core.rectangle(mRgba, trackface.boundingRect().tl(), trackface.boundingRect().br(), HUE_RECT_COLOR, 3);
	             
	            //System.out.println(trackface.size.area());
	            //System.out.println(trackface.size.width);
	            if (mNativeDetectoreye != null && !eyesdetected)
	            {
	            	if (eyeslost)
	            	{
	            		// TODO check the previous region, track
	            		mNativeDetectoreye.detect(mGray, eyes);
	            	}
	            	else
	            	{
	            		mNativeDetectoreye.detect(mGray, eyes);
	            	}
	                   // check if there is a face detected and assign them to the array
	                if (!eyes.empty())
	                {
	                   	eyesArray = eyes.toArray();  
	                   	eyesdetected = true;
	                   	eyeslost = false;
	                   	Core.rectangle(mRgba, eyesArray[0].tl(), eyesArray[0].br(), EYES_RECT_COLOR, 3);
	                    // When face is detected, start tracking it using camshifting
	                    //cseyes.create_tracked_object(mRgba,eyesArray,cseyes);
	                }
	            }
	                
		        if (eyesdetected)
		        {
		        	mNativeDetectoreye.detect(mGray, eyes);
		        	eyesArray = eyes.toArray();
		            for (int j = 0; j < eyesArray.length; j++)
		            {
		            	Core.rectangle(mRgba, eyesArray[j].tl(), eyesArray[j].br(), EYES_RECT_COLOR, 3);            	
		            }
		        	/*
		            //track the face in the new frame
		            trackeyes = cseyes.camshift_track_face(mRgba, eyesArray, cseyes);
		            
		            // check whether the face is still a valid detection, else check again
		            if (trackeyes.size.area() < 100)
		            {
		            	eyesdetected = false;
		            	eyeslost = true;
		            }
		            */
		        }
            }
        }
        return mRgba;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemFace50 = menu.add("Face size 50%");
        mItemFace40 = menu.add("Face size 40%");
        mItemFace30 = menu.add("Face size 30%");
        mItemFace20 = menu.add("Face size 20%");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item == mItemFace50)
            setMinFaceSize(0.5f);
        else if (item == mItemFace40)
            setMinFaceSize(0.4f);
        else if (item == mItemFace30)
            setMinFaceSize(0.3f);
        else if (item == mItemFace20)
            setMinFaceSize(0.2f);
        return true;
    }

    private void setMinFaceSize(float faceSize) {
        mRelativeFaceSize = faceSize;
        mAbsoluteFaceSize = 0;
    }

}
