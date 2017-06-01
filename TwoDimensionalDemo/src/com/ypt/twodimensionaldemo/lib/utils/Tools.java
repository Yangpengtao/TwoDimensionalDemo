package com.ypt.twodimensionaldemo.lib.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;

/**
 * @author Administrator ypt
 */
public class Tools {
	/**
	 * 压缩图片，第一可以防止oom，第二可以方便识别。我最初没有压缩的时候好多都识别不了。压缩后大多数都能识别了。
	 * 
	 * @param bgimage
	 *            将要压缩的bitmap
	 * @param newWidth
	 *            ，newHeight 新的高宽
	 * @return 压缩后的bitmap
	 */
	public static Bitmap zoomImage(Bitmap bgimage, double newWidth,
			double newHeight) {
		// 获取这个图片的宽和高
		float width = bgimage.getWidth();
		float height = bgimage.getHeight();
		// 创建操作图片用的matrix对象
		Matrix matrix = new Matrix();
		// 计算宽高缩放率
		float scaleWidth = ((float) newWidth) / width;
		float scaleHeight = ((float) newHeight) / height;
		// 缩放图片动作
		matrix.postScale(scaleWidth, scaleHeight);
		Bitmap bitmap = Bitmap.createBitmap(bgimage, 0, 0, (int) width,
				(int) height, matrix, true);
		return bitmap;
	}
}
