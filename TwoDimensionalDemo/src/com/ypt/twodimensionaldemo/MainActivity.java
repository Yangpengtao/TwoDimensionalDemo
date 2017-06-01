package com.ypt.twodimensionaldemo;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Interpolator.Result;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.ypt.twodimensionaldemo.lib.activity.CaptureActivity;
import com.ypt.twodimensionaldemo.lib.decode.DecodeFormatManager;
import com.ypt.twodimensionaldemo.lib.utils.Tools;

public class MainActivity extends ActionBarActivity {

	private TextView tvResult;
	private ImageView img;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		tvResult = (TextView) findViewById(R.id.result);
		img = (ImageView) findViewById(R.id.img);

	}

	public void scan(View v) {
		startActivityForResult(new Intent(MainActivity.this,
				CaptureActivity.class), 0);
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if (data == null) return;
		if (request == 0) {
			Bundle b = data.getExtras();
			String str = b.getString("result");
			tvResult.setText(str);
		} else if (2 == request) {
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
				tvResult.setText(r.getText());
			} else {
				Toast.makeText(MainActivity.this, "图片未能识别", Toast.LENGTH_SHORT)
						.show();
				tvResult.setText("<<<图片未能识别>>>");
			}
			img.setImageBitmap(bm);
		}
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

}
