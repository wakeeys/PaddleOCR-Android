package com.example.questionextractionmodule;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.Callback;
import retrofit2.Response;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Bitmap;
import android.util.Base64;
import java.io.ByteArrayOutputStream;


public class ResultActivity extends AppCompatActivity {
    // Model settings of ocr
    protected String modelPath = "models";
    protected String labelPath = "labels/ppocr_keys_v1.txt";
    protected int cpuThreadNum = 1;
    protected String cpuPowerMode = "LITE_POWER_HIGH";
    protected int detLongSize = 2048;
    protected float scoreThreshold = 0.1f;
    private String currentPhotoPath;
    private AssetManager assetManager = null;

    protected Predictor predictor = new Predictor();

    private ImageView croppedImageView;
    Uri croppedImageUri;
    private ImageView resultImageView;
    private TextView resultTextView;
    private WebView resultWebView;
    String res;
    String content1 = "<html>" +
            "<head>" +
            "<script type=\"text/x-mathjax-config\">" +
            "MathJax.Hub.Config({" +
            "tex2jax: {inlineMath: [['$','$'], ['\\\\(','\\\\)']]}" +
            "});" +
            "</script>" +
            "<script type=\"text/javascript\" async " +
            "src=\"https://cdnjs.cloudflare.com/ajax/libs/mathjax/2.7.7/MathJax.js?config=TeX-MML-AM_CHTML\">" +
            "</script>"+
            "<style>body {width: 100%;overflow-x: hidden;margin: 0;padding: 0;}"+  // prohibit the sliding left or right
            "img {display: block;margin: 0 auto;max-width: 100%;height: auto;}</style>" +  // display the image in the center
            "</head>" +
            "<body>";
    String content2 = "</body></html>";
    String content3 = "<img src=\"data:image/jpeg;base64,";
    String content4 = "\" style=\"width:100%;height:auto;overflow-x:hidden;\" />";
    String emptyLineBeforeImage = "<br/><br/>";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        croppedImageView = findViewById(R.id.cropped_image);
        //resultImageView = findViewById(R.id.resulted_image);
        resultWebView = findViewById(R.id.web_view);
        //resultTextView = findViewById(R.id.text_result);

        // Get the cropped image Uri from the intent
        Intent intent = getIntent();
        String uriString = intent.getStringExtra("CROPPED_IMAGE_URI");

        resultWebView.getSettings().setJavaScriptEnabled(true);
        resultWebView.setBackgroundColor(0x00000000); // 透明颜色
        resultWebView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);

        resultWebView.setHorizontalScrollBarEnabled(false);
        resultWebView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        resultWebView.setVerticalScrollBarEnabled(false);

        if (uriString != null) {
            croppedImageUri = Uri.parse(uriString);
            croppedImageView.setImageURI(croppedImageUri);
            // new UploadImageTask().execute(croppedImageUri);

            loadModelAndRunInference();
        } else {
            Toast.makeText(this, "No image received", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadModelAndRunInference() {
        // Load the model in a background thread
        new Thread(() -> {
            boolean modelLoaded = predictor.init(this, modelPath, labelPath, 0, cpuThreadNum, cpuPowerMode, detLongSize, scoreThreshold);
            if (modelLoaded) {
                runModel();
            } else {
                runOnUiThread(() -> Toast.makeText(this, "Model loading failed!", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public boolean onRunModel() {
        String run_mode = "检测+分类+识别";      //spRunMode.getSelectedItem().toString();
        int run_det = run_mode.contains("检测") ? 1 : 0;
        int run_cls = run_mode.contains("分类") ? 1 : 0;
        int run_rec = run_mode.contains("识别") ? 1 : 0;
        return predictor.runModel(run_det, run_cls, run_rec);
    }

    public String bitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) {
            return "";
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream); // choose jpeg or png
        byte[] byteArray = outputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    private void showResult(String res, Bitmap outputImage, String ress){
        String encodedImage = bitmapToBase64(outputImage);
        String temp;
        String latexTemp = "<p>Here is an example of LaTeX formula: \\( E = mc^2\\) & \\( \\frac{\\sqrt[3](a)}{a_5^6} \\)</p>";
        res = latexTemp + res;
        if (!encodedImage.isEmpty()) { // have a image
            temp = content1 + res + emptyLineBeforeImage + content3 + encodedImage + content4 + emptyLineBeforeImage + ress + content2;
        } else {
            temp = content1 + res + emptyLineBeforeImage + ress+ content2;
        }
        resultWebView.loadDataWithBaseURL(null, temp, "text/html", "utf-8", null);
    }

    private String processResult(String resultText){
        String res = "";
        ArrayList<String> results = new ArrayList<>();

        Pattern pattern = Pattern.compile("Rec:(.*?)(,|$)");
        Matcher matcher = pattern.matcher(resultText);
        while (matcher.find()) {
            results.add(matcher.group(1).trim());
        }
        for (int i = results.size() - 1; i >= 0; i--) {
            res += results.get(i);
        }
        return res;
    }

    private void runModel() {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), croppedImageUri);
            if (bitmap != null) {
                predictor.setInputImage(bitmap);
                boolean success = onRunModel();
                runOnUiThread(() -> {
                    if (success) {
                        Bitmap outputImage = predictor.outputImage();
                        String resultText = predictor.outputResult();
                        res = processResult(resultText);
                        showResult(res, outputImage, "");
                        //resultTextView.setText(res);
                    } else {
                        Toast.makeText(this, "Model inference failed!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
            runOnUiThread(() -> Toast.makeText(this, "Failed to load image!", Toast.LENGTH_SHORT).show());
        }
    }

    private class UploadImageTask extends AsyncTask<Uri, Void, MultipartBody.Part> {
        @Override
        protected MultipartBody.Part doInBackground(Uri... uris) {
            Uri imageUri = uris[0];
            Bitmap bitmap;

            try {
                ContentResolver contentResolver = getContentResolver();
                InputStream inputStream = contentResolver.openInputStream(imageUri);
                bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
            }
            catch (IOException e) {
                e.printStackTrace();
                return null;
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            byte[] imageBytes = bos.toByteArray();

            RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), imageBytes);
            return MultipartBody.Part.createFormData("image", "image.jpg", requestFile);
        }

        @Override
        protected void onPostExecute(MultipartBody.Part body) {
            if (body != null) {
                OkHttpClient okHttpClient = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)  
                        .writeTimeout(30, TimeUnit.SECONDS)    
                        .readTimeout(30, TimeUnit.SECONDS)     
                        .build();

                Retrofit retrofit = new Retrofit.Builder()
                        .baseUrl("http://192.168.1.8:5000/")    // Replace with your server URL
                        .addConverterFactory(GsonConverterFactory.create())
                        .client(okHttpClient)
                        .build();

                ApiService apiService = retrofit.create(ApiService.class);

                // sent requirement
                Call<ResponseBody> call = apiService.predict(body);
                call.enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                // Convert response body to string
                                String responseBodyString = response.body().string();
                                JSONObject jsonResponse = new JSONObject(responseBodyString);

                                Log.d("JSONResponse", jsonResponse.toString());
                                // Get the text and image from the JSON response
                                String textResult = jsonResponse.getString("text");
                                String imageBase64 = jsonResponse.getString("image");

                                // Decode the base64 image string to Bitmap
                                byte[] decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT);
                                Bitmap resultBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                                // Display text and image in the UI
                                runOnUiThread(() -> {
                                    resultTextView.setText(textResult);
                                    resultImageView.setImageBitmap(resultBitmap);
                                });

                            } catch (IOException e) {
                                Log.e("Prediction", "Error processing response", e);
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            Log.e("Prediction", "Request failed: " + response.message());
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Log.e("Prediction", "Network request failed", t);
                    }
                });
            } else {
                Log.e("Prediction", "Image upload failed");
            }
        }
    }
}

















