package com.example.omair.migrateto;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassResult;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifiedImage;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifiedImages;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifierResult;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.DetectedFaces;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.Face;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.FaceAge;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.FaceGender;

import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ImageWithFaces;


import java.util.List;
import java.util.Locale;

/**
 * Class used to construct a UI to deliver information received from Visual Recognition to the user.
 */
class RecognitionResultBuilder {

    private final Cam context;

    RecognitionResultBuilder(Cam context) {
        this.context = context;
    }

    /**
     * Dynamically constructs a LinearLayout with information from Visual Recognition.
     * @return A LinearLayout with a dynamic number image_tag.xml
     */
    LinearLayout buildRecognitionResultView(ClassifiedImages visualClassification, DetectedFaces detectedFaces) {
        LinearLayout recognitionLayout = new LinearLayout(context);
        recognitionLayout.setOrientation(LinearLayout.VERTICAL);

        FlexboxLayout imageTagContainer = (FlexboxLayout)context.getLayoutInflater().inflate(R.layout.tag_box, null);

        // First process facial data from Visual Recognition. For each feature create an image tag with a name and score.
        List<ImageWithFaces> potentialFaces = detectedFaces.getImages();
        for (int i = 0; i < potentialFaces.size(); i++) {
            List<Face> allFaces = potentialFaces.get(i).getFaces();
            if (allFaces == null) {break;}
            for (Face face : allFaces) {
                if (face.getGender() != null) {
                    String formattedScore = String.format(Locale.US, "%.0f", face.getGender().getScore() * 100) + "%";
                    imageTagContainer.addView(constructImageTag(context.getLayoutInflater(),
                            face.getGender().getGender(), formattedScore));
                }

                String faceResult = "";
                String faceScore = "";

                FaceGender gender = face.getGender();
                if (gender.getGender() != null) {
                    faceResult += gender.getGender();
                    faceScore += String.format(Locale.US, "%.0f", gender.getScore() * 100) + "%";
                } else {
                    faceResult += "Unknown Gender";
                    faceScore += "N/A";
                }

                FaceAge age = face.getAge();
                if (age != null) {
                    //   if (age.getMin() == null) {age.setMin(0);}
                    //  if (age.getMax() == null) {age.setMax(age.getMin()+15);}
                    faceResult += " (" + age.getMin() + " - " + age.getMax() + ")";
                    faceScore += " (" + String.format(Locale.US, "%.0f", age.getScore() * 100) + "%)";
                }
                imageTagContainer.addView(constructImageTag(context.getLayoutInflater(), faceResult, faceScore));
            }
        }

        // Next process general classification data from Visual Recognition and create image tags for each visual class.
        List<ClassifiedImage> classifications = visualClassification.getImages();

        for (int i = 0; i < classifications.size(); i++) {
            List<ClassifierResult> classifiers = classifications.get(i).getClassifiers();
            if (classifiers == null) break;
            for (int j = 0; j < classifiers.size(); j++) {
                List<ClassResult> visualClasses = classifiers.get(j).getClasses();
                if (visualClasses == null) break;
                for (ClassResult visualClass : visualClasses) {
                    String formattedScore = String.format(Locale.US, "%.0f", visualClass.getScore() * 100) + "%";
                    imageTagContainer.addView(constructImageTag(context.getLayoutInflater(), visualClass.getClassName(), formattedScore));
                }
            }
        }

        // If parsing through Visual Recognition's return has resulted in no image tags, create an "Unknown" tag.
        if (imageTagContainer.getChildCount() <= 0) {
            imageTagContainer.addView(constructImageTag(context.getLayoutInflater(), "Unknown", "N/A"));
        }

        recognitionLayout.addView(imageTagContainer);

        return recognitionLayout;
    }

    /**
     * Creates a TextView image tag with a name and score to be displayed to the user.
     * @param inflater Layout inflater to access R.layout.image_tag.
     * @param tagName Name of the tag to be displayed.
     * @param tagScore Certainty score of the tag, to be displayed when the user clicks the tag.
     * @return A TextView representation of the image tag.
     */
    static TextView constructImageTag(LayoutInflater inflater, final String tagName, final String tagScore) {
        TextView imageTagView = (TextView)inflater.inflate(R.layout.image_tag, null);
        imageTagView.setText(tagName);

        // Set an onclick listener that gives each image tag a toggle between its name and its score.
        imageTagView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView label = (TextView)v;
                String currentText = label.getText().toString();

                if (currentText.equals(tagName)) {
                    label.setMinWidth(label.getWidth());
                    label.setText(tagScore);
                } else {
                    label.setText(tagName);
                }
            }
        });

        return imageTagView;
    }
}

