package com.example.emoplayer.Music;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.emoplayer.Adapter.AdapterEmotionSong;
import com.example.emoplayer.Adapter.AdapterRecommendedSong;
import com.example.emoplayer.Model.Model_Songs;
import com.example.emoplayer.Model.Model_Users;
import com.example.emoplayer.R;
import com.example.emoplayer.Utils.AppPermission;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.custom.FirebaseCustomLocalModel;
import com.google.firebase.ml.custom.FirebaseModelDataType;
import com.google.firebase.ml.custom.FirebaseModelInputOutputOptions;
import com.google.firebase.ml.custom.FirebaseModelInputs;
import com.google.firebase.ml.custom.FirebaseModelInterpreter;
import com.google.firebase.ml.custom.FirebaseModelInterpreterOptions;
import com.google.firebase.ml.custom.FirebaseModelOutputs;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static android.app.Activity.RESULT_OK;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final String MODEL_PATH = "converted_model.tflite";
    private static final int IMAGE_PICK_CAMERA_CODE = 100;

    public HomeFragment() {
    }

    FirebaseCustomLocalModel localModel;
    FirebaseModelInterpreter firebaseInterpreter;
    FirebaseModelInputOutputOptions inputOutputOptions;

    public String emotion;
    private Rect bounds;
    public static List<String> label = Arrays.asList("angry", "disgust", "scared", "happy", "sad", "surprised", "neutral");

    private TextView emotionTV;
    private TextView displayNameTV;
    private ImageButton cameraButton;
    private RecyclerView recommend_recyclerView;
    private RecyclerView emotion_recyclerView;
    //private ImageView imageView;

    private FirebaseUser user;
    private FirebaseFirestore databaseUser;
    private FirebaseFirestore databaseEmotionSong;
    private FirebaseFirestore databaseRecommendedSong;

    private AdapterEmotionSong adapterEmotionSong;
    private ArrayList<Model_Songs> songListEmotion = new ArrayList<>();
    private AdapterRecommendedSong adapterRecommendedSong;
    private ArrayList<Model_Songs> songListRecommended = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_home, container, false);

        initViews(view);

        user = FirebaseAuth.getInstance().getCurrentUser();
        databaseUser = FirebaseFirestore.getInstance();
        databaseEmotionSong = FirebaseFirestore.getInstance();
        databaseRecommendedSong = FirebaseFirestore.getInstance();

        getUserDetail();
        getRecommendedSong();

        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getPicFromCamera();
            }
        });

        return view;
    }

    private void initViews(View view) {

        emotionTV = view.findViewById(R.id.home_emotionTv);
        displayNameTV = view.findViewById(R.id.home_displayName);
        cameraButton = view.findViewById(R.id.home_cameraButton);
        recommend_recyclerView = view.findViewById(R.id.home_recyclerView_recommendation);
        emotion_recyclerView = view.findViewById(R.id.home_recyclerView_emotion);
        //imageView = view.findViewById(R.id.home_imageView);

        emotion_recyclerView.setHasFixedSize(true);
        emotion_recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        recommend_recyclerView.setHasFixedSize(true);
        recommend_recyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false));

    }

    private void getUserDetail() {

        String uid = user.getUid();

        databaseUser.collection("Users").document(uid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {

                        Log.d(TAG, "getUserDetail: Successful");

                        Model_Users model_users = documentSnapshot.toObject(Model_Users.class);
                        assert model_users != null;
                        Log.d(TAG, "getUserDetail: user = " + model_users.getUserName());

                        setDisplayName(model_users);

                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "getUserDetail: failed " + e.getMessage());
            }
        });

    }

    private void setDisplayName(Model_Users model_users) {

        char c;
        String display;

        if (model_users.getUserName().equals("")) {
            c = model_users.getEmail().charAt(0);
        } else {
            c = model_users.getUserName().charAt(0);
        }
        display = Character.toString(c).toUpperCase();

        displayNameTV.setText(display);

    }

    private void getPicFromCamera() {

        AppPermission appPermission = new AppPermission(getActivity());
        if (!appPermission.checkCameraPermission()) {
            appPermission.requestCameraPermission();
        }

        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra("android.intent.extras.CAMERA_FACING", 1);
            intent.putExtra("android.intent.extra.quickCapture", true);
            startActivityForResult(intent, IMAGE_PICK_CAMERA_CODE);

        } catch (Exception e) {
            Log.d(TAG, "getPicFromCamera: " + e.getMessage());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == IMAGE_PICK_CAMERA_CODE) {

                Bitmap bitmap = (Bitmap) data.getExtras().get("data");

                Log.d(TAG, "onActivityResult: bitmap -> " + bitmap);

                runInterpreter(bitmap);
            }
        }
    }

    private void runInterpreter(Bitmap bitmap) {
        localModel = new FirebaseCustomLocalModel.Builder().setAssetFilePath(MODEL_PATH).build();
        try {
            firebaseInterpreter = createInterpreter();
        } catch (FirebaseMLException e) {
            Log.d(TAG, "FaceAndEmotion1: Interpreter exception: " + e.getMessage());
        }
        try {
            inputOutputOptions = createInputOutputOptions();
        } catch (FirebaseMLException e) {
            Log.d(TAG, "FaceAndEmotion1: Input output exception: " + e.getMessage());
        }
        runModel(bitmap);
    }

    private FirebaseModelInterpreter createInterpreter() throws FirebaseMLException {

        FirebaseModelInterpreterOptions options = new FirebaseModelInterpreterOptions.Builder(localModel).build();
        FirebaseModelInterpreter interpreter = FirebaseModelInterpreter.getInstance(options);
        return interpreter;
    }

    private FirebaseModelInputOutputOptions createInputOutputOptions() throws FirebaseMLException {

        FirebaseModelInputOutputOptions inputOutputOptions = new FirebaseModelInputOutputOptions.Builder()
                .setInputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 64, 64, 1})
                .setOutputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 7})
                .build();
        return inputOutputOptions;
    }

    private void runModel(final Bitmap bitmap) {

        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);

        FirebaseVisionFaceDetectorOptions options = new FirebaseVisionFaceDetectorOptions.Builder()
                .setClassificationMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build();

        FirebaseVisionFaceDetector detector = FirebaseVision.getInstance().getVisionFaceDetector(options);

        detector.detectInImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
            @Override
            public void onSuccess(List<FirebaseVisionFace> faces) {
                getFaceResults(faces, bitmap);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "runModel: detector: failed: " + e.getMessage());
            }
        });

    }

    private void getFaceResults(List<FirebaseVisionFace> faces, Bitmap bitmap) {

        /*for (FirebaseVisionFace face : faces) {
            bounds = face.getBoundingBox();
        }*/
        //Bitmap croppedBmp = Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height());

        if (faces.size() > 0) {
            FirebaseVisionFace face = faces.get(0);
            bounds = face.getBoundingBox();
            Log.d(TAG, "getFaceResults: left = " + bounds.left);
            Log.d(TAG, "getFaceResults: right = " + bounds.right);
            Log.d(TAG, "getFaceResults: top = " + bounds.top);
            Log.d(TAG, "getFaceResults: bottom = " + bounds.bottom);
        }

        Bitmap croppedBmp = null;

        try {
            croppedBmp = Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height());
        } catch (Exception e) {
            Log.d(TAG, "");
        }


        if (croppedBmp != null) {

            Log.d(TAG, "getFaceResults: croppedBmp: " + croppedBmp);
            try {
                IdentifyEmotion(croppedBmp);
            } catch (FirebaseMLException e) {
                e.printStackTrace();
            }
        }
    }

    private void IdentifyEmotion(Bitmap croppedBmp) throws FirebaseMLException {

        Bitmap myBitmap = Bitmap.createScaledBitmap(croppedBmp, 64, 64, true);
        Bitmap bmpGrayscale = test(myBitmap);

        //Bitmap bmpGrayscale = Bitmap.createBitmap(myBitmap.getWidth(), myBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        //imageView.setImageBitmap(bmpGrayscale);

        int batchNum = 0;
        float[][][][] input = new float[1][64][64][1];
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                int pixel = bmpGrayscale.getPixel(x, y);
                input[batchNum][x][y][0] = (pixel - 127) / 255.0f;
            }
        }

        FirebaseModelInputs inputs = new FirebaseModelInputs.Builder()
                .add(input)
                .build();

        firebaseInterpreter.run(inputs, inputOutputOptions)
                .addOnSuccessListener(new OnSuccessListener<FirebaseModelOutputs>() {
                    @Override
                    public void onSuccess(FirebaseModelOutputs result) {
                        // [START_EXCLUDE]
                        // [START mlkit_read_result]
                        Log.d(TAG, "IdentifyEmotion: interpreter run successful");
                        float[][] output = result.getOutput(0);
                        float[] probabilities = output[0];
                        emotion = useInferenceResult(probabilities);
                        Log.d(TAG, "emotion successful :" + emotion);
                        getEmotion();

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Task failed with an exception
                        Log.d(TAG, "IdentifyEmotion: interpreter failed : " + e.getMessage());
                        Toast.makeText(getActivity(), "Can't detect your emotion", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public String useInferenceResult(float[] probabilities) {

        int max = 0;
        for (int i = 0; i < probabilities.length; i++) {
            if (probabilities[i] > probabilities[max]) max = i;
            Log.d(TAG, "useInferenceResult: prob: " + probabilities[i]);
        }
        return label.get(max);
    }

    @SuppressLint("SetTextI18n")
    private void getEmotion() {
        String myEmotion = emotion.substring(0, 1).toUpperCase() + emotion.substring(1);
        emotionTV.setText(myEmotion);
        getSongBasedOnYourEmotion();
    }

    private void getSongBasedOnYourEmotion() {

        databaseEmotionSong.collection("Songs")
                .whereEqualTo("songCategory", emotionTV.getText().toString())
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {

                        if (task.isSuccessful()) {
                            songListEmotion.clear();
                            for (QueryDocumentSnapshot document : task.getResult()) {

                                Model_Songs model_song = document.toObject(Model_Songs.class);
                                songListEmotion.add(model_song);
                            }
                            adapterEmotionSong = new AdapterEmotionSong(getActivity(), songListEmotion);
                            emotion_recyclerView.setAdapter(adapterEmotionSong);

                        } else {
                            Log.d(TAG, "getSongBasedOnYourEmotion: Error getting documents: " + task.getException());
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "getSongBasedOnYourEmotion: failed: " + e.getMessage());
            }
        });

    }

    private void getRecommendedSong() {

        databaseRecommendedSong.collection("Songs").get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {

                        if (task.isSuccessful()) {

                            ArrayList<Model_Songs> songList = new ArrayList<>();
                            for (DocumentSnapshot document : task.getResult()) {
                                Model_Songs model_song = document.toObject(Model_Songs.class);
                                songList.add(model_song);
                            }
                            int songListSize = songList.size() / 3;


                            for (int i = 0; i < songListSize; i++) {

                                Model_Songs randomSong = songList.get(new Random().nextInt(songListSize));
                                if (!songListRecommended.contains(randomSong)) {
                                    songListRecommended.add(randomSong);
                                    if (songListRecommended.size() == songListSize) {
                                        break;
                                    }
                                }
                            }

                            adapterRecommendedSong = new AdapterRecommendedSong(getActivity(), songListRecommended);
                            recommend_recyclerView.setAdapter(adapterRecommendedSong);

                        } else {
                            Log.d(TAG, "getRecommendedSong: Error getting documents: " + task.getException());
                        }

                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "getRecommendedSong: failed: " + e.getMessage());
            }
        });
    }

    public static Bitmap test(Bitmap src) {

        int width = src.getWidth();
        int height = src.getHeight();
        // create output bitmap
        Bitmap bmOut = Bitmap.createBitmap(width, height, src.getConfig());
        // color information
        int A, R, G, B;
        int pixel;
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                // get pixel color
                pixel = src.getPixel(x, y);
                A = Color.alpha(pixel);
                R = Color.red(pixel);
                G = Color.green(pixel);
                B = Color.blue(pixel);
                int gray = (int) (0.2989 * R + 0.5870 * G + 0.1140 * B);
                // use 128 as threshold, above -> white, below -> black
                if (gray > 128) {
                    gray = 255;
                } else {
                    gray = 0;
                }
                // set new pixel color to output bitmap
                bmOut.setPixel(x, y, Color.argb(A, gray, gray, gray));
            }
        }
        return bmOut;
    }

}




