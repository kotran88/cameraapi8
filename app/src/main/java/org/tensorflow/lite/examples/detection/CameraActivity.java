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

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;

public abstract class CameraActivity extends AppCompatActivity
        implements OnImageAvailableListener,
        Camera.PreviewCallback,
        CompoundButton.OnCheckedChangeListener,
        View.OnClickListener {
  private static final Logger LOGGER = new Logger();

  View mView;
  private static final int PERMISSIONS_REQUEST = 1;

  private CameraDevice cameraDevice;
   CameraCharacteristics characteristics;
  public static Bitmap mBitmap;
  public static Camera.Parameters param;
  public static Camera.Size mPreviewSize;
  public static byte[] byteArray;

  CameraConnectionFragment camera2Fragment;
  CameraManager manager;
  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;

  private static final String PERMISSION_W = Manifest.permission.WRITE_EXTERNAL_STORAGE;
  private static final String PERMISSION_R = Manifest.permission.READ_EXTERNAL_STORAGE;

  private static final String PERMISSION_I = Manifest.permission.INTERNET;
  protected int previewWidth = 0;
  protected int previewHeight = 0;
  private boolean debug = false;
  private Handler handler;
  private HandlerThread handlerThread;
  private boolean useCamera2API;
  private boolean isProcessingFrame = false;
  private byte[][] yuvBytes = new byte[3][];
  private int[] rgbBytes = null;
  private int yRowStride;
  private Runnable postInferenceCallback;
  private Runnable imageConverter;
  final int PERMISSION = 1;

  private LinearLayout bottomSheetLayout;
  private LinearLayout gestureLayout;
  private BottomSheetBehavior<LinearLayout> sheetBehavior;

  protected TextView frameValueTextView, cropValueTextView,cropValueTextView2,cropValueTextView3,cropValueTextView4, inferenceTimeTextView;
  protected ImageView bottomSheetArrowImageView;
  private ImageView plusImageView, minusImageView;
  private SwitchCompat apiSwitchCompat;
  private TextView threadsTextView;
  private SpeechRecognizer mRecognizer;
  private void openScreenshot(File imageFile) {
    Log.e("testing88","open screenshow"+imageFile.toString());
    Intent intent = new Intent();
    intent.setAction(Intent.ACTION_SEND);
    Uri uri = Uri.fromFile(imageFile);
    intent.setDataAndType(uri, "image/*");
    startActivity(intent);
  }


  public static void savePic(Bitmap b, String strFileName) {
    FileOutputStream fos;
    try {
      Log.e("testing88",strFileName);
      fos = new FileOutputStream(strFileName);
      b.compress(Bitmap.CompressFormat.PNG, 90, fos);
      fos.flush();
      fos.close();
    } catch (IOException e) {
      Log.e("testing88","savepic error"+e.toString());
      e.printStackTrace();
    }
  }

  private RecognitionListener listener = new RecognitionListener() {
    @Override public void onReadyForSpeech(Bundle params) {
      Toast.makeText(getApplicationContext(),"음성인식을 시작합니다.",Toast.LENGTH_SHORT).show();
    }
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onError(int error) { String message;
      switch (error) {
        case SpeechRecognizer.ERROR_AUDIO: message = "오디오 에러"; break;
        case SpeechRecognizer.ERROR_CLIENT: message = "클라이언트 에러"; break;
        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "퍼미션 없음"; break;
        case SpeechRecognizer.ERROR_NETWORK: message = "네트워크 에러"; break;
        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "네트웍 타임아웃"; break;
        case SpeechRecognizer.ERROR_NO_MATCH: message = "찾을 수 없음"; break;
        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "RECOGNIZER가 바쁨"; break;
        case SpeechRecognizer.ERROR_SERVER: message = "서버가 이상함"; break;
        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "말하는 시간초과"; break;
        default: message = "알 수 없는 오류임"; break; }
      Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName());
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");
      mRecognizer= SpeechRecognizer.createSpeechRecognizer(CameraActivity.this);
      mRecognizer.setRecognitionListener(listener);
      mRecognizer.startListening(intent);
    }
        @Override public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

          Toast.makeText(getApplicationContext(), "인식  : " + matches.get(0),Toast.LENGTH_SHORT).show();
              for(int i = 0; i < matches.size() ; i++){

                Log.e("audio","matches.get(i)"+matches.get(i) );
              }
              if(matches.get(0).equals("오디오")){
                Log.e("audio","gogo capture");

              }
          Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
          intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName());
          intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");
          mRecognizer= SpeechRecognizer.createSpeechRecognizer(CameraActivity.this);
          mRecognizer.setRecognitionListener(listener);
          mRecognizer.startListening(intent);
    }
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {} };


  private void checkPermission() {

    Dexter.withActivity(this).withPermissions(Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .withListener(new MultiplePermissionsListener() {
              @Override
              public void onPermissionsChecked(MultiplePermissionsReport report) {
                // check if all permissions are granted or not
                if (report.areAllPermissionsGranted()) {

                }
                // check for permanent denial of any permission show alert dialog
                if (report.isAnyPermissionPermanentlyDenied()) {
                  // open Settings activity
                }
              }


              @Override
              public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                token.continuePermissionRequest();
              }
            }).withErrorListener(error -> Toast.makeText(this.getApplicationContext(), "Error occurred! ", Toast.LENGTH_SHORT).show())
            .onSameThread()
            .check();
  }
  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    LOGGER.d("onCreate " + this);
    super.onCreate(null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


    
    // 버튼을 클릭 이벤트 - 객체에 Context와 listener를 할당한 후 실행
    // sttBtn.setOnClickListener(v -> { mRecognizer=SpeechRecognizer.createSpeechRecognizer(this); mRecognizer.setRecognitionListener(listener); mRecognizer.startListening(intent); });

    setContentView(R.layout.tfe_od_activity_camera);

    checkPermission();
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayShowTitleEnabled(false);

    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }

    threadsTextView = findViewById(R.id.threads);
    plusImageView = findViewById(R.id.plus);
    minusImageView = findViewById(R.id.minus);
    apiSwitchCompat = findViewById(R.id.api_info_switch);
    bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);
    View content = findViewById(R.id.bottom_sheet_layout);

    Log.e("audio","gggg");

//    sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);

//    ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
//    vto.addOnGlobalLayoutListener(
//            new ViewTreeObserver.OnGlobalLayoutListener() {
//              @Override
//              public void onGlobalLayout() {
//                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
//                  gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
//                } else {
//                  gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
//                }
//                //                int width = bottomSheetLayout.getMeasuredWidth();
//                int height = gestureLayout.getMeasuredHeight();
//
//                sheetBehavior.setPeekHeight(height);
//              }
//            });
//    sheetBehavior.setHideable(false);

//    sheetBehavior.setBottomSheetCallback(
//            new BottomSheetBehavior.BottomSheetCallback() {
//              @Override
//              public void onStateChanged(@NonNull View bottomSheet, int newState) {
//                switch (newState) {
//                  case BottomSheetBehavior.STATE_HIDDEN:
//                    break;
//                  case BottomSheetBehavior.STATE_EXPANDED:
//                  {
//                    bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);
//                  }
//                  break;
//                  case BottomSheetBehavior.STATE_COLLAPSED:
//                  {
//                    bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
//                  }
//                  break;
//                  case BottomSheetBehavior.STATE_DRAGGING:
//                    break;
//                  case BottomSheetBehavior.STATE_SETTLING:
//                    bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
//                    break;
//                }
//              }
//
//              @Override
//              public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
//            });

    frameValueTextView = findViewById(R.id.frame_info);
    cropValueTextView = findViewById(R.id.crop_info);
    cropValueTextView2 = findViewById(R.id.crop_info2);
    cropValueTextView3 = findViewById(R.id.crop_info3);
    cropValueTextView4 = findViewById(R.id.crop_info4);
    inferenceTimeTextView = findViewById(R.id.inference_info);

    apiSwitchCompat.setOnCheckedChangeListener(this);

    plusImageView.setOnClickListener(this);
    minusImageView.setOnClickListener(this);
//    takeScreenShot(this);
//    ActivityCompat.requestPermissions(this, new String[]{
//            Manifest.permission.INTERNET, Manifest.permission.RECORD_AUDIO},PERMISSION);
//    if ( Build.VERSION.SDK_INT >= 23 ){
//      }
    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName());
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");
//    mRecognizer= SpeechRecognizer.createSpeechRecognizer(this);
//    mRecognizer.setRecognitionListener(listener);
//    mRecognizer.startListening(intent);


    FloatingActionButton fab = findViewById(R.id.fab);
    fab.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {

        camera2Fragment.capturestaring(CameraActivity.this);
        Snackbar.make(view, "현재 후면 카메라 사진이 전송되었습니다.", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();

      }
    });

    FloatingActionButton fab_cam = findViewById(R.id.fab_cam);
    fab_cam.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {

        try {
          camera2Fragment.videostaring(CameraActivity.this);
        } catch (IOException e) {
          e.printStackTrace();
        } catch (CameraAccessException e) {
          e.printStackTrace();
        }
        Snackbar.make(view, "비디오 블랙박스 촬영 시작 ", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();

      }
    });

//
//    new Handler().postDelayed(new Runnable() {
//      @Override
//      public void run() {
//
//
//      }
//    }, 3000);

  }

  protected int[] getRgbBytes() {
    imageConverter.run();
    return rgbBytes;
  }

  protected int getLuminanceStride() {
    return yRowStride;
  }

  protected byte[] getLuminance() {
    return yuvBytes[0];
  }

  /** Callback for android.hardware.Camera API */
  @Override
  public void onPreviewFrame(final byte[] bytes, final Camera camera) {
    Log.e("testing88","onpreview frame");
    if (isProcessingFrame) {
      LOGGER.w("Dropping frame!");
      return;
    }

    try {
      // Initialize the storage bitmaps once when the resolution is known.
      if (rgbBytes == null) {
        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        previewHeight = previewSize.height;
        previewWidth = previewSize.width;
        rgbBytes = new int[previewWidth * previewHeight];
        onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
      }
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      return;
    }

    isProcessingFrame = true;
    yuvBytes[0] = bytes;
    yRowStride = previewWidth;

    imageConverter =
            new Runnable() {
              @Override
              public void run() {
                ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
              }
            };

    postInferenceCallback =
            new Runnable() {
              @Override
              public void run() {
                camera.addCallbackBuffer(bytes);
                isProcessingFrame = false;
              }
            };
    processImage();
  }

  /** Callback for Camera2 API */
  @Override
  public void onImageAvailable(final ImageReader reader) {
    Log.e("testing88","onImageAvailable");
    // We need wait until we have some size from onPreviewSizeChosen
    if (previewWidth == 0 || previewHeight == 0) {
      return;
    }
    if (rgbBytes == null) {
      rgbBytes = new int[previewWidth * previewHeight];
    }
    try {
      final Image image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

      if (isProcessingFrame) {
        image.close();
        return;
      }
      isProcessingFrame = true;
      Trace.beginSection("imageAvailable");
      final Plane[] planes = image.getPlanes();
      fillBytes(planes, yuvBytes);
      yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();

      imageConverter =
              new Runnable() {
                @Override
                public void run() {
                  ImageUtils.convertYUV420ToARGB8888(
                          yuvBytes[0],
                          yuvBytes[1],
                          yuvBytes[2],
                          previewWidth,
                          previewHeight,
                          yRowStride,
                          uvRowStride,
                          uvPixelStride,
                          rgbBytes);
                }
              };

      postInferenceCallback =
              new Runnable() {
                @Override
                public void run() {
                  image.close();
                  isProcessingFrame = false;
                }
              };

      processImage();
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }
    Trace.endSection();
  }

  @Override
  public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    super.onStart();
  }

  @Override
  public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();

    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

    super.onPause();
  }

  @Override
  public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    super.onDestroy();
  }

  protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
  }

  @Override
  public void onRequestPermissionsResult(
          final int requestCode, final String[] permissions, final int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERMISSIONS_REQUEST) {
      if (allPermissionsGranted(grantResults)) {
        setFragment();
      } else {
        requestPermission();
      }
    }
  }

  private static boolean allPermissionsGranted(final int[] grantResults) {
    for (int result : grantResults) {
      if (result != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(PERMISSION_W) == PackageManager.PERMISSION_GRANTED&& checkSelfPermission(PERMISSION_R) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
        Toast.makeText(
                CameraActivity.this,
                "Camera permission is required for this demo",
                Toast.LENGTH_LONG)
                .show();
      }
      requestPermissions(new String[] {PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
    }
  }

  // Returns true if the device supports the required hardware level, or better.
  private boolean isHardwareLevelSupported(
          CameraCharacteristics characteristics, int requiredLevel) {
    int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
    if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
      return requiredLevel == deviceLevel;
    }
    // deviceLevel is not LEGACY, can use numerical sort
    return requiredLevel <= deviceLevel;
  }


  private String chooseCamera() {
      manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try {
      for (final String cameraId : manager.getCameraIdList()) {
         characteristics = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        final StreamConfigurationMap map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
          continue;
        }

        // Fallback to camera1 API for internal cameras that don't have full support.
        // This should help with legacy situations where using the camera2 API causes
        // distorted or otherwise broken previews.
        useCamera2API =
                (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                        || isHardwareLevelSupported(
                        characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
        Log.e("testing88","Camera API lv2?: "+ useCamera2API);
        return cameraId;
      }
    } catch (CameraAccessException e) {
      LOGGER.e(e, "Not allowed to access camera");
    }

    return null;
  }

  protected void setFragment() {
    String cameraId = chooseCamera();

    Fragment fragment;
    if (useCamera2API) {
       camera2Fragment =
              CameraConnectionFragment.newInstance(
                      new CameraConnectionFragment.ConnectionCallback() {
                        @Override
                        public void onPreviewSizeChosen(final Size size, final int rotation) {
                          previewHeight = size.getHeight();
                          previewWidth = size.getWidth();
                          CameraActivity.this.onPreviewSizeChosen(size, rotation);
                        }
                      },
                      this,
                      getLayoutId(),
                      getDesiredPreviewFrameSize());


      camera2Fragment.showToast("testing...");
      camera2Fragment.setCamera(cameraId);

      fragment = camera2Fragment;
    } else {
      fragment =
              new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
    }

    getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  public boolean isDebug() {
    return debug;
  }

  protected void readyForNextImage() {
    if (postInferenceCallback != null) {
      postInferenceCallback.run();
    }
  }

  protected int getScreenOrientation() {
    switch (getWindowManager().getDefaultDisplay().getRotation()) {
      case Surface.ROTATION_270:
        return 270;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_90:
        return 90;
      default:
        return 0;
    }
  }

  @Override
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    setUseNNAPI(isChecked);
    if (isChecked) apiSwitchCompat.setText("NNAPI");
    else apiSwitchCompat.setText("TFLITE");
  }

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.plus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      if (numThreads >= 9) return;
      numThreads++;
      threadsTextView.setText(String.valueOf(numThreads));
      setNumThreads(numThreads);
    } else if (v.getId() == R.id.minus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      if (numThreads == 1) {
        return;
      }
      numThreads--;
      threadsTextView.setText(String.valueOf(numThreads));
      setNumThreads(numThreads);
    }
  }

  protected void showFrameInfo_withColor(String frameInfo) {
    frameValueTextView.setTextSize(getResources().getDimension(R.dimen.textsize));
    frameValueTextView.setText(frameInfo);
  }
  protected void showFrameInfo(String frameInfo) {
    Log.e("testing88","show frame...."+frameValueTextView);
    if(frameValueTextView!=null){

      frameValueTextView.setText(frameInfo);
      frameValueTextView.setTextSize(getResources().getDimension(R.dimen.textsize_normal));
    }
  }

  protected void showCropInfo(String cropInfo) {
    cropValueTextView.setText(cropInfo);
  }
  protected void showCropInfo2(String cropInfo) {
    cropValueTextView2.setText(cropInfo);
  }
  protected void showcurrent(String cropInfo) {
    cropValueTextView4.setText("current : "+cropInfo);
  }
  protected void showCropInfo3(String cropInfo) {
    cropValueTextView3.setText(cropInfo);
  }

  public Bitmap getBitmap() {
    try {
      if (param == null)
        return null;

      if (mPreviewSize == null)
        return null;

      int format = param.getPreviewFormat();
      YuvImage yuvImage = new YuvImage(byteArray, format, previewWidth, previewHeight, null);
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

      Log.e("audio","array: "+byteArray.toString());



      Rect rect = new Rect(0, 0, previewWidth, previewHeight);

      yuvImage.compressToJpeg(rect, 75, byteArrayOutputStream);
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inPurgeable = true;
      options.inInputShareable = true;
      mBitmap = BitmapFactory.decodeByteArray(byteArrayOutputStream.toByteArray(), 0, byteArrayOutputStream.size(), options);

      byteArrayOutputStream.flush();
      byteArrayOutputStream.close();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }

    return mBitmap;
  }

  protected void showInference(String inferenceTime) {

//    Bitmap screen = Bitmap.createBitmap(getBitmap());

//    shareView(screen);
    inferenceTimeTextView.setText(inferenceTime);
  }

  protected abstract void processImage();

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

  protected abstract int getLayoutId();

  protected abstract Size getDesiredPreviewFrameSize();

  protected abstract void setNumThreads(int numThreads);

  protected abstract void setUseNNAPI(boolean isChecked);
}
