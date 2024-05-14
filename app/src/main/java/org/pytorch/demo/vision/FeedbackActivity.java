package org.pytorch.demo.vision;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
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

    public String serial = Utils.getMacAddr();

    private Module mModuleEncoder;
    private Module mModuleDecoder;
    private Tensor mInputTensor;
    private LongBuffer mInputTensorBuffer;

    private EditText mFeedbackSummary;
    private EditText mFeedbackDetails;
    private TextView mTextView;
    private Button mButton;

    // private static String assetFilePath(Context context, String assetName) throws IOException {
    //     File file = new File(context.getFilesDir(), assetName);
    //     if (file.exists() && file.length() > 0) {
    //         return file.getAbsolutePath();
    //     }

    //     try (InputStream is = context.getAssets().open(assetName)) {
    //         try (OutputStream os = new FileOutputStream(file)) {
    //             byte[] buffer = new byte[4 * 1024];
    //             int read;
    //             while ((read = is.read(buffer)) != -1) {
    //                 os.write(buffer, 0, read);
    //             }
    //             os.flush();
    //         }
    //         return file.getAbsolutePath();
    //     }
    // }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        mButton = findViewById(R.id.btnFeedbackSubmit);
        mFeedbackSummary = findViewById(R.id.feedbackSummary);
        mFeedbackDetails = findViewById(R.id.feedbackDetails);
        mTextView = findViewById(R.id.feedbackStatus);

        mButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mButton.setEnabled(false);
                Thread thread = new Thread(FeedbackActivity.this);
                thread.start();
            }
        });
    }

    private void showTranslationResult(String result) {
        mTextView.setText(result);
        mFeedbackSummary.setHint("Please enter your Feedback Summary here");
        mFeedbackSummary.setText(null);
        mFeedbackDetails.setHint("Please enter your Feedback Details here");
        mFeedbackDetails.setText(null);
    }

    public void run() {
        final String result = callFeedbackAPI(mFeedbackSummary.getText().toString(), mFeedbackDetails.getText().toString());
        runOnUiThread(() -> {
            showTranslationResult(result);
            mButton.setEnabled(true);
        });
    }

    @Nullable
    private String callFeedbackAPI(final String summary, final String details) {
        final String[] result = {null};
        Log.i("serial;", serial);
        Log.i("summary;", summary);
        Log.i("details;", details);
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

            Log.i("response success:;", String.valueOf(response.isSuccess()));
            Log.i("response;", String.valueOf(response));

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

}