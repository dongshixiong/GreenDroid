package greendroid.image.ext2;

import android.graphics.Bitmap;

public interface IImageLoader {
	public static interface ImageLoaderCallback {

		void onImageLoadingStarted(IImageLoader loader);

		void onImageLoadingEnded(IImageLoader loader, Bitmap bitmap);

		void onImageLoadingFailed(IImageLoader loader, Throwable exception);
	}
	public void loadImage(ImageRequest request, ImageLoaderCallback callback);
}