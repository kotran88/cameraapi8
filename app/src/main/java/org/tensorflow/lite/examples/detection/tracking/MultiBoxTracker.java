/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.lite.examples.detection.tracking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.util.TypedValue;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.tensorflow.lite.examples.detection.CameraActivity;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Detector.Recognition;

/** A tracker that handles non-max suppression and matches existing objects to new detections. */
public class MultiBoxTracker extends CameraActivity implements TextToSpeech.OnInitListener{
  private static final float TEXT_SIZE_DIP = 18;
  private static final float MIN_SIZE = 5f;

  private final Paint paintcircle = new Paint();
  ConcurrentLinkedQueue<String> queue=new ConcurrentLinkedQueue<>();

  TextToSpeech textToSpeech;
  private static final int[] COLORS = {
          Color.BLUE,
          Color.RED,
          Color.GREEN,
          Color.YELLOW,
          Color.CYAN,
          Color.MAGENTA,
          Color.WHITE,
          Color.parseColor("#55FF55"),
          Color.parseColor("#FFA500"),
          Color.parseColor("#FF8888"),
          Color.parseColor("#AAAAFF"),
          Color.parseColor("#FFFFAA"),
          Color.parseColor("#55AAAA"),
          Color.parseColor("#AA33AA"),
          Color.parseColor("#0D0068")
  };
  final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();
  private final Logger logger = new Logger();
  private final Queue<Integer> availableColors = new LinkedList<Integer>();
  private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();
  private final Paint boxPaint = new Paint();
  private final float textSizePx;
  private final BorderedText borderedText;
  private Matrix frameToCanvasMatrix;
  private int frameWidth;
  private int frameHeight;
  private int sensorOrientation;
  private float detectedarea;

  public MultiBoxTracker(final Context context) {
    for (final int color : COLORS) {
      availableColors.add(color);
    }


    boxPaint.setColor(Color.RED);
    boxPaint.setStyle(Style.STROKE);
    boxPaint.setStrokeWidth(10.0f);
    boxPaint.setStrokeCap(Cap.ROUND);
    boxPaint.setStrokeJoin(Join.ROUND);
    boxPaint.setStrokeMiter(100);

    textSizePx =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    textToSpeech = new TextToSpeech(context, this);
  }

  public synchronized void setFrameConfiguration(
          final int width, final int height, final int sensorOrientation) {
    frameWidth = width;
    frameHeight = height;
    this.sensorOrientation = sensorOrientation;
  }

  public synchronized void drawDebug(final Canvas canvas) {
    final Paint textPaint = new Paint();
    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(60.0f);

    final Paint boxPaint = new Paint();
    boxPaint.setColor(Color.RED);
    boxPaint.setAlpha(200);
    boxPaint.setStyle(Style.STROKE);


    paintcircle.setColor(Color.BLUE);
    paintcircle.setAntiAlias(true);
    paintcircle.setStrokeWidth(5);
    paintcircle.setStyle(Paint.Style.FILL_AND_STROKE);
    paintcircle.setStrokeJoin(Paint.Join.ROUND);
    paintcircle.setStrokeCap(Paint.Cap.ROUND);

    for (final Pair<Float, RectF> detection : screenRects) {
      final RectF rect = detection.second;
      canvas.drawRect(rect, boxPaint);
      canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
      borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
    }
  }

  public synchronized void trackResults(final List<Recognition> results, final long timestamp) {
    logger.i("Processing %d results from %d", results.size(), timestamp);
    processResults(results);
  }

  private Matrix getFrameToCanvasMatrix() {
    return frameToCanvasMatrix;
  }


  public synchronized void draw(final Canvas canvas) {
    final boolean rotated = sensorOrientation % 180 == 90;
    final float multiplier =
            Math.min(
                    canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                    canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
    frameToCanvasMatrix =
            ImageUtils.getTransformationMatrix(
                    frameWidth,
                    frameHeight,
                    (int) (multiplier * (rotated ? frameHeight : frameWidth)),
                    (int) (multiplier * (rotated ? frameWidth : frameHeight)),
                    sensorOrientation,
                    false);
    int countingtrack=0;
    for (final TrackedRecognition recognition : trackedObjects) {
      final RectF trackedPos = new RectF(recognition.location);

      countingtrack++;

      getFrameToCanvasMatrix().mapRect(trackedPos);
      boxPaint.setColor(recognition.color);

      float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
      canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);

      if(recognition.title.equals("car")){

        Log.e("car",trackedPos.left+trackedPos.width()/2+"is center left");
        Log.e("car",trackedPos.top+trackedPos.height()/2+"is center top");
        canvas.drawCircle(trackedPos.left+trackedPos.width()/2,trackedPos.top+trackedPos.height()/2,5.0f,paintcircle);
        String first="";
        String second="";
        String third="";
        //차가 디텍트 되고  크기가 60000이상일경우에 띵 울리면서 차 인식 시작.
        if(trackedPos.width()*trackedPos.height()>60000){


          detectedarea=trackedPos.width()*trackedPos.height();
          Log.e("car",countingtrack+" car is "+recognition.title+",,,"+trackedPos.width()*trackedPos.width());
          int countall = 0;
          if (queue.size() > 3) {
            //if queue size is over 10  , remove all.
            queue.poll();
            countall=0;

          }

          Log.e("car","detecting car in queue is : "+queue.toString());

          for (String b : queue) {
            countall++;
            if(countall==1){
              first=b;
            }
            if(countall==2){
              second=b;
            }
            if(countall==3){
              third=b;
            }
          }



          queue.add(trackedPos.width()*trackedPos.height()+"");

        }else{
          Log.e("car",detectedarea*0.7+"current area : "+Double.parseDouble(""+trackedPos.width()*trackedPos.height()));
          if(Double.parseDouble(""+trackedPos.width()*trackedPos.height())<60000){

            Log.e("car","car move !");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
              textToSpeech.speak("car move", TextToSpeech.QUEUE_FLUSH, null, null);
            } else {
              textToSpeech.speak("car move", TextToSpeech.QUEUE_FLUSH, null);
            }
          }
//          if(first!=""){
//            Log.e("car","first area : "+(first));
//          }
//          if(second!=""){
//
//            Log.e("car","second area : "+Double.parseDouble(second));
//            if(Double.parseDouble(""+trackedPos.width()*trackedPos.height()) < Double.parseDouble(second)*0.8){
//              Log.e("car","car move start!!!");
//
//            }
//
//          }
//          if(third!=""){
//
//            Log.e("car","third area : "+Double.parseDouble(third));
//          }

        }


      }
      final String labelString =
              !TextUtils.isEmpty(recognition.title)
                      ? String.format("%s %.2f", recognition.title, (100 * recognition.detectionConfidence))
                      : String.format("%.2f", (100 * recognition.detectionConfidence));
      Log.e("draw",labelString);
      //            borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.top,
      // labelString);

//      String newval="";
//      if(recognition.title.equals("person")){
//        newval="green";
//      }else{
//        newval="red";
//      }

      borderedText.drawText(
              canvas, trackedPos.left + cornerSize, trackedPos.top, labelString + "/"+trackedPos.width()*trackedPos.height(), boxPaint);
    }
  }

  private void processResults(final List<Recognition> results) {
    final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

    screenRects.clear();
    final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

    for (final Recognition result : results) {
      if (result.getLocation() == null) {
        continue;
      }
      final RectF detectionFrameRect = new RectF(result.getLocation());

      final RectF detectionScreenRect = new RectF();
      rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

      Log.e("testing88",
              detectionFrameRect.width()+",,,,"+detectionFrameRect.height()+"Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

      screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

      if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
        Log.e("testing88","Degenerate rectangle! " + detectionFrameRect);
        continue;
      }

      Log.e("testing88",result.getConfidence()+",,,,"+result);
      rectsToTrack.add(new Pair<Float, Recognition>(result.getConfidence(), result));
    }

    trackedObjects.clear();
    if (rectsToTrack.isEmpty()) {
      Log.e("testing88","Nothing to track, aborting.");
      return;
    }

    for (final Pair<Float, Recognition> potential : rectsToTrack) {
      final TrackedRecognition trackedRecognition = new TrackedRecognition();
      trackedRecognition.detectionConfidence = potential.first;
      trackedRecognition.location = new RectF(potential.second.getLocation());
      trackedRecognition.title = potential.second.getTitle();
      trackedRecognition.color = COLORS[trackedObjects.size()];
      trackedObjects.add(trackedRecognition);

      if (trackedObjects.size() >= COLORS.length) {
        break;
      }
    }
  }

  @Override
  protected void processImage() {

  }

  @Override
  protected void onPreviewSizeChosen(Size size, int rotation) {

  }

  @Override
  protected int getLayoutId() {
    return 0;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return null;
  }

  @Override
  protected void setNumThreads(int numThreads) {

  }

  @Override
  protected void setUseNNAPI(boolean isChecked) {

  }

  @Override
  public void onInit(int status) {
    if (status == TextToSpeech.SUCCESS) {
      int result = textToSpeech.setLanguage(Locale.US);
      String text = "test";
      textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }
  }

  private static class TrackedRecognition {
    RectF location;
    float detectionConfidence;
    int color;
    String title;
  }
}
