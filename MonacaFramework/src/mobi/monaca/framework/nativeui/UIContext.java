package mobi.monaca.framework.nativeui;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;

import mobi.monaca.framework.MonacaPageActivity;
import mobi.monaca.framework.bootloader.LocalFileBootloader;
import mobi.monaca.framework.util.InputStreamLoader;
import mobi.monaca.framework.util.MyLog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class UIContext extends ContextWrapper {

    public interface OnRotateListener {
        public void onRotate(int orientation);
    }

	private static final String TAG = UIContext.class.getSimpleName();

    protected String uiFilePath;
    protected MonacaPageActivity pageActivity;
    protected DisplayMetrics metrics;
    protected SparseIntArray computedFontSizeCache = new SparseIntArray();
    protected ArrayList<OnRotateListener> onRotateListeners = new ArrayList<OnRotateListener>();

    public UIContext(String uiFilePath, MonacaPageActivity pageActivity) {
        super(pageActivity);
        this.uiFilePath = uiFilePath;
        this.pageActivity = pageActivity;

        metrics = new DisplayMetrics();
        pageActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
    }

    public DisplayMetrics getDisplayMetrics() {
        return metrics;
    }

    public void showSoftInput(View view) {
        ((InputMethodManager)pageActivity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(view, 0);
    }

    public String getUIFilePath() {
        return uiFilePath;
    }

    public void react(String uri) {
        if (uri.startsWith("javascript:")) {
            pageActivity.sendJavascript(uri.substring(11));
        } else {
            pageActivity.pushPageWithIntent(uri, null);
        }
    }
	public void loadRelativePathWithoutUIFile(String relativePath) {
		MyLog.v(TAG, "loadRelativePathWithoutUIFile. relativePath:" + relativePath);
		String newUri = pageActivity.getCurrentUriWithoutQuery() + "/../" + relativePath;
		MyLog.v(TAG, "uri unresolved=" + newUri);
		if (newUri.startsWith("file://")) {
			try {
				pageActivity.loadUri("file://" + new File(newUri.substring(7)).getCanonicalPath(), true);
				MyLog.v(TAG, "uri resolved=" + pageActivity.getCurrentUriWithoutQuery());
			} catch (Exception e) {
				e.printStackTrace();
				pageActivity.loadUri(pageActivity.getCurrentUriWithoutQuery(), true);
			}
		} else {
			pageActivity.loadUri(pageActivity.getCurrentUriWithoutQuery(), true);
		}
	}

	public void changeCurrentUri(String uri) {
		String newUri = pageActivity.getCurrentUriWithoutQuery() + "/../" + uri;
		pageActivity.setCurrentUri(newUri);
	}

    public Bitmap readBitmap(String path) {
        path = resolve(path);

        try {
            if (path.startsWith("file:///android_asset/")) {
                InputStream stream = InputStreamLoader.loadAssetFile(this,
                        path.substring("file:///android_asset/".length()));
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                stream.close();

                Matrix matrix = new Matrix();
                float scale = getDensityScale() * getBitmapScaleFactorFromPath(path);
                matrix.postScale(scale, scale);
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            } else if (path.startsWith("file:///")) {
                InputStream stream = InputStreamLoader.loadLocalFile(path
                        .substring("file://".length()));
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                stream.close();

                Matrix matrix = new Matrix();
                float scale = getDensityScale() * getBitmapScaleFactorFromPath(path);
                matrix.postScale(scale, scale);
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    protected float getDensityScale() {
        DisplayMetrics metrics = new DisplayMetrics();
        pageActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

        return metrics.density;
    }

    protected float getBitmapScaleFactorFromPath(String path) {
        if (path.endsWith("@2x.png") || path.endsWith("@2x.gif") || path.endsWith("@2x.jpg") || path.endsWith("@2x.jpeg")) {
            return 0.5f;
        }

        return 1.0f;
    }

    protected String resolve(String path) {
        try {
            path = new URI(uiFilePath).resolve(path).toString();
        } catch (Exception e) {
            return path;
        }

        path = path.startsWith("file:/android_asset/") ? "file://"
                + path.substring("file:".length()) : path;
        path = path.startsWith("file:/data") ? "file:///"
                + path.substring("file:/".length()) : path;

        String preferredPath = getPreferredPath(path);

        return preferredPath != null && exists(preferredPath) ? preferredPath
                : path;
    }

    public static String getPreferredPath(String path) {
        if (path.endsWith(".png")) {
            return path.substring(0, path.length() - 4) + "@2x.png";
        }

        if (path.endsWith(".jpg")) {
            return path.substring(0, path.length() - 4) + "@2x.jpg";
        }

        if (path.endsWith(".jpeg")) {
            return path.substring(0, path.length() - 5) + "@2x.jpeg";
        }

        if (path.endsWith(".gif")) {
            return path.substring(0, path.length() - 4) + "@2x.gif";
        }

        return null;
    }

    protected boolean exists(String resolvedPath) {
        if (resolvedPath.startsWith("file:///android_asset/")) {
            try {
            	InputStream stream = LocalFileBootloader.openAsset(pageActivity.getApplicationContext(),
                        resolvedPath.substring("file:///android_asset/"
                                .length()));
                stream.close();
                return true;
            } catch (Exception e) {
            }
            return false;
        } else if (resolvedPath.startsWith("file://")) {
            return new File(resolvedPath.substring("file://".length()))
                    .exists();
        }

        throw new RuntimeException("unsupported path: " + resolvedPath);
    }

    public int getFontSizeFromDip(int dip) {
        return UIUtil.getFontSizeFromDip(this, dip);
    }

    public void addOnRotateListener(OnRotateListener listener) {
        onRotateListeners.add(listener);
    }

    public void fireOnRotateListeners(int orientation) {
        for (OnRotateListener listener : onRotateListeners) {
            listener.onRotate(orientation);
        }
    }

    public int getUIOrientation() {
        return pageActivity.getResources().getConfiguration().orientation;
    }

}