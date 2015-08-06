package jp.ac.u_tokyo.slamwithcameraimu;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.highgui.Highgui;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

/**
 * A simple wrapper around a Camera and a SurfaceView that renders a centered
 * preview of the Camera to the surface. We need to center the SurfaceView
 * because not all devices have cameras that support preview sizes at the same
 * aspect ratio as the device's display.
 */
class Preview extends ViewGroup implements SurfaceHolder.Callback {
	private final String TAG = "SLAM";

	Context mContext;
	SurfaceView mSurfaceView;
	SurfaceHolder mHolder;
	Size mPreviewSize;
	List<Size> mSupportedPreviewSizes;
	Camera mCamera;
	private boolean mProgressFlag = false;

	int count = 0;
	boolean isFirst = true;

	String path = "";
	SimpleDateFormat dateFormat;
	Size prevSize;
	private Mat mGray;
	private Mat mGray90;
	private FeatureDetector detector;
	private DescriptorExtractor extractor;
	Mat image01, image02;
	Mat image01KP, image02KP;
	Mat grayImage01, grayImage02;
	MatOfKeyPoint keyPoint01, keyPoint02;
	Mat descripters01, descripters02;


	Preview(Context context) {
		super(context);

		mContext = context;

		mSurfaceView = new SurfaceView(context);
		addView(mSurfaceView);

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = mSurfaceView.getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
	}

	public SurfaceHolder getHolder(){
		return mHolder;
	}

	public void initOpenCV(){
		//Mat
		mGray = new Mat(prevSize.height, prevSize.width, CvType.CV_8U); // プレビューサイズ分のMatを用意
		mGray90 = new Mat(prevSize.width, prevSize.height, CvType.CV_8U); // 今回はポートレイト＋フロントカメラを使ったので画像を回転させたりするためのバッファ

		//Features2d
		detector = FeatureDetector
				.create(FeatureDetector.ORB);
		extractor = DescriptorExtractor
				.create(DescriptorExtractor.ORB);

		//read conf of detector
		path = Environment.getExternalStorageDirectory()
				.getPath()
				+ "/DCIM/SLAMwithCameraIMU/conf/detector.txt";
		detector.read(path);

		path = Environment.getExternalStorageDirectory()
				.getPath()
				+ "/DCIM/SLAMwithCameraIMU/conf/extractor.txt";
		extractor.read(path);
	}

	private final Camera.PreviewCallback editPreviewImage = new Camera.PreviewCallback() {

		public void onPreviewFrame(byte[] data, Camera camera) {

//			count++;
//			Log.d(TAG, "count = " + count);
//			if (count >= 50) {
//				count = 0;
//				mCamera.stopPreview();

				Log.d(TAG, "captured");
//				Toast.makeText(mContext, "captured", Toast.LENGTH_SHORT).show();
				new QuickToastTask(mContext, "captured", 20).execute();

				if(isFirst){
					initOpenCV();
				}

				mGray.put(0, 0, data); // プレビュー画像NV21のYデータをコピーすればグレースケール画像になる
				Core.flip(mGray.t(), mGray90, 0); // ポートレイト＋フロントなので回転
				Core.flip(mGray90, mGray90, -1);

				image02 = mGray90;
				image02KP = mGray90;
				grayImage02 = mGray90;

				keyPoint02 = new MatOfKeyPoint();
				detector.detect(grayImage02, keyPoint02);

				descripters02 = new Mat(image02.rows(), image02.cols(),
						image02.type());
				extractor.compute(grayImage02, keyPoint02, descripters02);

				Features2d.drawKeypoints(image02, keyPoint02, image02KP);

				// 画像を保存
//				path = Environment.getExternalStorageDirectory()
//						.getPath()
//						+ "/DCIM/SLAMwithCameraIMU/"
//						+ dateFormat.format(new Date()) + "_KP.jpg";
//				Highgui.imwrite(path, image02KP);


				if (!isFirst) {
					MatOfDMatch matchs = new MatOfDMatch();
					DescriptorMatcher matcher = DescriptorMatcher
							.create(DescriptorMatcher.BRUTEFORCE);
					matcher.match(descripters01, descripters02, matchs);

//					// 上位50点以外の点を除去する
//					int N = 50;
//					DMatch[] tmp01 = matchs.toArray();
//					DMatch[] tmp02 = new DMatch[N];
//					for (int i = 0; i < tmp02.length; i++) {
//						tmp02[i] = tmp01[i];
//					}
//					matchs.fromArray(tmp02);

					Mat matchedImage = new Mat(image01.rows(),
							image01.cols() * 2, image01.type());
					Features2d.drawMatches(image01, keyPoint01, image02,
							keyPoint02, matchs, matchedImage);

					// 画像を保存
					path = Environment.getExternalStorageDirectory().getPath()
							+ "/DCIM/SLAMwithCameraIMU/"
							+ dateFormat.format(new Date()) + "_Match.jpg";
					Highgui.imwrite(path, matchedImage);
				}

				image01 = image02;
				keyPoint01 = keyPoint02;
				descripters01 = descripters02;

				isFirst = false;

//				mCamera.setPreviewCallback(editPreviewImage);
//				mCamera.startPreview();
//			}
		}
	};

	public void setCamera(Camera camera) {
		mCamera = camera;
		if (mCamera != null) {
			mSupportedPreviewSizes = mCamera.getParameters()
					.getSupportedPreviewSizes();
			requestLayout();
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		try {
			if (mCamera != null) {
				mCamera.setPreviewDisplay(holder);
			}
		} catch (IOException exception) {
			Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		try{
			// Now that the size is known, set up the camera parameters and begin
			// the preview.
			Camera.Parameters parameters = mCamera.getParameters();
			parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
			requestLayout();

			prevSize = parameters.getPreviewSize();

			mCamera.setParameters(parameters);
			mCamera.setPreviewCallback(editPreviewImage);
			mCamera.startPreview();
		}catch(Exception e){

		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		// Surface will be destroyed when we return, so stop the preview.
		if (mCamera != null) {
			mCamera.stopPreview();
		}
	}

	public void stopPreview(){
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			setCamera(null);
		}
	}

	public void switchCamera(Camera camera) {
		setCamera(camera);
		try {
			camera.setPreviewDisplay(mHolder);
		} catch (IOException exception) {
			Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
		}
		Camera.Parameters parameters = camera.getParameters();
		parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
		requestLayout();

		camera.setParameters(parameters);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// We purposely disregard child measurements because act as a
		// wrapper to a SurfaceView that centers the camera preview instead
		// of stretching it.
		final int width = resolveSize(getSuggestedMinimumWidth(),
				widthMeasureSpec);
		final int height = resolveSize(getSuggestedMinimumHeight(),
				heightMeasureSpec);
		setMeasuredDimension(width, height);

		if (mSupportedPreviewSizes != null) {
			mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width,
					height);
		}
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		if (changed && getChildCount() > 0) {
			final View child = getChildAt(0);

			final int width = r - l;
			final int height = b - t;

			int previewWidth = width;
			int previewHeight = height;
			if (mPreviewSize != null) {
				previewWidth = mPreviewSize.width;
				previewHeight = mPreviewSize.height;
			}

			// Center the child SurfaceView within the parent.
			if (width * previewHeight > height * previewWidth) {
				final int scaledChildWidth = previewWidth * height
						/ previewHeight;
				child.layout((width - scaledChildWidth) / 2, 0,
						(width + scaledChildWidth) / 2, height);
			} else {
				final int scaledChildHeight = previewHeight * width
						/ previewWidth;
				child.layout(0, (height - scaledChildHeight) / 2, width,
						(height + scaledChildHeight) / 2);
			}
		}
	}

	private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
		final double ASPECT_TOLERANCE = 0.1;
		double targetRatio = (double) w / h;
		if (sizes == null)
			return null;

		Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;

		int targetHeight = h;

		// Try to find an size match aspect ratio and size
		for (Size size : sizes) {
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if (Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}

		// Cannot find the one match the aspect ratio, ignore the requirement
		if (optimalSize == null) {
			minDiff = Double.MAX_VALUE;
			for (Size size : sizes) {
				if (Math.abs(size.height - targetHeight) < minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.height - targetHeight);
				}
			}
		}
		return optimalSize;
	}

}
