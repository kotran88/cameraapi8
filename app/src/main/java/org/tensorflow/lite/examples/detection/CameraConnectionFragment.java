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
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.tensorflow.lite.examples.detection.customview.AutoFitTextureView;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.listeners.PictureCapturingListener;
import org.tensorflow.lite.examples.detection.services.APictureCapturingService;
import org.tensorflow.lite.examples.detection.services.PictureCapturingServiceImpl;



@SuppressLint("ValidFragment")
public class CameraConnectionFragment extends Fragment implements PictureCapturingListener {
  private static final Logger LOGGER = new Logger();

  private String mOutputFilePath;


  public boolean mIsRecordingVideo;
  private Integer mSensorOrientation;
  private File mCurrentFile;

  private HandlerThread mBackgroundThread;
  private static final String VIDEO_DIRECTORY_NAME = "AndroidWave";
  private MediaRecorder mMediaRecorder;
  /**
   * The camera preview size will be chosen to be the smallest frame by pixel size capable of
   * containing a DESIRED_SIZE x DESIRED_SIZE square.
   */


  private Handler mBackgroundHandler;
  private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
  private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;

  private static final String TAG = "cacaca";
  private static final int MINIMUM_PREVIEW_SIZE = 640;
   CameraCharacteristics characteristics;
  /** Conversion from screen rotation to JPEG orientation. */
  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
   CameraManager manager;
  private static final String FRAGMENT_DIALOG = "dialog";

  private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
  private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
  private TreeMap<String, byte[]> picturesTaken;
  private PictureCapturingListener capturingListener;
  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  static {
    INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
    INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
    INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
  }

  static {
    DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
    DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
    DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
  }
  /** A {@link Semaphore} to prevent the app from exiting before closing the camera. */
  private final Semaphore cameraOpenCloseLock = new Semaphore(1);
  /** A {@link OnImageAvailableListener} to receive frames as they are available. */
  private final OnImageAvailableListener imageListener;
  /** The input size in pixels desired by TensorFlow (width and height of a square bitmap). */
  private final Size inputSize;
  /** The layout identifier to inflate for this Fragment. */
  private final int layout;

  private final ConnectionCallback cameraConnectionCallback;
  private final CameraCaptureSession.CaptureCallback captureCallback =
      new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(
            final CameraCaptureSession session,
            final CaptureRequest request,
            final CaptureResult partialResult) {}

        @Override
        public void onCaptureCompleted(
            final CameraCaptureSession session,
            final CaptureRequest request,
            final TotalCaptureResult result) {}
      };
  /** ID of the current {@link CameraDevice}. */
  private String cameraId;
  /** An {@link AutoFitTextureView} for camera preview. */
  private AutoFitTextureView textureView;
  /** A {@link CameraCaptureSession } for camera preview. */
  private CameraCaptureSession captureSession;
  /** A reference to the opened {@link CameraDevice}. */
  private CameraDevice cameraDevice;
  /** The rotation in degrees of the camera sensor from the display. */
  private Integer sensorOrientation;
  /** The {@link Size} of camera preview. */
  private Size previewSize;
  /** An additional thread for running tasks that shouldn't block the UI. */
  private HandlerThread backgroundThread;
  /** A {@link Handler} for running tasks in the background. */
  private Handler backgroundHandler;
  /** An {@link ImageReader} that handles preview frame capture. */
  private ImageReader previewReader;
  /** {@link CaptureRequest.Builder} for the camera preview */
  private CaptureRequest.Builder previewRequestBuilder;
  /** {@link CaptureRequest} generated by {@link #previewRequestBuilder} */
  private CaptureRequest previewRequest;
  /** {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state. */
  private final CameraDevice.StateCallback stateCallback =
      new CameraDevice.StateCallback() {
        @Override
        public void onOpened(final CameraDevice cd) {
          // This method is called when the camera is opened.  We start camera preview here.
          cameraOpenCloseLock.release();
          cameraDevice = cd;
          createCameraPreviewSession();




        }

        @Override
        public void onDisconnected(final CameraDevice cd) {
          cameraOpenCloseLock.release();
          cd.close();
          cameraDevice = null;
        }

        @Override
        public void onError(final CameraDevice cd, final int error) {
          cameraOpenCloseLock.release();
          cd.close();
          cameraDevice = null;
          final Activity activity = getActivity();
          if (null != activity) {
            activity.finish();
          }
        }
      };



  private void startBackgroundThread2() {
    Log.e(TAG,"startBackgroundThread2");
    mBackgroundThread = new HandlerThread("CameraBackground");
    mBackgroundThread.start();
    mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
  }
  private void stopBackgroundThread2() {
    mBackgroundThread.quitSafely();
    try {
      mBackgroundThread.join();
      mBackgroundThread = null;
      mBackgroundHandler = null;
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
  private void setUpMediaRecorder() throws IOException {
    final Activity activity = getActivity();
    if (null == activity) {
      return;
    }
    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
    mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
    /**
     * create video output file
     */
    mCurrentFile = getOutputMediaFile();
    /**
     * set output file in media recorder
     */
    mMediaRecorder.setOutputFile(mCurrentFile.getAbsolutePath());
    CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
    mMediaRecorder.setVideoFrameRate(profile.videoFrameRate);
    mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
    mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
    mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
    mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
    mMediaRecorder.setAudioEncodingBitRate(profile.audioBitRate);
    mMediaRecorder.setAudioSamplingRate(profile.audioSampleRate);

    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    switch (mSensorOrientation) {
      case SENSOR_ORIENTATION_DEFAULT_DEGREES:
        mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
        break;
      case SENSOR_ORIENTATION_INVERSE_DEGREES:
        mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
        break;
    }
    mMediaRecorder.prepare();
  }

  private final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
    @Override
    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                   @NonNull TotalCaptureResult result) {
      super.onCaptureCompleted(session, request, result);
      Log.e(TAG,"capturelistener oncapture completed"+picturesTaken.toString());
      Log.e(TAG,picturesTaken.lastEntry()+"");
      if (picturesTaken.lastEntry() != null) {
        Log.e(TAG,"last entry not null");
        try{
          capturingListener.onCaptureDone(picturesTaken.lastEntry().getKey(), picturesTaken.lastEntry().getValue());
          Log.e(TAG, "done taking picture from camera " + cameraDevice.getId());
          closeCamera();
        }catch(Exception e){
          Log.e(TAG,"e is : "+e.toString() );

          cameraOpenCloseLock.release();
          createCameraPreviewSession();


          picturesTaken = new TreeMap<>();
//          Log.e(TAG, "Taking picture from camera " );
//          //Take the picture after some delay. It may resolve getting a black dark photos.
//          new Handler().postDelayed(() -> {
//            try {
//              takePicture();
//            } catch (CameraAccessException ee) {
//              ee.printStackTrace();
//            }
//          }, 60000);
        }

      }else{

        cameraOpenCloseLock.release();
        createCameraPreviewSession();


        picturesTaken = new TreeMap<>();
        Log.e(TAG, "null so Taking picture from camera " );
        //Take the picture after some delay. It may resolve getting a black dark photos.
        new Handler().postDelayed(() -> {
          try {
            takePicture();
          } catch (CameraAccessException e) {
            e.printStackTrace();
          }
        }, 100);
      }

    }

    @Override
    public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
      super.onCaptureStarted(session, request, timestamp, frameNumber);
      Log.e(TAG,"onCaptureStarted");
    }

    @Override
    public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
      super.onCaptureProgressed(session, request, partialResult);
      Log.e(TAG,"pregress");
    }

    @Override
    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
      super.onCaptureFailed(session, request, failure);
      Log.e(TAG,"failed");
    }


    @Override
    public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
      super.onCaptureSequenceAborted(session, sequenceId);
    }

    @Override
    public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
      super.onCaptureBufferLost(session, request, target, frameNumber);
    }
  };
  private APictureCapturingService pictureService;

  private void saveImageToDisk(final byte[] bytes) {
    Log.e(TAG,"saveImage to disk");
    Calendar calendar = Calendar.getInstance();

    SimpleDateFormat sdf = new SimpleDateFormat("MMMMdd:HH:mm:ss");
    Date d=new Date();

    final String cameraId = this.cameraDevice == null ? UUID.randomUUID().toString() : this.cameraDevice.getId();
    final File file = new File(Environment.getExternalStorageDirectory() + "/" +  sdf.format(d) + ".jpg");
    Log.e(TAG,file.toString());
    try (final OutputStream output = new FileOutputStream(file)) {
      output.write(bytes);
      this.picturesTaken.put(file.getPath(), bytes);
    } catch (final IOException e) {
      Log.e("testing88","Exception occurred while saving picture to external storage ", e);
    }
  }

  private final ImageReader.OnImageAvailableListener onImageAvailableListener = (ImageReader imReader) -> {
    Log.e(TAG,"onImageAvailable");
    final Image image = imReader.acquireLatestImage();
    final ByteBuffer buffer = image.getPlanes()[0].getBuffer();
    final byte[] bytes = new byte[buffer.capacity()];
    buffer.get(bytes);
    saveImageToDisk(bytes);
    image.close();
  };
  private final ImageReader.OnImageAvailableListener onImageAvailableListener2 = (ImageReader imReader) -> {
    Log.e(TAG,"onImageAvailable");
    final Image image = imReader.acquireLatestImage();
    final ByteBuffer buffer = image.getPlanes()[0].getBuffer();
    final byte[] bytes = new byte[buffer.capacity()];
    buffer.get(bytes);
    image.close();
  };


  public void stopRecordingVideo() throws Exception {
    // UI
    mIsRecordingVideo = false;
    Log.e(TAG,"stop come");
    try {
      captureSession.stopRepeating();
      captureSession.abortCaptures();
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }

    Log.e(TAG,"stop come222");
    // Stop recording
    mMediaRecorder.stop();
    mMediaRecorder.reset();

    createCameraPreviewSession();
    Log.e(TAG,"stop come333");
  }
//  private void takevideoPicture2() throws CameraAccessException, IOException {
//    if (null == cameraDevice) {
//      Log.e(TAG, "cameraDevice is null");
//      return;
//    }
//
//
//    characteristics = manager.getCameraCharacteristics(cameraId);
////     characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
//    Size[] jpegSizes = null;
//    StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//    if (streamConfigurationMap != null) {
//      jpegSizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
//    }
//    final boolean jpegSizesNotEmpty = jpegSizes != null && 0 < jpegSizes.length;
//    int width = jpegSizesNotEmpty ? jpegSizes[0].getWidth() : 640;
//    int height = jpegSizesNotEmpty ? jpegSizes[0].getHeight() : 480;
//    final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
//    final List<Surface> outputSurfaces = new ArrayList<>();
//    outputSurfaces.add(reader.getSurface());
//    previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
//    previewRequestBuilder.addTarget(reader.getSurface());
//    previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
//    previewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);
//    Log.e(TAG,"go to onimageavail");
//
//    Surface recorderSurface = mMediaRecorder.getSurface();
//    outputSurfaces.add(recorderSurface);
//    previewRequestBuilder.addTarget(recorderSurface);
//    cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
//
//      @Override
//      public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
//        Log.e(TAG,"video on configured started!!!!!!!!!!!!!!!!!!");
//        captureSession = cameraCaptureSession;
////
////
////        updatePreview();
////        createCameraPreviewvideoSession();
////        getActivity().runOnUiThread(() -> {
////          mIsRecordingVideo = true;
////          // Start recording
////          mMediaRecorder.start();
////        });
//      }
//
//      @Override
//      public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
//        Log.e(TAG, "onConfigureFailed: Failed");
//      }
//    }, mBackgroundHandler);
////    cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
////              @Override
////              public void onConfigured(@NonNull CameraCaptureSession session) {
////                try {
////                  Log.e(TAG,"onconfigured");
////
////                  session.capture(captureBuilder.build(), captureListener, null);
////                } catch (final CameraAccessException e) {
////                  Log.e(TAG, " exception occurred while accessing " , e);
////                }
////              }
////
////              @Override
////              public void onConfigureFailed(@NonNull CameraCaptureSession session) {
////              }
////            }
////            , null);
//  }
  private void takevideoPicture() throws CameraAccessException, IOException {
    if (null == cameraDevice) {
      Log.e(TAG, "cameraDevice is null");
      return;
    }
    closePreviewSession();
    setUpMediaRecorder();
    final SurfaceTexture texture = textureView.getSurfaceTexture();
    assert texture != null;
    texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
    previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
    List<Surface> surfaces = new ArrayList<>();
    // This is the output Surface we need to start preview.
    final Surface surface = new Surface(texture);
//     characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
//    Size[] jpegSizes = null;
//    StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//    if (streamConfigurationMap != null) {
//      jpegSizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
//    }
//    final boolean jpegSizesNotEmpty = jpegSizes != null && 0 < jpegSizes.length;
//    int width = jpegSizesNotEmpty ? jpegSizes[0].getWidth() : 640;
//    int height = jpegSizesNotEmpty ? jpegSizes[0].getHeight() : 480;
////    final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
////    reader.setOnImageAvailableListener(onImageAvailableListener2, null);
////    outputSurfaces.add(reader.getSurface());
//    final List<Surface> outputSurfaces = new ArrayList<>();
//
//    Log.e(TAG,"gogogoggogoogogo");
//    Log.e(TAG,previewRequestBuilder.toString());
//

    Log.e(TAG,"gogogoggogoogogo");
    Log.e(TAG,previewRequestBuilder.toString());
//
//    surfaces.add(surface);
//    previewRequestBuilder.addTarget(surface);
//
//    Surface recorderSurface = mMediaRecorder.getSurface();
//    surfaces.add(recorderSurface);
//    previewRequestBuilder.addTarget(recorderSurface);
//
//    cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

    Surface previewSurface = new Surface(texture);
    surfaces.add(previewSurface);
    previewRequestBuilder.addTarget(previewSurface);

    //MediaRecorder setup for surface
    Surface recorderSurface = mMediaRecorder.getSurface();
    surfaces.add(recorderSurface);
    previewRequestBuilder.addTarget(recorderSurface);

    // Start a capture session
    cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
      @Override
      public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
        Log.e(TAG,"video on configured started!!!!!!!!!!!!!!!!!!");
        captureSession = cameraCaptureSession;
//
//
        updatePreview();
//        createCameraPreviewvideoSession();
        getActivity().runOnUiThread(() -> {
          mIsRecordingVideo = true;
          // Start recording
          mMediaRecorder.start();
        });
      }

      @Override
      public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
        Log.e(TAG, "onConfigureFailed: Failed");
      }
    }, mBackgroundHandler);
//    cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
//              @Override
//              public void onConfigured(@NonNull CameraCaptureSession session) {
//                try {
//                  Log.e(TAG,"onconfigured");
//
//                  session.capture(captureBuilder.build(), captureListener, null);
//                } catch (final CameraAccessException e) {
//                  Log.e(TAG, " exception occurred while accessing " , e);
//                }
//              }
//
//              @Override
//              public void onConfigureFailed(@NonNull CameraCaptureSession session) {
//              }
//            }
//            , null);
  }
  private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
    builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
  }
//  private void updatePreview() {
//    if (null == cameraDevice) {
//      return;
//    }
//    try {
//      setUpCaptureRequestBuilder(videocaptureBuilder);
//      HandlerThread thread = new HandlerThread("CameraPreview");
//      thread.start();
//      mPreviewSession.setRepeatingRequest(videocaptureBuilder.build(), null, mBackgroundHandler);
//    } catch (CameraAccessException e) {
//      e.printStackTrace();
//    }
//  }
private void updatePreview() {
  if (null == cameraDevice) {
    return;
  }
  try {

//    createCameraPreviewvideoSession();
    Log.e(TAG,"Update preview...");
    setUpCaptureRequestBuilder(previewRequestBuilder);
    HandlerThread thread = new HandlerThread("CameraPreview");
    thread.start();
//    new Handler().postDelayed(() -> {
//      try {
//        takevideoPicture2();
//      } catch (CameraAccessException | IOException e) {
//        e.printStackTrace();
//      }
//    }, 100);
    captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback, mBackgroundHandler);

//
    // Here, we create a CameraCaptureSession for camera preview.
//    cameraDevice.createCaptureSession(
//            Arrays.asList(surface, previewReader.getSurface()),
//            new CameraCaptureSession.StateCallback() {
//
//              @Override
//              public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
//                // The camera is already closed
//                Log.e(TAG,"refresh on configure"+cameraDevice.toString());
//                if (null == cameraDevice) {
//                  return;
//                }
//
//                captureSession = cameraCaptureSession;
//                try {
//                  // Auto focus should be continuous for camera preview.
//                  previewRequestBuilder.set(
//                          CaptureRequest.CONTROL_AF_MODE,
//                          CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//                  // Flash is automatically enabled when necessary.
//                  previewRequestBuilder.set(
//                          CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
//
//                  // Finally, we start displaying the camera preview.
//                  previewRequest = previewRequestBuilder.build();
//                  captureSession.setRepeatingRequest(
//                          previewRequest, captureCallback, backgroundHandler);
//                } catch (final CameraAccessException e) {
//                  LOGGER.e(e, "Exception!");
//                }
//              }
//
//              @Override
//              public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
//                showToast("Failed");
//              }
//            },
//            null);

  } catch (CameraAccessException e) {
    e.printStackTrace();
  }



}
  private void takePicture() throws CameraAccessException {
    if (null == cameraDevice) {
      Log.e(TAG, "cameraDevice is null");
      return;
    }
    characteristics = manager.getCameraCharacteristics(cameraId);
//     characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
    Size[] jpegSizes = null;
    StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
    if (streamConfigurationMap != null) {
      jpegSizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
    }
    final boolean jpegSizesNotEmpty = jpegSizes != null && 0 < jpegSizes.length;
    int width = jpegSizesNotEmpty ? jpegSizes[0].getWidth() : 640;
    int height = jpegSizesNotEmpty ? jpegSizes[0].getHeight() : 480;
    final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
    final List<Surface> outputSurfaces = new ArrayList<>();
    outputSurfaces.add(reader.getSurface());
    final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
    captureBuilder.addTarget(reader.getSurface());
    captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);
    reader.setOnImageAvailableListener(onImageAvailableListener, null);
    Log.e(TAG,"go to onimageavail");
    cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
              @Override
              public void onConfigured(@NonNull CameraCaptureSession session) {
                Log.e(TAG,"onconfigured");

                try {
                  session.capture(captureBuilder.build(), captureListener, null);
                } catch (CameraAccessException e) {
                  e.printStackTrace();
                }

//                  session.capture(captureBuilder.build(), captureListener, null);
              }

              @Override
              public void onConfigureFailed(@NonNull CameraCaptureSession session) {
              }
            }
            , null);
  }
  /**
   * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a {@link
   * TextureView}.
   */
  private final TextureView.SurfaceTextureListener surfaceTextureListener =
      new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(
            final SurfaceTexture texture, final int width, final int height) {
          openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(
            final SurfaceTexture texture, final int width, final int height) {
          configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
          return true;
        }

        @Override
        public void onSurfaceTextureUpdated(final SurfaceTexture texture) {}
      };

  private CameraConnectionFragment(
      final ConnectionCallback connectionCallback,
      final OnImageAvailableListener imageListener,
      final int layout,
      final Size inputSize) {
    this.cameraConnectionCallback = connectionCallback;
    this.imageListener = imageListener;
    this.layout = layout;
    this.inputSize = inputSize;
  }

  /**
   * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
   * width and height are at least as large as the minimum of both, or an exact match if possible.
   *
   * @param choices The list of sizes that the camera supports for the intended output class
   * @param width The minimum desired width
   * @param height The minimum desired height
   * @return The optimal {@code Size}, or an arbitrary one if none were big enough
   */
  protected static Size chooseOptimalSize(final Size[] choices, final int width, final int height) {
    final int minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE);
    final Size desiredSize = new Size(width, height);

    // Collect the supported resolutions that are at least as big as the preview Surface
    boolean exactSizeFound = false;
    final List<Size> bigEnough = new ArrayList<Size>();
    final List<Size> tooSmall = new ArrayList<Size>();
    for (final Size option : choices) {
      if (option.equals(desiredSize)) {
        // Set the size but don't return yet so that remaining sizes will still be logged.
        exactSizeFound = true;
      }

      if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
        bigEnough.add(option);
      } else {
        tooSmall.add(option);
      }
    }

    LOGGER.i("Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize);
    LOGGER.i("Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]");
    LOGGER.i("Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]");

    if (exactSizeFound) {
      LOGGER.i("Exact size match found.");
      return desiredSize;
    }

    // Pick the smallest of those, assuming we found any
    if (bigEnough.size() > 0) {
      final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
      LOGGER.i("Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
      return chosenSize;
    } else {
      LOGGER.e("Couldn't find any suitable preview size");
      return choices[0];
    }
  }

  public static CameraConnectionFragment newInstance(
      final ConnectionCallback callback,
      final OnImageAvailableListener imageListener,
      final int layout,
      final Size inputSize) {
    return new CameraConnectionFragment(callback, imageListener, layout, inputSize);
  }

  /**
   * Shows a {@link Toast} on the UI thread.
   *
   * @param text The message to show
   */

  public void showToast(final String text) {
    Log.e("audio","showToast");
    final Activity activity = getActivity();
    if (activity != null) {
      activity.runOnUiThread(
          new Runnable() {
            @Override
            public void run() {
              Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
            }
          });
    }
  }

  @Override
  public View onCreateView(
      final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
    return inflater.inflate(layout, container, false);
  }

  @Override
  public void onViewCreated(final View view, final Bundle savedInstanceState) {
    textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
  }

  @Override
  public void onActivityCreated(final Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
  }

  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();

    startBackgroundThread2();
    // When the screen is turned off and turned back on, the SurfaceTexture is already
    // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
    // a camera and start preview from here (otherwise, we wait until the surface is ready in
    // the SurfaceTextureListener).
    if (textureView.isAvailable()) {
      openCamera(textureView.getWidth(), textureView.getHeight());
    } else {
      textureView.setSurfaceTextureListener(surfaceTextureListener);
    }
  }

  @Override
  public void onPause() {
    closeCamera();
    stopBackgroundThread();

    stopBackgroundThread2();
    super.onPause();
  }

  public void setCamera(String cameraId) {
    this.cameraId = cameraId;
  }

  /** Sets up member variables related to camera. */
  private void setUpCameraOutputs() {
    final Activity activity = getActivity();
    manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
       characteristics = manager.getCameraCharacteristics(cameraId);

      final StreamConfigurationMap map =
          characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

      sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

      // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
      // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
      // garbage capture data.
      previewSize =
          chooseOptimalSize(
              map.getOutputSizes(SurfaceTexture.class),
              inputSize.getWidth(),
              inputSize.getHeight());

      // We fit the aspect ratio of TextureView to the size of preview we picked.
      final int orientation = getResources().getConfiguration().orientation;
      if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
      } else {
        textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
      }
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    } catch (final NullPointerException e) {
      // Currently an NPE is thrown when the Camera2API is used but not supported on the
      // device this code runs.
      ErrorDialog.newInstance(getString(R.string.tfe_od_camera_error))
          .show(getChildFragmentManager(), FRAGMENT_DIALOG);
      throw new IllegalStateException(getString(R.string.tfe_od_camera_error));
    }

    cameraConnectionCallback.onPreviewSizeChosen(previewSize, sensorOrientation);
  }
  private File getOutputMediaFile() {

    // External sdcard file location
    File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
            VIDEO_DIRECTORY_NAME);
    // Create storage directory if it does not exist
    if (!mediaStorageDir.exists()) {
      if (!mediaStorageDir.mkdirs()) {
        Log.d(TAG, "Oops! Failed create "
                + VIDEO_DIRECTORY_NAME + " directory");
        return null;
      }
    }
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
            Locale.getDefault()).format(new Date());
    File mediaFile;

    Log.e(TAG,"outputmedia file");
    mediaFile = new File(mediaStorageDir.getPath() + File.separator
            + "VID_" + timeStamp + ".mp4");
    return mediaFile;
  }
  /** Opens the camera specified by {@link CameraConnectionFragment#cameraId}. */
  private void openCamera(final int width, final int height) {
    setUpCameraOutputs();
    configureTransform(width, height);
    final Activity activity = getActivity();
    final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    mMediaRecorder = new MediaRecorder();
    mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
    try {
      if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
      manager.openCamera(cameraId, stateCallback, backgroundHandler);
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    } catch (final InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
    }
  }

  /** Closes the current {@link CameraDevice}. */
  private void closeCamera() {
    try {
      cameraOpenCloseLock.acquire();
      if (null != captureSession) {
        captureSession.close();
        captureSession = null;
      }
      if (null != cameraDevice) {
        cameraDevice.close();
        cameraDevice = null;
      }
      if (null != previewReader) {
        previewReader.close();
        previewReader = null;
      }
    } catch (final InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      cameraOpenCloseLock.release();
    }
  }

  /** Starts a background thread and its {@link Handler}. */
  private void startBackgroundThread() {
    backgroundThread = new HandlerThread("ImageListener");
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());
  }

  /** Stops the background thread and its {@link Handler}. */
  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
      backgroundHandler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }
  }
  private void closePreviewSession() {
    if (captureSession != null) {
      captureSession.close();
      captureSession = null;
    }
  }
  /** Creates a new {@link CameraCaptureSession} for camera preview. */
  private void createCameraPreviewSession() {
    try {
      closePreviewSession();
      final SurfaceTexture texture = textureView.getSurfaceTexture();
      assert texture != null;

      // We configure the size of default buffer to be the size of camera preview we want.
      texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

      // This is the output Surface we need to start preview.
      final Surface surface = new Surface(texture);

      // We set up a CaptureRequest.Builder with the output Surface.
      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      previewRequestBuilder.addTarget(surface);
      previewReader =
              ImageReader.newInstance(
                      previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

      previewReader.setOnImageAvailableListener(imageListener, backgroundHandler);
      previewRequestBuilder.addTarget(previewReader.getSurface());


      Log.e(TAG,"gogogoggogoogogo");
      Log.e(TAG,previewRequestBuilder.toString());
      cameraDevice.createCaptureSession(
              Arrays.asList(surface, previewReader.getSurface()),
              new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
                  // The camera is already closed
                  if (null == cameraDevice) {
                    return;
                  }
                  Log.e(TAG,"onconfigure started"+cameraDevice.toString());
                  // When the session is ready, we start displaying the preview.
                  captureSession = cameraCaptureSession;

                  updatePreview();
                }

                @Override
                public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
                  showToast("Failed");
                }
              },
              mBackgroundHandler);
      //      previewReader =
//          ImageReader.newInstance(
//              previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
//
//      previewReader.setOnImageAvailableListener(imageListener, backgroundHandler);
//      previewRequestBuilder.addTarget(previewReader.getSurface());
//      Surface previewSurface = new Surface(texture);
//      previewRequestBuilder.addTarget(previewSurface);
//      cameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
//              new CameraCaptureSession.StateCallback() {
//
//                @Override
//                public void onConfigured(@NonNull CameraCaptureSession session) {
//                  captureSession = session;
//                  updatePreview();
//                }
//
//                @Override
//                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
//                  Log.e(TAG, "onConfigureFailed: Failed ");
//                }
//              }, mBackgroundHandler);

//      closePreviewSession();
//      final SurfaceTexture texture = textureView.getSurfaceTexture();
//      assert texture != null;
//
//      // We configure the size of default buffer to be the size of camera preview we want.
//      texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
//
//      // This is the output Surface we need to start preview.
//      final Surface surface = new Surface(texture);
//
//      // We set up a CaptureRequest.Builder with the output Surface.
//      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//      previewRequestBuilder.addTarget(surface);
//
//      LOGGER.i("Opening camera preview: " + previewSize.getWidth() + "x" + previewSize.getHeight());
//
//      // Create the reader for the preview frames.
//      previewReader =
//          ImageReader.newInstance(
//              previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
//
//      previewReader.setOnImageAvailableListener(imageListener, backgroundHandler);
//      previewRequestBuilder.addTarget(previewReader.getSurface());
//
//
//      Log.e(TAG,"gogogoggogoogogo");
//      Log.e(TAG,previewRequestBuilder.toString());
//      // Here, we create a CameraCaptureSession for camera preview.
//      cameraDevice.createCaptureSession(
//          Arrays.asList(surface, previewReader.getSurface()),
//          new CameraCaptureSession.StateCallback() {
//
//            @Override
//            public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
//              // The camera is already closed
//              if (null == cameraDevice) {
//                return;
//              }
//              Log.e(TAG,"onconfigure started"+cameraDevice.toString());
//              // When the session is ready, we start displaying the preview.
//              captureSession = cameraCaptureSession;
//
//              updatePreview();
//            }
//
//            @Override
//            public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
//              showToast("Failed");
//            }
//          },
//              mBackgroundHandler);
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    }
  }
  /** Creates a new {@link CameraCaptureSession} for camera preview. */
  private void createCameraPreviewvideoSession() {
    try {
      final SurfaceTexture texture = textureView.getSurfaceTexture();
      assert texture != null;

      // We configure the size of default buffer to be the size of camera preview we want.
      texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

      // This is the output Surface we need to start preview.
      final Surface surface = new Surface(texture);

      // We set up a CaptureRequest.Builder with the output Surface.
      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      previewRequestBuilder.addTarget(surface);

      LOGGER.i("Opening camera preview: " + previewSize.getWidth() + "x" + previewSize.getHeight());

      // Create the reader for the preview frames.
      previewReader =
              ImageReader.newInstance(
                      previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

      previewReader.setOnImageAvailableListener(imageListener, backgroundHandler);
      previewRequestBuilder.addTarget(previewReader.getSurface());

      // Here, we create a CameraCaptureSession for camera preview.
      cameraDevice.createCaptureSession(
              Arrays.asList(surface, previewReader.getSurface()),
              new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
                  // The camera is already closed
                  if (null == cameraDevice) {
                    return;
                  }
                  Log.e(TAG,"onconfigure started"+cameraDevice.toString());
                  // When the session is ready, we start displaying the preview.
                  captureSession = cameraCaptureSession;

                  updatePreview();
//              try {
//                // Auto focus should be continuous for camera preview.
//                previewRequestBuilder.set(
//                    CaptureRequest.CONTROL_AF_MODE,
//                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//                // Flash is automatically enabled when necessary.
//                previewRequestBuilder.set(
//                    CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
//
//                // Finally, we start displaying the camera preview.
//                previewRequest = previewRequestBuilder.build();
//                captureSession.setRepeatingRequest(
//                    previewRequest, captureCallback, backgroundHandler);
//              } catch (final CameraAccessException e) {
//                LOGGER.e(e, "Exception!");
//              }
                }

                @Override
                public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
                  showToast("Failed");
                }
              },
              mBackgroundHandler);
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    }
  }

//
//  public void takePicture() {
//
//
//      Size[] jpegSizes = null;
//      if (characteristics != null) {
//        jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
//      }
//      int width = 640;
//      int height = 480;
//      if (jpegSizes != null &&
//              0 < jpegSizes.length) {
//        width = jpegSizes[0].getWidth();
//        height = jpegSizes[0].getHeight();
//      }
//      final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
//      List<Surface> outputSurfaces = new ArrayList<Surface>(2);
//      outputSurfaces.add(reader.getSurface());
//      //i believe this fucks over the preview after snapping
//      //outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
//      final CaptureRequest.Builder captureBuilder;
//      try {
//        captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
//        captureBuilder.addTarget(reader.getSurface());
//        captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
//        // Orientation
//        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);
//      } catch (CameraAccessException e) {
//        e.printStackTrace();
//      }
//
//      final File file = new File(Environment.getExternalStorageDirectory()+"/pictureeee.jpg");
//      ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
//        @Override
//        public void onImageAvailable(ImageReader reader) {
//          Image image = null;
//          try {
//            image = reader.acquireLatestImage();
//            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
//            byte[] bytes = new byte[buffer.capacity()];
//            buffer.get(bytes);
//            save(bytes);
//          } catch (FileNotFoundException e) {
//            e.printStackTrace();
//          } catch (IOException e) {
//            e.printStackTrace();
//          } finally {
//            if (image != null) {
//              image.close();
//            }
//          }
//        }
//        private void save(byte[] bytes) throws IOException {
//          OutputStream output = null;
//          try {
//            output = new FileOutputStream(file);
//            output.write(bytes);
//          } finally {
//            if (null != output) {
//              output.close();
//            }
//          }
//        }
//      };
////      reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
////      final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
////        @Override
////        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
////          super.onCaptureCompleted(session, request, result);
////          Toast.makeText(ScanReceipt.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
////          createCameraPreview();
////        }
////      };
////      cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
////        @Override
////        public void onConfigured(@NonNull CameraCaptureSession session) {
////          try {
////            session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
////          } catch (CameraAccessException e) {
////            e.printStackTrace();
////          }
////        }
////        @Override
////        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
////          Toast.makeText(getApplicationContext(), "Failed on config!", Toast.LENGTH_SHORT).show();
////        }
////      }, mBackgroundHandler);
////    } catch (CameraAccessException e) {
////      e.printStackTrace();
////    }
//
//  }

  /**
   * Configures the necessary {@link Matrix} transformation to `mTextureView`. This method should be
   * called after the camera preview size is determined in setUpCameraOutputs and also the size of
   * `mTextureView` is fixed.
   *
   * @param viewWidth The width of `mTextureView`
   * @param viewHeight The height of `mTextureView`
   */
  private void configureTransform(final int viewWidth, final int viewHeight) {
    final Activity activity = getActivity();
    if (null == textureView || null == previewSize || null == activity) {
      return;
    }
    final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    final Matrix matrix = new Matrix();
    final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
    final float centerX = viewRect.centerX();
    final float centerY = viewRect.centerY();
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      final float scale =
          Math.max(
              (float) viewHeight / previewSize.getHeight(),
              (float) viewWidth / previewSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    } else if (Surface.ROTATION_180 == rotation) {
      matrix.postRotate(180, centerX, centerY);
    }
    textureView.setTransform(matrix);
  }

  @Override
  public void onCaptureDone(String pictureUrl, byte[] pictureData) {

  }

  @Override
  public void onDoneCapturingAllPhotos(TreeMap<String, byte[]> picturesTaken) {

  }

  /**
   * Callback for Activities to use to initialize their data once the selected preview size is
   * known.
   */
  public interface ConnectionCallback {
    void onPreviewSizeChosen(Size size, int cameraRotation);
  }

  /** Compares two {@code Size}s based on their areas. */
  static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(final Size lhs, final Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum(
          (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }

  private void openCamera() {
    Log.e(TAG, "opening camera " + cameraId);

//    manager.openCamera(currentCameraId, stateCallback, null);
//    try {
//      if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
//              == PackageManager.PERMISSION_GRANTED
//              && ActivityCompat.checkSelfPermission(context,
//              Manifest.permission.WRITE_EXTERNAL_STORAGE)
//              == PackageManager.PERMISSION_GRANTED) {
//        Log.e(TAG,"open camera ..."+currentCameraId);
//        if(currentCameraId.equals("0")){
//
//        }
//      }
//    } catch (final CameraAccessException e) {
//      Log.e(TAG, " exception occurred while opening camera " + currentCameraId, e);
//    }
  }

 public void capturestaring(CameraActivity ca){
    Log.e(TAG,"capture startin...");
   picturesTaken = new TreeMap<>();
   //Take the picture after some delay. It may resolve getting a black dark photos.
   new Handler().postDelayed(() -> {
     try {
       takePicture();
     } catch (CameraAccessException e) {
       e.printStackTrace();
     }
   }, 1000);
 }
  protected File getCurrentFile() {
    return mCurrentFile;
  }
  public void videostaring(CameraActivity ca) throws IOException, CameraAccessException {
    Log.e(TAG,"videos startin...");

    if (mIsRecordingVideo) {
      Log.e(TAG,"going to stop video");
      try {
        stopRecordingVideo();
//        prepareViews();
      } catch (Exception e) {
        e.printStackTrace();
      }

    } else {
      Log.e(TAG,"going to start video");
      takevideoPicture();

      mOutputFilePath = getCurrentFile().getAbsolutePath();
    }

//    picturesTaken = new TreeMap<>();
//    //Take the picture after some delay. It may resolve getting a black dark photos.
//    new Handler().postDelayed(() -> {
//      try {
//        takePicture();
//      } catch (CameraAccessException e) {
//        e.printStackTrace();
//      }
//    }, 1000);
  }
  /** Shows an error message dialog. */
  public static class ErrorDialog extends DialogFragment {
    private static final String ARG_MESSAGE = "message";

    public static ErrorDialog newInstance(final String message) {
      final ErrorDialog dialog = new ErrorDialog();
      final Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
      return dialog;
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
      final Activity activity = getActivity();
      return new AlertDialog.Builder(activity)
          .setMessage(getArguments().getString(ARG_MESSAGE))
          .setPositiveButton(
              android.R.string.ok,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialogInterface, final int i) {
                  activity.finish();
                }
              })
          .create();
    }
  }
}
