/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ypt.twodimensionaldemo.lib.activity;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Hashtable;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.ypt.twodimensionaldemo.MainActivity;
import com.ypt.twodimensionaldemo.R;
import com.ypt.twodimensionaldemo.lib.camera.CameraManager;
import com.ypt.twodimensionaldemo.lib.decode.DecodeFormatManager;
import com.ypt.twodimensionaldemo.lib.decode.DecodeThread;
import com.ypt.twodimensionaldemo.lib.utils.BeepManager;
import com.ypt.twodimensionaldemo.lib.utils.CaptureActivityHandler;
import com.ypt.twodimensionaldemo.lib.utils.InactivityTimer;
import com.ypt.twodimensionaldemo.lib.utils.Tools;

/**
 * This activity opens the camera and does the actual scanning on a background
 * thread. It draws a viewfinder to help the user place the barcode correctly,
 * shows feedback as the image processing is happening, and then overlays the
 * results when a scan is successful.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class CaptureActivity extends Activity implements
		SurfaceHolder.Callback {

	private static final String TAG = CaptureActivity.class.getSimpleName();

	private CameraManager cameraManager;
	private CaptureActivityHandler handler;
	private InactivityTimer inactivityTimer;
	private BeepManager beepManager;

	private SurfaceView scanPreview = null;
	private RelativeLayout scanContainer;
	private RelativeLayout scanCropView;
	private ImageView scanLine;

	private Rect mCropRect = null;
	private boolean isHasSurface = false;

	public Handler getHandler() {
		return handler;
	}

	public CameraManager getCameraManager() {
		return cameraManager;
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_capture);

		scanPreview = (SurfaceView) findViewById(R.id.capture_preview);
		scanContainer = (RelativeLayout) findViewById(R.id.capture_container);
		scanCropView = (RelativeLayout) findViewById(R.id.capture_crop_view);
		scanLine = (ImageView) findViewById(R.id.capture_scan_line);

		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);

		TranslateAnimation animation = new TranslateAnimation(
				Animation.RELATIVE_TO_PARENT, 0.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f,
				Animation.RELATIVE_TO_PARENT, 0.9f);
		animation.setDuration(4500);
		animation.setRepeatCount(-1);
		animation.setRepeatMode(Animation.RESTART);
		scanLine.startAnimation(animation);
	}

	@Override
	protected void onResume() {
		super.onResume();

		// CameraManager must be initialized here, not in onCreate(). This is
		// necessary because we don't
		// want to open the camera driver and measure the screen size if we're
		// going to show the help on
		// first launch. That led to bugs where the scanning rectangle was the
		// wrong size and partially
		// off screen.
		cameraManager = new CameraManager(getApplication());

		handler = null;

		if (isHasSurface) {
			// The activity was paused but not stopped, so the surface still
			// exists. Therefore
			// surfaceCreated() won't be called, so init the camera here.
			initCamera(scanPreview.getHolder());
		} else {
			// Install the callback and wait for surfaceCreated() to init the
			// camera.
			scanPreview.getHolder().addCallback(this);
		}

		inactivityTimer.onResume();
	}

	@Override
	protected void onPause() {
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		inactivityTimer.onPause();
		beepManager.close();
		cameraManager.closeDriver();
		if (!isHasSurface) {
			scanPreview.getHolder().removeCallback(this);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (holder == null) {
			Log.e(TAG,
					"*** WARNING *** surfaceCreated() gave us a null surface!");
		}
		if (!isHasSurface) {
			isHasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		isHasSurface = false;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {

	}

	/**
	 * A valid barcode has been found, so give an indication of success and show
	 * the results.
	 * 
	 * @param rawResult
	 *            The contents of the barcode.
	 * @param bundle
	 *            The extras
	 */
	public void handleDecode(Result rawResult, Bundle bundle) {
		inactivityTimer.onActivity();
		beepManager.playBeepSoundAndVibrate();

		// Toast.makeText(this, "reuslt:" + rawResult.getText(),
		// Toast.LENGTH_LONG)
		// .show();
		// finish();
		Intent resultIntent = new Intent();
		bundle.putInt("width", mCropRect.width());
		bundle.putInt("height", mCropRect.height());
		bundle.putString("result", rawResult.getText());
		resultIntent.putExtras(bundle);
		this.setResult(RESULT_OK, resultIntent);
		CaptureActivity.this.finish();
	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		if (surfaceHolder == null) {
			throw new IllegalStateException("No SurfaceHolder provided");
		}
		if (cameraManager.isOpen()) {
			Log.w(TAG,
					"initCamera() while already open -- late SurfaceView callback?");
			return;
		}
		try {
			cameraManager.openDriver(surfaceHolder);
			// Creating the handler starts the preview, which can also throw a
			// RuntimeException.
			if (handler == null) {
				handler = new CaptureActivityHandler(this, cameraManager,
						DecodeThread.ALL_MODE);
			}

			initCrop();
		} catch (IOException ioe) {
			Log.w(TAG, ioe);
			displayFrameworkBugMessageAndExit();
		} catch (RuntimeException e) {
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			Log.w(TAG, "Unexpected error initializing camera", e);
			displayFrameworkBugMessageAndExit();
		}
	}

	private void displayFrameworkBugMessageAndExit() {
		// camera error
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage("Camera error");
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				finish();
			}

		});
		builder.setOnCancelListener(new DialogInterface.OnCancelListener() {

			@Override
			public void onCancel(DialogInterface dialog) {
				finish();
			}
		});
		builder.show();
	}

	public void restartPreviewAfterDelay(long delayMS) {
		if (handler != null) {
			handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
		}
	}

	public Rect getCropRect() {
		return mCropRect;
	}

	/**
	 * 初始化截取的矩形区域
	 */
	private void initCrop() {
		int cameraWidth = cameraManager.getCameraResolution().y;
		int cameraHeight = cameraManager.getCameraResolution().x;

		Log.e("aaaaa", cameraWidth + "--------------------" + cameraHeight);
		/** 获取布局中扫描框的位置信息 */
		int[] location = new int[2];
		scanCropView.getLocationInWindow(location);

		int cropLeft = location[0];
		int cropTop = location[1] - getStatusBarHeight();

		int cropWidth = scanCropView.getWidth();
		int cropHeight = scanCropView.getHeight();

		/** 获取布局容器的宽高 */
		int containerWidth = scanContainer.getWidth();
		int containerHeight = scanContainer.getHeight();

		/** 计算最终截取的矩形的左上角顶点x坐标 */
		int x = cropLeft * cameraWidth / containerWidth;
		/** 计算最终截取的矩形的左上角顶点y坐标 */
		int y = cropTop * cameraHeight / containerHeight;

		/** 计算最终截取的矩形的宽度 */
		int width = cropWidth * cameraWidth / containerWidth;
		/** 计算最终截取的矩形的高度 */
		int height = cropHeight * cameraHeight / containerHeight;

		Log.e("aaaaa", x + "--------------------" + y);
		Log.e("aaaaa", width + "--------------------" + height);
		/** 生成最终的截取的矩形 */
		mCropRect = new Rect(x, y, width + x, height + y);
	}

	private int getStatusBarHeight() {
		try {
			Class<?> c = Class.forName("com.android.internal.R$dimen");
			Object obj = c.newInstance();
			Field field = c.getField("status_bar_height");
			int x = Integer.parseInt(field.get(obj).toString());
			return getResources().getDimensionPixelSize(x);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * 扫描二维码图片的方法
	 * 
	 * @param path
	 * @return
	 */
	// 解析二维码图片,返回结果封装在Result对象中
	private com.google.zxing.Result parseQRcodeBitmap(Bitmap bitmapPath) {
		if (bitmapPath == null) {
			Log.i("deCode", "-----------------------null");
			return null;
		}
		Hashtable<DecodeHintType, Object> hints = null;
		initHints(hints, null, "UTF8");
		MultiFormatReader multiFormatReader = new MultiFormatReader();
		multiFormatReader.setHints(hints);

		Result result = null;

		com.ypt.twodimensionaldemo.lib.utils.RGBLuminanceSource rgbLuminanceSource = new com.ypt.twodimensionaldemo.lib.utils.RGBLuminanceSource(
				bitmapPath);
		BinaryBitmap bit = new BinaryBitmap(new HybridBinarizer(
				rgbLuminanceSource));

		try {
			return multiFormatReader.decodeWithState(bit);

		} catch (ReaderException re) {
			// continue
		} finally {
			multiFormatReader.reset();
		}
		return null;

	}

	public void initHints(Hashtable<DecodeHintType, Object> hints,
			Vector<BarcodeFormat> decodeFormats, String CODE_STYLE) {
		hints = new Hashtable<DecodeHintType, Object>(2);
		if (decodeFormats == null || decodeFormats.isEmpty()) {
			decodeFormats = new Vector<BarcodeFormat>();
			decodeFormats.addAll(DecodeFormatManager.PRODUCT_FORMATS);
			decodeFormats.addAll(DecodeFormatManager.INDUSTRIAL_FORMATS);
			decodeFormats.addAll(DecodeFormatManager.ONE_D_FORMATS);
		}
		hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
		if (CODE_STYLE != null) {

			// hints.put(DecodeHintType.CHARACTER_SET, CODE_STYLE);
		}

	}

	/* 标注闪光灯的开发 */
	boolean isOpen = false;

	/**
	 * 打开闪光灯
	 * 
	 * @param v
	 */
	public void turnOnLight(View v) {
		if (!isOpen) {
			cameraManager.setFlashlightSwitch(true);
			isOpen = true;
		} else {
			cameraManager.setFlashlightSwitch(false);
			isOpen = false;
		}
	}

	/**
	 * 相册
	 * 
	 * @param v
	 */
	public void toSelectPhoto(View v) {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("image/*");
		intent.putExtra("return-data", true);
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
			startActivityForResult(intent, 2);
		} else {
			startActivityForResult(intent, 2);
		}
	}

	/**
	 * 结束
	 * 
	 * @param v
	 */
	public void finish(View v) {
		this.finish();
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if (data == null)
			return;
		if (2 == request) {
			Uri uri = data.getData();
			Bitmap bm = null;

			ContentResolver resolver = getContentResolver();
			try {
				bm = MediaStore.Images.Media.getBitmap(resolver, uri);
			} catch (IOException e) {
				e.printStackTrace();
			}
			bm = Tools.zoomImage(bm, 500, 500);

			com.google.zxing.Result r = parseQRcodeBitmap(bm);
			if (r != null) {
				Toast.makeText(this, "reuslt:" + r.getText(), Toast.LENGTH_LONG)
						.show();
			} else {
				Toast.makeText(CaptureActivity.this, "图片未能识别",
						Toast.LENGTH_SHORT).show();
			}
			finish();
		}
	}

}