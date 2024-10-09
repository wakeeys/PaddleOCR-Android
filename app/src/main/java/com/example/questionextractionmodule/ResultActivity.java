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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.image.data.DataUriSchemeHandler;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
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
            "</script>" +
            "<style>body {width: 100%;overflow-x: hidden;margin: 0;padding: 0;}"+
            "table {width: 100%;border-collapse: collapse;margin: 0 auto;}" +  // Add margin: 0 auto to center the table
            "table, th, td {border: 1px solid black;}" +
            "th, td {padding: 8px;text-align: left;}" +
            "img {display: block;margin: 0 auto;max-width: 100%;height: auto;}</style>" +  // Combine image and table styles
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
        resultImageView = findViewById(R.id.resulted_image);
        //resultWebView = findViewById(R.id.web_view);
        resultTextView = findViewById(R.id.textView);

        // Get the cropped image Uri from the intent
        Intent intent = getIntent();
        String uriString = intent.getStringExtra("CROPPED_IMAGE_URI");

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

    private String processContent(String markdownTemp){
        // replace $ to $$
        String placeholder = "TEMP_PLACEHOLDER";
        markdownTemp = markdownTemp.replace("$$", placeholder);
        markdownTemp = markdownTemp.replace("$", "$$");
        markdownTemp = markdownTemp.replace(placeholder, "$$");
        // replace \ to \\ except for \\ and \n


        // add a \n after \n except tha the \n is between | and |
        String regex1 = "\n", regex2 = "\\|\n\n\\|";
        markdownTemp = markdownTemp.replaceAll(regex1, "\n\n");
        markdownTemp = markdownTemp.replaceAll(regex2, "\\|\n\\|");
        Log.e("re", markdownTemp);
        return markdownTemp;
    }

    private void showResult(String res, Bitmap outputImage, String res2){
        String encodedImage = bitmapToBase64(outputImage);
        String temp;
        String latexTemp = "<p>Here is an example of LaTeX formula: \\( E = mc^2\\) & \\( \\frac{\\sqrt[3](a)}{a_5^6} \\)</p>";
        //res = latexTemp + res;
//        if (!encodedImage.isEmpty()) { // have a image
//            temp = content1 + res + emptyLineBeforeImage + content3 + encodedImage + content4 + emptyLineBeforeImage + res2 + content2;
//        } else {
//            temp = content1 + res + emptyLineBeforeImage + res2+ content2;
//        }
//        resultWebView.loadDataWithBaseURL(null, temp, "text/html", "utf-8", null);
        //String markdownTemp = "知识点一 加速度有哪些内容？[answer]<br> **1.定义**：加速度是 速度的变化量 与发生这一变化所用 时间 之比。<br>**2.定义式：** 在$a = \\frac{\\Delta v}{\\Delta t}$。 $E = mc^2$ <br> **3.单位：** 在国际单位制中，单位是米每二次方秒，符号是$m/s^{2}$或$m \\cdot s^{- 2}$。<br> **4.物理意义：** 描述物体 速度变化快慢 的物理量。<br>**点拨**由公式$a = \\frac{\\Delta v}{\\Delta t}$计算出的加速度是平均加速度，是过程量，与一段时间（一段位移）相对应，粗略地反映了物体速度变化的快慢。当$\\left. \\Delta t\\rightarrow 0 \\right.$时，平均加速度可视为瞬时加速度，瞬时加速度为状态量。[answer]<br>  知识点三 从v-t 图像看加速度的内容有哪些？[answer]1.定性判断：v-t 图线的 倾斜 程度反映加速度的大小。2.定量计算：在v-t 图像上取两点$E\\left( {t_{1},v_{1}} \\right)$、$F\\left( {t_{2},v_{2}} \\right)$，加速度$a = \\frac{\\Delta v}{\\Delta t} =\\frac{v_{2} - v_{1}}{t_{2} - t_{1}}$。[answer]";
        String markdownText = "$x-t$ 图像与$v-t$ 图像的比较<br>hello world $$\\frac{mc_1^2}{\\Delta \\bar{s}}$$ <br>\n" + "| 比较内容<br> | == | $x-t$ 图像<br> | $v-t$ 图像<br> |\n| --- | --- | --- | --- |\n| 图像<br> | == | ![Image]()<br> | ![Image]()<br> |\n| 物理意义<br> | == | 反映的是位移随时间的变化规律<br> | 反映的是速度随时间的变化规律<br> |\n| 物体的运动性质<br> | ①<br> | 表示物体从位移为正处开始一直沿负方向做匀速直线运动并过零位移处<br> | 表示物体先做正向匀减速直线运动，再做负向匀加速直线运动<br> |\n| 物体的运动性质<br> | ②<br> | 表示物体静止不动<br> | 表示物体做正向匀速直线运动<br> |\n| 物体的运动性质<br> | ③<br> | 表示物体从位移为零处开始做正向匀速运动<br> | 表示物体从静止开始做正向匀加速直线运动<br> |\n| 物体的运动性质<br> | ④<br> | 表示物体做加速直线运动<br> | 表示物体做加速度逐渐增大的加速运动<br> |\n| 图线与时间轴围成的“面积”的意义<br> | == | 无实际意义<br> | 表示相应时间内的位移<br> |";
        //String markdownTemp = "\frac{1}{2}";
        String markdownTemp = processContent("识别结果: "+res+ "\n\n\n" + markdownText);
        Markwon markwon = Markwon.builder(this)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(GlideImagesPlugin.create(this))  // Use Glide to load images in Markdown
                .usePlugin(ImagesPlugin.create())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configure(@NonNull Registry registry) {
                        // 配置 ImagesPlugin 来处理 Base64 图像
                        registry.require(ImagesPlugin.class, new Action<ImagesPlugin>() {
                            @Override
                            public void apply(@NonNull ImagesPlugin imagesPlugin) {
                                imagesPlugin.addSchemeHandler(DataUriSchemeHandler.create());
                            }
                        });
                    }
                })
                .usePlugin(TablePlugin.create(this))        // tablePlugin to load tables
                .usePlugin(MarkwonInlineParserPlugin.create())  // markdown inline
                .usePlugin(JLatexMathPlugin.create(resultTextView.getTextSize(), new JLatexMathPlugin.BuilderConfigure() {
                    @Override
                    public void configureBuilder(@NonNull JLatexMathPlugin.Builder builder) {
                        // ENABLE inlines
                        builder.inlinesEnabled(true);
                    }
                }))
                .build();

        // 使用 Markwon 将 Markdown 渲染到 TextView
        markwon.setMarkdown(resultTextView, markdownTemp);
        resultImageView.setImageBitmap(outputImage);
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

















