package org.pytorch.demo.vision;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.Toast;
import android.graphics.Matrix;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.Environment;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TableLayout.LayoutParams;
import android.view.Gravity;
import android.graphics.Color;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.PyTorchAndroid;
import org.pytorch.Tensor;

import org.pytorch.demo.R;
import org.pytorch.demo.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.Date;
import android.icu.text.SimpleDateFormat;
import java.util.Locale;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.ANRequest;
import com.androidnetworking.common.ANResponse;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.JSONObjectRequestListener;

public class FeedbackActivity extends AppCompatActivity implements Runnable{
    // to be consistent with the model inputs defined in seq2seq_nmt.py, based on
    // https://pytorch.org/tutorials/intermediate/seq2seq_translation_tutorial.html
    private static final int HIDDEN_SIZE = 256;
    private static final int EOS_TOKEN = 1;
    private static final int MAX_LENGTH = 50;
    private static final String TAG = FeedbackActivity.class.getName();

    public static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 101;

    private String serial = Utils.getMacAddr();

    private Module mModuleEncoder;
    private Module mModuleDecoder;
    private Tensor mInputTensor;
    private LongBuffer mInputTensorBuffer;

    private EditText mFeedbackSummary;
    private EditText mFeedbackDetails;
    private TextView mTextView;
    private Button mButton;
    private Button mButtonImage;
    private ImageView mFeedbackImageView;

    private Uri mImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);
        // Allow strict mode
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        mButton = findViewById(R.id.btnFeedbackSubmit);
        mFeedbackImageView = findViewById(R.id.feedbackImageView);
        mButtonImage = findViewById(R.id.btnFeedbackImage);
        mFeedbackSummary = findViewById(R.id.feedbackSummary);
        mFeedbackDetails = findViewById(R.id.feedbackDetails);
        mTextView = findViewById(R.id.feedbackStatus);
        initTable();

        mButtonImage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(checkAndRequestPermissions(FeedbackActivity.this)){
                    chooseImage(FeedbackActivity.this);
                }
                // startActivity(new Intent(FeedbackActivity.this, FeedbackImageActivity.class));
            }
        });


        mButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mButton.setEnabled(false);
                Thread thread = new Thread(FeedbackActivity.this);
                thread.start();
            }
        });
    }

    public void initTable() {
        TableLayout stk = (TableLayout) findViewById(R.id.tableMain);
        TableRow tbrow0 = new TableRow(this);
        TextView tv0 = new TextView(this);
        tv0.setText("No.");
        tv0.setTextColor(Color.WHITE);
        tv0.setGravity(Gravity.LEFT);
        tv0.setPadding(30,20,0,20);
        tv0.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        tbrow0.addView(tv0);
        TextView tv1 = new TextView(this);
        tv1.setText("Summary");
        tv1.setTextColor(Color.WHITE);
        tv1.setGravity(Gravity.LEFT);
        tv1.setPadding(10,20,0,20);
        tv1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        tbrow0.addView(tv1);
        TextView tv2 = new TextView(this);
        tv2.setText("Details");
        tv2.setTextColor(Color.WHITE);
        tv2.setGravity(Gravity.LEFT);
        tv2.setPadding(10,20,0,20);
        tv2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        tbrow0.addView(tv2);
        TextView tv3 = new TextView(this);
        tv3.setText("Date Time");
        tv3.setTextColor(Color.WHITE);
        tv3.setGravity(Gravity.LEFT);
        tv3.setPadding(10,20,0,20);
        tv3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        tbrow0.addView(tv3);
        stk.addView(tbrow0);
        JSONArray feedbacks = callGetFeedbacksAPI();
        Log.i("feedbacks length / count: ", String.valueOf(feedbacks.length()));
        renderTable(feedbacks);
    }

    private void showTranslationResult(String result) {
        mTextView.setText(result);
        mTextView.setTextColor(Color.BLUE);
        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        mFeedbackSummary.setHint("Please enter your Feedback Summary here");
        mFeedbackSummary.setText(null);
        mFeedbackDetails.setHint("Please enter your Feedback Details here");
        mFeedbackDetails.setText(null);
        mFeedbackImageView.setImageDrawable(null);
        mFeedbackImageView.getLayoutParams().height = 1;
        mButtonImage.setText("Take Photo or Choose from Gallery");
    }

    private void renderTable(JSONArray feedbacks) {
        TableLayout stk = (TableLayout) findViewById(R.id.tableMain);
        stk.removeViews(1, Math.max(0, stk.getChildCount() - 1));
        try {
            for (int i = 0 ; i < feedbacks.length(); i++) {
                JSONObject obj = feedbacks.getJSONObject(i);
                TableRow tbrow = new TableRow(this);
                TextView t0v = new TextView(this);
                Log.i("count: ", String.valueOf(i));
                t0v.setText(String.valueOf(i));
                t0v.setTextColor(Color.WHITE);
                t0v.setBackgroundColor(Color.BLUE);
                t0v.setGravity(Gravity.LEFT);
                t0v.setPadding(30,20,0,20);
                t0v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
                tbrow.addView(t0v);
                TextView t1v = new TextView(this);
                Log.i("summary: ", obj.getString("summary"));
                t1v.setText(obj.getString("summary"));
                t1v.setTextColor(Color.WHITE);
                t1v.setBackgroundColor(Color.GRAY);
                t1v.setGravity(Gravity.LEFT);
                t1v.setPadding(30,20,0,20);
                t1v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
                tbrow.addView(t1v);
                TextView t2v = new TextView(this);
                Log.i("details: ", obj.getString("details"));
                t2v.setText(obj.getString("details"));
                t2v.setTextColor(Color.WHITE);
                t2v.setBackgroundColor(Color.GRAY);
                t2v.setGravity(Gravity.LEFT);
                t2v.setPadding(10,20,0,20);
                t2v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
                tbrow.addView(t2v);
                TextView t3v = new TextView(this);
                Log.i("imgLink: ", obj.getString("imgLink"));
                Log.i("createdDatetime: ", obj.getString("createdDatetime"));
                t3v.setText(obj.getString("createdDatetime"));
                t3v.setTextColor(Color.WHITE);
                t3v.setBackgroundColor(Color.GRAY);
                t3v.setGravity(Gravity.LEFT);
                t3v.setPadding(10,20,0,20);
                t3v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
                tbrow.addView(t3v);
                stk.addView(tbrow);
                tbrow = new TableRow(this);
                TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
                layoutParams.span = 4;
                tbrow.setLayoutParams(layoutParams);
                t3v = new TextView(this);
                callGetImageByNameAPI(obj.getString("imgLink"));
                t3v.setText(obj.getString("imgLink"));
                t3v.setTextColor(Color.WHITE);
                t3v.setBackgroundColor(Color.GRAY);
                t3v.setGravity(Gravity.LEFT);
                t3v.setPadding(10,20,0,20);
                t3v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
                t3v.setLayoutParams(layoutParams); 
                tbrow.addView(t3v);
                stk.addView(tbrow);
                tbrow = new TableRow(this);
                layoutParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
                layoutParams.span = 4;
                tbrow.setLayoutParams(layoutParams);
                t3v = new TextView(this);
                t3v.setText("----------------------");
                t3v.setTextColor(Color.WHITE);
                t3v.setBackgroundColor(Color.GRAY);
                t3v.setGravity(Gravity.LEFT);
                t3v.setPadding(10,20,0,20);
                t3v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
                t3v.setLayoutParams(layoutParams); 
                tbrow.addView(t3v);
                stk.addView(tbrow);
            }
        } catch (JSONException e) {
            //some exception handler code.
        }
    }

    public void run() {
        if (mFeedbackImageView.getDrawable() == null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Bitmap bMap = ((BitmapDrawable) mFeedbackImageView.getDrawable()).getBitmap();
            bMap.compress(CompressFormat.JPEG, 100, bos);
            byte[] bitMapData = bos.toByteArray();

            try {
                //create a file to write bitmap data
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSSZ", Locale.getDefault());
                String currentDateAndTime = sdf.format(new Date());
                String fileName = serial.replace(":","_") + "__" + currentDateAndTime + ".png";
                File fImage = new File(FeedbackActivity.this.getCacheDir(), fileName);
                fImage.createNewFile();

                //write the bytes in file
                FileOutputStream fos = new FileOutputStream(fImage);

                fos.write(bitMapData);
                fos.flush();
                fos.close();

                final String result = callCreateFeedbackAPI(
                mFeedbackSummary.getText().toString(),
                mFeedbackDetails.getText().toString(),
                fImage
                );

                JSONArray feedbacks = callGetFeedbacksAPI();
                Log.i("feedbacks length / count: ", String.valueOf(feedbacks.length()));
                runOnUiThread(() -> {
                    renderTable(feedbacks);
                    showTranslationResult(result);
                    mButton.setEnabled(true);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Bitmap bMap = ((BitmapDrawable) mFeedbackImageView.getDrawable()).getBitmap();
            bMap.compress(CompressFormat.JPEG, 100, bos);
            byte[] bitMapData = bos.toByteArray();

            try {
                //create a file to write bitmap data
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSSZ", Locale.getDefault());
                String currentDateAndTime = sdf.format(new Date());
                String fileName = serial.replace(":","_") + "__" + currentDateAndTime + ".png";
                File fImage = new File(FeedbackActivity.this.getCacheDir(), fileName);
                fImage.createNewFile();

                //write the bytes in file
                FileOutputStream fos = new FileOutputStream(fImage);

                fos.write(bitMapData);
                fos.flush();
                fos.close();

                final String result = callCreateFeedbackAPI(
                mFeedbackSummary.getText().toString(),
                mFeedbackDetails.getText().toString(),
                fImage
                );

                JSONArray feedbacks = callGetFeedbacksAPI();
                Log.i("feedbacks length / count: ", String.valueOf(feedbacks.length()));
                runOnUiThread(() -> {
                    renderTable(feedbacks);
                    showTranslationResult(result);
                    mButton.setEnabled(true);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        
    }

    @Nullable
    private String callCreateFeedbackAPI(final String summary, final String details, final File image) {
        final String[] result = {null};
        Log.i("serial:", serial);
        Log.i("summary:", summary);
        Log.i("details:", details);
        Log.i("image length:", String.valueOf(image.length()));
        Log.i("image name:", image.getName());
        Log.i("image is file?:", String.valueOf(image.isFile()));

        ANRequest request = AndroidNetworking.upload("https://x6zqxtaxgr52.share.zrok.io/feedback/create/")
                .addQueryParameter("serial", serial) // serial as query param
                .addQueryParameter("summary", summary) // summary as query param
                .addQueryParameter("details", details) // details as query param
                .addQueryParameter("img_link", image.getName()) // details as query param
                .addMultipartFile("feedback_file",image)  
                .addHeaders("accept", "application/json")
                .addHeaders("Content-Type", "application/json")
                .addHeaders("token", "ICjgxQXFB9_UjD7UKP5-Qti4ymx1dfH5YyOdHIT04LZCycRPuXSZpLeVfWgYC4KjMaqA1nPLXwq3c6CVw07dXw")
                .setTag("test")
                .build();
        
        ANResponse<JSONObject> response = request.executeForJSONObject();

        Log.i("POST API response success: ", String.valueOf(response.isSuccess()));
        Log.i("POST API response: ", String.valueOf(response));

        if (response.isSuccess()) {
            Log.i("Successful API call feedback/create/:", String.valueOf(response.getResult()));
            result[0] = "Feedback Submission: Successful";
        } else {
            ANError error = response.getError();
            Log.e("Failed API call feedback/create/:", String.valueOf(error));
            result[0] = "Feedback Submission: Failed";
        }

        return result[0];
    }

    @Nullable
    private JSONArray callGetFeedbacksAPI() {
        JSONArray result = new JSONArray();
        Log.i("serial:", serial);

        ANRequest request = AndroidNetworking.get("https://x6zqxtaxgr52.share.zrok.io/feedback/")
                .addQueryParameter("serial", serial) // serial as query param
                .addHeaders("accept", "application/json")
                .addHeaders("Content-Type", "application/json")
                .addHeaders("token", "ICjgxQXFB9_UjD7UKP5-Qti4ymx1dfH5YyOdHIT04LZCycRPuXSZpLeVfWgYC4KjMaqA1nPLXwq3c6CVw07dXw")
                .setTag("test")
                .build();
        
        Log.i("GET API request: ", String.valueOf(request));
        
        ANResponse<JSONArray> response = request.executeForJSONArray();

        Log.i("GET API response success: ", String.valueOf(response.isSuccess()));
        Log.i("GET API response: ", String.valueOf(response));

        if (response.isSuccess()) {
            Log.i("Successful GET API call feedbacks:", String.valueOf(response.getResult().length()));
            result = response.getResult();
        } else {
            ANError error = response.getError();
            Log.e("Failed GET API call feedbacks:", String.valueOf(error));
            result = null;
        }

        return result;
    }

    @Nullable
    private JSONArray callGetImageByNameAPI(final String filename) {
        JSONArray result = new JSONArray();
        Log.i("filename:", filename);

        ANRequest request = AndroidNetworking.get("https://x6zqxtaxgr52.share.zrok.io/feedback/img/name")
                .addQueryParameter("filename", filename) // filename as query param
                .addHeaders("accept", "application/json")
                .addHeaders("Content-Type", "application/json")
                .addHeaders("token", "ICjgxQXFB9_UjD7UKP5-Qti4ymx1dfH5YyOdHIT04LZCycRPuXSZpLeVfWgYC4KjMaqA1nPLXwq3c6CVw07dXw")
                .setTag("test")
                .build();
        
        Log.i("GET API request: ", String.valueOf(request));
        
        ANResponse<JSONArray> response = request.executeForJSONArray();

        Log.i("GET API response success: ", String.valueOf(response.isSuccess()));
        Log.i("GET API response: ", String.valueOf(response));

        if (response.isSuccess()) {
            Log.i("Successful GET API call feedbacks:", String.valueOf(response.getResult().length()));
            result = response.getResult();
        } else {
            ANError error = response.getError();
            Log.e("Failed GET API call feedbacks:", String.valueOf(error));
            result = null;
        }

        return result;
    }

    // function to let's the user to choose image from camera or gallery

    private void chooseImage(Context context){

        final CharSequence[] optionsMenu = {"Take Photo", "Choose from Gallery", "Exit" }; // create a menuOption Array

        // create a dialog for showing the optionsMenu

        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        // set the items in builder

        builder.setItems(optionsMenu, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

                if(optionsMenu[i].equals("Take Photo")){

                    // Open the camera and get the photo

                    Intent takePicture = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

                    try {
                        mImageUri = Uri.fromFile(getTempFile());
                        takePicture.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
                    }
                    catch(Exception e)
                    {
                        Log.v(TAG, "!@#$!");
                        Log.v(TAG, "Can't create file to take picture!");
                        Log.v(TAG, "!@#$!!!");
                        Toast.makeText(FeedbackActivity.this, "Please check SD card! Image shot is impossible!", 10000);
                    }
                    

                    startActivityForResult(takePicture, 0);
                }
                else if(optionsMenu[i].equals("Choose from Gallery")){

                    // choose from  external storage

                    Intent pickPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(pickPhoto , 1);

                }
                else if (optionsMenu[i].equals("Exit")) {
                    dialogInterface.dismiss();
                    finish();
                }

            }
        });
        builder.show();
    }

    private File getTempFile()
    {
        //it will return /sdcard/image.tmp
        return new File(Environment.getExternalStorageDirectory(),  "image.tmp");
    }

    // function to check permission

    public static boolean checkAndRequestPermissions(final Activity context) {
        int WExtstorePermission = ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int cameraPermission = ContextCompat.checkSelfPermission(context,
                Manifest.permission.CAMERA);
        List<String> listPermissionsNeeded = new ArrayList<>();
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (WExtstorePermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded
                    .add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(context, listPermissionsNeeded
                            .toArray(new String[listPermissionsNeeded.size()]),
                    REQUEST_ID_MULTIPLE_PERMISSIONS);
            return false;
        }
        return true;
    }

    // Handled permission Result


    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSIONS:
                if (ContextCompat.checkSelfPermission(FeedbackActivity.this,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(),
                            "FlagUp Requires Access to Camara.", Toast.LENGTH_SHORT)
                            .show();

                } else if (ContextCompat.checkSelfPermission(FeedbackActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(),
                            "FlagUp Requires Access to Your Storage.",
                            Toast.LENGTH_SHORT).show();

                } else {
                    chooseImage(FeedbackActivity.this);
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            switch (requestCode) {
                case 0:
                    if (resultCode == RESULT_OK) {
                        Log.v(TAG, "*******");
                        Bitmap selectedImage = null;
                        try {
                            selectedImage = BitmapFactory.decodeStream(getContentResolver().openInputStream(mImageUri));
                        }
                        catch (FileNotFoundException e) 
                        {
                            e.printStackTrace();
                        }
                        Matrix matrix = new Matrix();
                        matrix.postRotate(90);
                        selectedImage = Bitmap.createBitmap(selectedImage, 0, 0,
                        selectedImage.getWidth(), selectedImage.getHeight(),
                        matrix, true);
                        mFeedbackImageView.getLayoutParams().height = 400;
                        mFeedbackImageView.setImageBitmap(selectedImage);
                        mButtonImage.setText("Optional: **Re-Take** Photo or Choose Again");
                    }
                    break;
                case 1:
                    if (resultCode == RESULT_OK && data != null) {
                        Uri selectedImagePath = data.getData();
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};
                        if (selectedImagePath != null) {
                            Cursor cursor = getContentResolver().query(selectedImagePath, filePathColumn, null, null, null);
                            if (cursor != null) {
                                cursor.moveToFirst();

                                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                                String picturePath = cursor.getString(columnIndex);
                                Bitmap selectedImage = (Bitmap) BitmapFactory.decodeFile(picturePath);
                                Matrix matrix = new Matrix();
                                matrix.postRotate(90);
                                selectedImage = Bitmap.createBitmap(selectedImage, 0, 0,
                                selectedImage.getWidth(), selectedImage.getHeight(),
                                matrix, true);
                                mFeedbackImageView.getLayoutParams().height = 400;
                                mFeedbackImageView.setImageBitmap(selectedImage);
                                mButtonImage.setText("Optional: **Re-Take** Photo or Choose Again");
                                cursor.close();
                            }
                        }

                    }
                    break;
            }
        }
    }

}