package greendroid.image.ext2;

import android.graphics.Bitmap;
import greendroid.image.ImageProcessor;
import greendroid.image.ext2.IImageLoader.ImageLoaderCallback;

public class ImageRequest {
	
	private String url;
	private String key;
	
	protected ImageProcessor process;
	protected IImageLoader loader;
	protected ImageLoaderCallback loaderCallback;
	
	public Bitmap loadImage(){
		return null;
	}
}
