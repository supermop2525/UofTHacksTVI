package com.example.omair.migrateto;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;

import com.ibm.watson.developer_cloud.service.exception.ForbiddenException;
import com.ibm.watson.developer_cloud.service.security.IamOptions;
import com.ibm.watson.developer_cloud.visual_recognition.v3.VisualRecognition;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifiedImages;

import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifyOptions;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.DetectFacesOptions;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.DetectedFaces;

import com.ibm.mobilefirstplatform.clientsdk.android.core.api.BMSClient;

public class Cam extends AppCompatActivity {


    private static final String STATE_IMAGE = "image";
    private static final int REQUEST_CAMERA = 1;
    private static final int REQUEST_GALLERY = 2;

    // Visual Recognition Service has a maximum file size limit that we control by limiting the size of the image.
    private static final float MAX_IMAGE_DIMENSION = 1200;

    private VisualRecognition visualService;
    private RecognitionResultFragment resultFragment;

    private String mSelectedImageUri = null;
    private File output = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cam);

        if (savedInstanceState == null) {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DCIM);
            dir.mkdirs();
            output = new File(dir, "mCameraContent.jpeg");
        } else {
            output = (File)savedInstanceState.getSerializable("com.ibm.visual_recognition.EXTRA_FILENAME");
        }
        resultFragment = (RecognitionResultFragment)getSupportFragmentManager().findFragmentByTag("result");
        if (resultFragment == null) {
            resultFragment = new RecognitionResultFragment();
            resultFragment.setRetainInstance(true);
            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, resultFragment, "result").commit();
        }
        if (savedInstanceState != null) {
            mSelectedImageUri = savedInstanceState.getString(STATE_IMAGE);
        }
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if(output == null){
            output=(File)savedInstanceState.getSerializable("com.ibm.visual_recognition.EXTRA_FILENAME");
        }
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(output));
        startActivityForResult(cameraIntent, REQUEST_CAMERA);

        BMSClient.getInstance().initialize(getApplicationContext(), BMSClient.REGION_US_SOUTH);

        IamOptions.Builder iamOptionsBuilder =new IamOptions.Builder();
        iamOptionsBuilder.apiKey("V7PWI5s8QCV3-YfDJEiZx-7WPLbWGqx7SPNfc-zOHvI7");

        visualService = new VisualRecognition("2018-03-19",
                iamOptionsBuilder.build());

        // Immediately on start attempt to validate the user's credentials from credentials.xml.
        ValidateCredentialsTask vct = new ValidateCredentialsTask();
        vct.execute();
    }
    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString(STATE_IMAGE, mSelectedImageUri);
        super.onSaveInstanceState(savedInstanceState);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_GALLERY || requestCode == REQUEST_CAMERA) {
                Uri uri = null;
                if (uri == null) {
                    uri = Uri.fromFile(output);
                }
                mSelectedImageUri = uri.toString();
                Bitmap selectedImage = fetchBitmapFromUri(uri);
                // Resize the Bitmap to constrain within Watson Image Recognition's Size Limit.
                selectedImage = resizeBitmapForWatson(selectedImage, MAX_IMAGE_DIMENSION);
                ClassifyTask ct = new ClassifyTask();
                ct.execute(selectedImage);
            }
        }
    }

    /**
     * Displays an AlertDialogFragment with the given parameters.
     * @param errorTitle Error Title from values/strings.xml.
     * @param errorMessage Error Message either from values/strings.xml or response from server.
     * @param canContinue Whether the application can continue without needing to be rebuilt.
     */
    private void showDialog(int errorTitle, String errorMessage, boolean canContinue) {
        DialogFragment newFragment = AlertDialogFragment.newInstance(errorTitle, errorMessage, canContinue);
        newFragment.show(getFragmentManager(), "dialog");
    }

    private class ValidateCredentialsTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            // Check to see if the user's credentials are valid or not along with other errors.
            try {
                visualService.listClassifiers().execute();
            } catch (Exception ex) {
                if (ex.getClass().equals(ForbiddenException.class) ||
                        ex.getClass().equals(IllegalArgumentException.class)) {
                    showDialog(R.string.error_title_invalid_credentials,
                            getString(R.string.error_message_invalid_credentials), false);
                }
                else if (ex.getCause() != null && ex.getCause().getClass().equals(UnknownHostException.class)) {
                    showDialog(R.string.error_title_bluemix_connection,
                            getString(R.string.error_message_bluemix_connection), true);
                }
                else {
                    showDialog(R.string.error_title_default, ex.getMessage(), true);
                    ex.printStackTrace();
                }
            }
            return null;
        }
    }
    private class ClassifyTask extends AsyncTask<Bitmap, Void, ClassifyTaskResult> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected ClassifyTaskResult doInBackground(Bitmap... params) {
            Bitmap createdPhoto = params[0];

            // Reformat Bitmap into a .jpg and save as file to input to Watson.
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            createdPhoto.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

            try {
                File tempPhoto = File.createTempFile("photo", ".jpg", getCacheDir());
                FileOutputStream out = new FileOutputStream(tempPhoto);
                out.write(bytes.toByteArray());
                out.close();

                // Two different service calls for objects and for faces.
                ClassifyOptions classifyImagesOptions = new ClassifyOptions.Builder().imagesFile(tempPhoto).build();
                DetectFacesOptions detectFacesOptions = new DetectFacesOptions.Builder().imagesFile(tempPhoto).build();

                ClassifiedImages classification = visualService.classify(classifyImagesOptions).execute();
                DetectedFaces faces = visualService.detectFaces(detectFacesOptions).execute();

                ClassifyTaskResult result = new ClassifyTaskResult(classification, faces);

                tempPhoto.delete();

                return result;
            } catch (Exception ex) {
                if (ex.getCause() != null && ex.getCause().getClass().equals(UnknownHostException.class)) {
                    showDialog(R.string.error_title_bluemix_connection,
                            getString(R.string.error_message_bluemix_connection), true);
                } else {
                    showDialog(R.string.error_title_default, ex.getMessage(), true);
                    ex.printStackTrace();
                }
                return null;
            }
        }

        @Override
        protected void onPostExecute(ClassifyTaskResult result) {
            ProgressBar progressSpinner = (ProgressBar)findViewById(R.id.loadingSpinner);
            progressSpinner.setVisibility(View.GONE);
            if (result != null) {
                // If not null send the full result from ToneAnalyzer to our UI Builder class.
                RecognitionResultBuilder resultBuilder = new RecognitionResultBuilder(Cam.this);
                LinearLayout resultLayout = (LinearLayout) findViewById(R.id.recognitionResultLayout);
                if (resultLayout==null) Log.d("post","here1");

                if(resultLayout != null){
                    resultLayout.removeAllViews();
                }
                LinearLayout recognitionView = resultBuilder.buildRecognitionResultView(result.getVisualClassification(), result.getDetectedFaces());
                if (recognitionView==null) Log.d("post","here1");
                resultLayout.addView(recognitionView);
                Log.d("post","here");
            }
        }
    }
    private class ClassifyTaskResult {
        private final ClassifiedImages visualClassification;
        private final DetectedFaces detectedFaces;

        ClassifyTaskResult (ClassifiedImages vcIn, DetectedFaces dfIn) {
            visualClassification = vcIn;
            detectedFaces = dfIn;
        }

        ClassifiedImages getVisualClassification() { return visualClassification;}
        DetectedFaces getDetectedFaces() {return detectedFaces;}
    }
    private Bitmap fetchBitmapFromUri(Uri imageUri) {
        try {
            // Fetch the Bitmap from the Uri.
            Bitmap selectedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

            // Fetch the orientation of the Bitmap in storage.
            String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
            Cursor cursor = getContentResolver().query(imageUri, orientationColumn, null, null, null);
            int orientation = 0;
            if (cursor != null && cursor.moveToFirst()) {
                orientation = cursor.getInt(cursor.getColumnIndex(orientationColumn[0]));
            }
            if(cursor != null) {
                cursor.close();
            }
            // Rotate the bitmap with the found orientation.
            Matrix matrix = new Matrix();
            matrix.setRotate(orientation);
            selectedImage = Bitmap.createBitmap(selectedImage, 0, 0, selectedImage.getWidth(), selectedImage.getHeight(), matrix, true);

            return selectedImage;

        } catch (IOException e) {
            return null;
        }
    }
    private Bitmap resizeBitmapForWatson(Bitmap originalImage, float maxSize) {

        int originalHeight = originalImage.getHeight();
        int originalWidth = originalImage.getWidth();

        int boundingDimension = (originalHeight > originalWidth) ? originalHeight : originalWidth;

        float scale = maxSize / boundingDimension;

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        originalImage = Bitmap.createBitmap(originalImage, 0, 0, originalWidth, originalHeight, matrix, true);

        return originalImage;
    }
}
