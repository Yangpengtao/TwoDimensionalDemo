package com.ypt.twodimensionaldemo.lib.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;

/**
 * @author Administrator ypt
 */
public class Tools {
	/**
	 * ѹ��ͼƬ����һ���Է�ֹoom���ڶ����Է���ʶ�������û��ѹ����ʱ��ö඼ʶ���ˡ�ѹ������������ʶ���ˡ�
	 * 
	 * @param bgimage
	 *            ��Ҫѹ����bitmap
	 * @param newWidth
	 *            ��newHeight �µĸ߿�
	 * @return ѹ�����bitmap
	 */
	public static Bitmap zoomImage(Bitmap bgimage, double newWidth,
			double newHeight) {
		// ��ȡ���ͼƬ�Ŀ�͸�
		float width = bgimage.getWidth();
		float height = bgimage.getHeight();
		// ��������ͼƬ�õ�matrix����
		Matrix matrix = new Matrix();
		// ������������
		float scaleWidth = ((float) newWidth) / width;
		float scaleHeight = ((float) newHeight) / height;
		// ����ͼƬ����
		matrix.postScale(scaleWidth, scaleHeight);
		Bitmap bitmap = Bitmap.createBitmap(bgimage, 0, 0, (int) width,
				(int) height, matrix, true);
		return bitmap;
	}
}
