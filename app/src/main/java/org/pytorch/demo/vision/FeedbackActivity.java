package org.pytorch.demo.vision;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;

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

    private String serial = Utils.getMacAddr();

    private Module mModuleEncoder;
    private Module mModuleDecoder;
    private Tensor mInputTensor;
    private LongBuffer mInputTensorBuffer;

    private EditText mFeedbackSummary;
    private EditText mFeedbackDetails;
    private TextView mTextView;
    private Button mButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        mButton = findViewById(R.id.btnFeedbackSubmit);
        mFeedbackSummary = findViewById(R.id.feedbackSummary);
        mFeedbackDetails = findViewById(R.id.feedbackDetails);
        mTextView = findViewById(R.id.feedbackStatus);
        initTable();

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

    }

    private void showTranslationResult(String result) {
        mTextView.setText(result);
        mTextView.setTextColor(Color.BLUE);
        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        mFeedbackSummary.setHint("Please enter your Feedback Summary here");
        mFeedbackSummary.setText(null);
        mFeedbackDetails.setHint("Please enter your Feedback Details here");
        mFeedbackDetails.setText(null);
    }

    public void run() {
        final String result = callCreateFeedbackAPI(mFeedbackSummary.getText().toString(), mFeedbackDetails.getText().toString());
        JSONArray feedbacks = callGetFeedbacksAPI();
        Log.i("feedbacks length / count: ", String.valueOf(feedbacks.length()));
        // try {
        //     for (int i = 0 ; i < feedbacks.length(); i++) {
        //         JSONObject obj = feedbacks.getJSONObject(i);
        //         Log.i("summary: ", obj.getString("summary"));
        //         Log.i("details: ", obj.getString("details"));
        //         Log.i("imgLink: ", obj.getString("imgLink"));
        //         Log.i("createdDatetime: ", obj.getString("createdDatetime"));
        //     }
        // } catch (JSONException e) {
        //     //some exception handler code.
        // }
        runOnUiThread(() -> {
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
                }
            } catch (JSONException e) {
                //some exception handler code.
            }
            showTranslationResult(result);
            mButton.setEnabled(true);
        });
    }

    @Nullable
    private String callCreateFeedbackAPI(final String summary, final String details) {
        final String[] result = {null};
        Log.i("serial:", serial);
        Log.i("summary:", summary);
        Log.i("details:", details);
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("serial", serial);
            jsonObject.put("summary", summary);
            jsonObject.put("details", details);

            ANRequest request = AndroidNetworking.post("https://x6zqxtaxgr52.share.zrok.io/feedback/create/")
                    .addJSONObjectBody(jsonObject) // posting json
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

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
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

}