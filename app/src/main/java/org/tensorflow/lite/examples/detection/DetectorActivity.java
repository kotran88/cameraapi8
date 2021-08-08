/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Detector;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener, TextToSpeech.OnInitListener {
  private static final Logger LOGGER = new Logger();

  TextToSpeech textToSpeech;
  boolean firsttime=true;

  ConcurrentLinkedQueue<String>queue_all=new ConcurrentLinkedQueue<>();
  ConcurrentLinkedQueue<String>queue=new ConcurrentLinkedQueue<>();
  // Configuration values for the prepackaged SSD model.
  private static final int TF_OD_API_INPUT_SIZE = 640;
  private static final boolean  TF_OD_API_IS_QUANTIZED = false;
  private static final String TF_OD_API_MODEL_FILE = "model.tflite";
  private static final String TF_OD_API_LABELS_FILE = "aa.txt";
  private static final DetectorMode MODE = DetectorMode.TF_OD_API;
  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.3f;
  private static final boolean MAINTAIN_ASPECT = false;
  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;
  OverlayView trackingOverlay;
  private Integer sensorOrientation;

  private Detector detector;

  public String speakText;
  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private BorderedText borderedText;

  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    tracker = new MultiBoxTracker(this);

    int cropSize = TF_OD_API_INPUT_SIZE;

    try {
      detector =
          TFLiteObjectDetectionAPIModel.create(
              this,
              TF_OD_API_MODEL_FILE,
              TF_OD_API_LABELS_FILE,
              TF_OD_API_INPUT_SIZE,
              TF_OD_API_IS_QUANTIZED);
      cropSize = TF_OD_API_INPUT_SIZE;
    } catch (final IOException e) {
      e.printStackTrace();
      LOGGER.e(e, "Exception initializing Detector!");
      Toast toast =
          Toast.makeText(
              getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
      toast.show();
      finish();
    }

    textToSpeech = new TextToSpeech(this, this);
    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    sensorOrientation = rotation - getScreenOrientation();
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

    frameToCropTransform =
        ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation, MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(
        new DrawCallback() {
          @Override
          public void drawCallback(final Canvas canvas) {
            tracker.draw(canvas);
            if (isDebug()) {
              tracker.drawDebug(canvas);
            }
          }
        });

    tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
  }

  @Override
  protected void processImage() {
    ++timestamp;
    final long currTimestamp = timestamp;
    trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    Log.e("testing88","Preparing image " + currTimestamp + " for detection in bg thread.");


    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    readyForNextImage();

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(croppedBitmap);
    }

    runInBackground(
        new Runnable() {
          @Override
          public void run() {



            final long startTime = SystemClock.uptimeMillis();
            final List<Detector.Recognition> results = detector.recognizeImage(croppedBitmap);
            Log.e("testing88", "result . : " + results.toString());
            if(results.get(0).getTitle().equals("red")||results.get(0).getTitle().equals("green")){
              Log.e("voice","fㄹㄹirst is green or red");
              queue_all.offer(results.get(0).getConfidence() + "");
            }
            if(results.get(1).getTitle().equals("green")||results.get(1).getTitle().equals("red")){
              Log.e("voice","sㄹㄴㄴㄴecond is green or red");
              queue_all.offer(results.get(1).getConfidence() + "");
            }
            if(results.get(2).getTitle().equals("green")||results.get(2).getTitle().equals("red")){
              Log.e("voice","ㄴtttt is green or red");
              queue_all.offer(results.get(2).getConfidence() + "");
            }

            if (queue_all.size() > 10) {
              queue_all.poll();
            }
            String f = "";
            String s = "";
            String t = "";
            String fo = "";
            String fi = "";
            String si = "";
            String se = "";
            String ei = "";
            String ni = "";
            String te = "";

            int countall = 0;
            for (String b : queue_all) {
              countall++;
              if (countall == 1) {
                f = b;
              }
              if (countall == 2) {
                s = b;
              }
              if (countall == 3) {
                t = b;
              }
              if (countall == 4) {
                fo = b;
              }
              if (countall == 5) {
                fi = b;
              }

              if (countall == 6) {
                si = b;
              }
              if (countall == 7) {
                se = b;
              }
              if (countall == 8) {
                ei = b;
              }
              if (countall == 9) {
                ni = b;
              }
              if (countall == 10) {
                te = b;
              }
            }

            Log.e("testing88", f + "," + se + "," + t + ",,," + fo + ",,,," + fi + ",,,,,," + si + ",,,,,,," + se + ",,,,,...,," + ei + ",,,,m,m,mnm" + ni + ",,,,mn,m" + te);


            String first = "";
            String second = "";
            String third = "";
            String fourth = "";
            String fifth = "";
            if(fi.length()!=0&&f.length()!=0&&te.length()!=0){
              //모두 차 면 비움 ?
              if (Float.parseFloat(f) < MINIMUM_CONFIDENCE_TF_OD_API && Float.parseFloat(fi) < MINIMUM_CONFIDENCE_TF_OD_API && Float.parseFloat(te) < MINIMUM_CONFIDENCE_TF_OD_API) {
                //아무것도 인식이 되지 않으면 모두 비움.
                Log.e("testing88", "remove all queue__alllll");
                f = "";
                s = "";
                t = "";
                fo = "";
                fi = "";
                si = "";
                se = "";
                ei = "";
                ni = "";
                te = "";
                first="";
                second="";
                third="";
                fourth="";
                fifth="";

                queue.clear();
                queue_all.clear();

                firsttime=true;
                Log.e("testing88", "qqqque after remove" + queue.toString());
              }
            }


            if (results.get(0).getConfidence() > MINIMUM_CONFIDENCE_TF_OD_API) {

//                Log.e("voice", "1순위 : " + results.get(0).getTitle() + "/" + results.get(0).getConfidence());
//
//                Log.e("voice", "2순위 : " + results.get(1).getTitle() + "/" + results.get(1).getConfidence());
//
//
//                Log.e("voice", "3순위 : " + results.get(2).getTitle() + "/" + results.get(2).getConfidence());

              if(results.get(0).getTitle().equals("red")||results.get(0).getTitle().equals("green")){
                Log.e("voice","first is green or red");
                queue.offer(results.get(0).getTitle());
                if (queue.size() > 5) {
                  queue.poll();
                }
              }
              if(results.get(1).getTitle().equals("green")||results.get(1).getTitle().equals("red")){
                Log.e("voice","second is green or red");
                queue.offer(results.get(1).getTitle());
                if (queue.size() > 5) {
                  queue.poll();
                }
              }
              if(results.get(2).getTitle().equals("green")||results.get(2).getTitle().equals("red")){
                Log.e("voice","tttt is green or red");
                queue.offer(results.get(2).getTitle());
                if (queue.size() > 5) {
                  queue.poll();
                }
              }


              //current results.get(0).getTitle()
              //last : queue.peek()
              int count = 0;
              JSONObject jsonObject = new JSONObject();

              for (String a : queue) {
                count++;
                try {
                  jsonObject.put(count + "", a);
                } catch (JSONException e) {
                  e.printStackTrace();
                }
                Log.e("testing88", count + "a is : " + a);


                if (count == 1) {
                  first = a;
                }
                if (count == 2) {
                  second = a;
                }
                if (count == 3) {
                  third = a;
                }
                if (count == 4) {
                  fourth = a;
                }
                if (count == 5) {
                  fifth = a;
                }
              }

              Log.e("voice", first + "," + second + "," + third + ",,," + fourth + ",,,," + fifth);

              if(fifth==""){
                Log.e("testing88","wait untill fill 5 : ");
              }else{
                showCropInfo3(first + "," + second + "," + third + ",,," + fourth + ",,,," + fifth);
                if(first.equals(second)&&second.equals(third)&&third.equals(fourth)&&fourth.equals(fifth)&&fifth.equals(first)){
//                  if(firsttime){
//                    firsttime=false;
//                    String newval = "";
//                    Log.e("testing88","first time so speech is : "+fifth);
//                    if (fifth.equals("person")) {
//                      newval = "green";
//                    } else {
//                      newval = "red";
//                    }
                    Log.e("testing88", "every 5 frame same ! so current is... "+first );
                    showcurrent(first);
                }
                if (first.equals(second)) {
                  //첫번째랑 두번째가 같고
                  if (!fifth.equals(first)) {
                    //마지막이 첫번째랑 다르고
                    if (fifth.equals(fourth)) {
                      //마지막이랑 네번째랑 같고
                      if (!fifth.equals(third)) {
                        //다섯번째가 세번째랑 다르고
                        //red,red,red,green,green
                        Log.e("voice", "changed to " + fifth);
                        String newval = "";
                        if (fifth.equals("green")) {

                          Log.e("voice", "speak : " + newval);
                          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            textToSpeech.speak(fifth + "", TextToSpeech.QUEUE_FLUSH, null, null);
                          } else {
                            textToSpeech.speak(fifth + "", TextToSpeech.QUEUE_FLUSH, null);
                          }


                        }
                      }

                    }

                  }

                }
              }



            }


            Log.e("testing88", "queue is : " + queue.toString());

            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
            final Canvas canvas = new Canvas(cropCopyBitmap);
            final Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(2.0f);

            float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
            switch (MODE) {
              case TF_OD_API:
                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                break;
            }

            final List<Detector.Recognition> mappedRecognitions =
                    new ArrayList<Detector.Recognition>();

            for (final Detector.Recognition result : results) {
              final RectF location = result.getLocation();
              if (location != null && result.getConfidence() >= minimumConfidence) {
                canvas.drawRect(location, paint);

                cropToFrameTransform.mapRect(location);

                result.setLocation(location);
                mappedRecognitions.add(result);
              }
            }

            tracker.trackResults(mappedRecognitions, currTimestamp);
            trackingOverlay.postInvalidate();

            computingDetection = false;

            String newvalue = "";
            String conf = "";
//            if (.equals("person")) {
//              newvalue = "green";
//            } else if (results.get(0).getTitle().equals("bicycle")) {
//              newvalue = "red";
//            }
//            String finalNewvalue = newvalue;
            runOnUiThread(
                    new Runnable() {
                      @Override
                      public void run() {
                        showFrameInfo(results.get(0).getTitle() + ":" + results.get(0).getConfidence());
                        showCropInfo(results.get(1).getTitle() + ":" + results.get(1).getConfidence());
                        showCropInfo2(results.get(2).getTitle() + ":" + results.get(2).getConfidence());
                        showInference(lastProcessingTimeMs + "mssss");
                      }
                    });


          }
        });
  }

  @Override
  protected int getLayoutId() {
    return R.layout.tfe_od_camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  @Override
  public void onInit(int status) {
    if (status == TextToSpeech.SUCCESS) {
      int result = textToSpeech.setLanguage(Locale.US);
      String text = speakText;
      textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }
  }

  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
  // checkpoints.
  private enum DetectorMode {
    TF_OD_API;
  }

  @Override
  protected void setUseNNAPI(final boolean isChecked) {
    runInBackground(
        () -> {
          try {
            detector.setUseNNAPI(isChecked);
          } catch (UnsupportedOperationException e) {
            LOGGER.e(e, "Failed to set \"Use NNAPI\".");
            runOnUiThread(
                () -> {
                  Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
          }
        });
  }

  @Override
  protected void setNumThreads(final int numThreads) {
    runInBackground(() -> detector.setNumThreads(numThreads));
  }
}
