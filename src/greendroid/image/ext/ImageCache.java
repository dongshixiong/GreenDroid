
package greendroid.image.ext;

import greendroid.util.AndroidBuild;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.http.util.LangUtils;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.widget.ImageView.ScaleType;

public class ImageCache extends ObjectCache<String, Bitmap> {
    private static final String LOG_TAG = "ImageCache";

    private static final int MAX_SIZE = 8 * 1024 * 1024;
    private static final int LARGE_MAX_SIZE = 64 * 1024 * 1024;
    private final int NATIVE_SIZE;
    private static final int BYTE_PRE_PIX = 4;

    private static final String EXTRA_TYPE = "type";

    private final Handler mUIHandler = new Handler(Looper.getMainLooper());
    private final Context mContext;
    private Handler mBgHandler = null;
    private BroadcastReceiver mReceiver;

    private HandlerThread mBgThread;

    private static class LoadReq {
        private final String path;
        private final int width;
        private final int height;
        private final ScaleType type;
        private final HashSet<Pair<String, OnLoadedListener>> listeners;

        public LoadReq(String path, int width, int height, ScaleType type,
                OnLoadedListener listener) {
            super();
            this.path = path;
            this.width = width;
            this.height = height;
            this.type = type;
            this.listeners = new HashSet<Pair<String, OnLoadedListener>>();
            if (listener != null) {
                this.listeners.add(Pair.create(path, listener));
            }
        }

        @Override
        public int hashCode() {
            int code = LangUtils.hashCode(LangUtils.HASH_SEED, path);
            code = LangUtils.hashCode(code, width);
            code = LangUtils.hashCode(code, height);
            code = LangUtils.hashCode(code, type == null ? 0 : type.hashCode());
            return code;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LoadReq)) {
                return false;
            }
            LoadReq req = (LoadReq) o;

            return this.path == req.path && this.width == req.width
                    && this.height == req.height && this.type == req.type;
        }

        public void merge(LoadReq req) {
            this.listeners.addAll(req.listeners);
        }

    }

    private static class BitmapValueNode extends ValueNode<Bitmap> {
        private int width = -1;
        private int height = -1;
        private int scale = -1;
        private boolean inNative = false;

        private HashMap<LoadReq, LoadReq> loadReqs;

        public void copyData(BitmapValueNode node) {
            mValue = node.mValue;
            width = node.width;
            height = node.height;
            scale = node.scale;
            inNative = node.inNative;
        }

        // public LoadReq getSingleReq() {
        // if (loadReqs == null || loadReqs.size() <= 0) {
        // return null;
        // }
        // return loadReqs.keySet().iterator().next();
        // }

        public synchronized void mergeReq(BitmapValueNode node) {
            if (node == null) {
                return;
            }

            if (loadReqs == null) {
                loadReqs = new HashMap<LoadReq, LoadReq>();
            }

            synchronized (node) {
                if (node.loadReqs == null) {
                    return;
                }
                loadReqs.putAll(node.loadReqs);
            }

        }

        public synchronized void addReq(LoadReq req) {
            if (loadReqs == null) {
                loadReqs = new HashMap<LoadReq, LoadReq>();
            }

            LoadReq oldReq = loadReqs.put(req, req);
            if (oldReq != null) {
                req.merge(oldReq);
            }
        }

        public synchronized HashSet<Pair<String, OnLoadedListener>> removeReq(
                LoadReq req) {
            if (mValue == null || loadReqs == null) {
                return null;
            }

            HashSet<Pair<String, OnLoadedListener>> result = new HashSet<Pair<String, OnLoadedListener>>();
            req = loadReqs.remove(req);
            if (req != null) {
                result.addAll(req.listeners);
            }
            return result;
        }

        public synchronized HashSet<Pair<String, OnLoadedListener>> removeFitReq() {
            if (mValue == null || loadReqs == null) {
                return null;
            }

            HashSet<Pair<String, OnLoadedListener>> result = new HashSet<Pair<String, OnLoadedListener>>();
            if (scale <= 1 && scale >= 0) {
                for (LoadReq req : loadReqs.keySet()) {
                    result.addAll(req.listeners);
                }
                loadReqs.clear();
            } else {
                ArrayList<LoadReq> ls = new ArrayList<LoadReq>();
                for (LoadReq req : loadReqs.keySet()) {
                    if (!isSizeNeedChange(req)) {
                        // loadReqs.remove(req);
                        ls.add(req);
                        result.addAll(req.listeners);
                    }
                }
                for (LoadReq loadReq : ls) {
                    loadReqs.remove(loadReq);
                }

            }
            return result;
        }

        public synchronized boolean isSizeNeedChange(LoadReq req) {

            if (mValue == null) {
                if (loadReqs == null) {
                    loadReqs = new HashMap<LoadReq, LoadReq>();
                }

                LoadReq sameReq = loadReqs.get(req);
                if (sameReq != null) {
                    sameReq.merge(req);
                }
                return sameReq == null;
            }

            // if ((req.width < 1 && req.height < 1) || (scale <= 1 && scale >=
            // 0)) {
            if (req.width < 1 && req.height < 1) {
                return false;
            }

            if (width >= req.width && height >= req.height) {
                return false;
            }

            boolean result = false;
            switch (req.type) {
                case MATRIX:
                case CENTER:
                    result = scale > 1;
                    break;
                case FIT_XY:
                case CENTER_CROP:
                    result = width < req.width || height < req.height;
                    break;
                case FIT_CENTER:
                case FIT_START:
                case FIT_END:
                case CENTER_INSIDE:
                default:
                    result = (req.width < 1 || width < req.width)
                            && (req.height < 1 || height < req.height);
                    break;

            }
            return result;
        }
    }

    public static interface OnLoadedListener {
        void onLoaded(String key, String path, boolean success);
    }

    @SuppressLint("NewApi")
    public static int getBestMaxSize(Context context) {
        int cacheSize;
        ActivityManager am = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        final int version = AndroidBuild.getVersion();
        if (version >= AndroidBuild.VER_HONEYCOMB) {
            int heapSize = am.getLargeMemoryClass() * 1024 * 1024;
            int minSize = heapSize / 8;
            int maxSize = heapSize / 4 * 3;

            cacheSize = Math.max(minSize, Math.min(maxSize, LARGE_MAX_SIZE));
        } else if (version >= AndroidBuild.VER_ECLAIR) {
            int heapSize = am.getMemoryClass() * 1024 * 1024;
            int minSize = heapSize / 8;
            int maxSize = heapSize / 2;
            cacheSize = Math.max(minSize, Math.min(maxSize, MAX_SIZE));
        } else {
            cacheSize = MAX_SIZE;
        }

        Log.d(LOG_TAG, "ImageCache MaxSize set to " + cacheSize);
        return cacheSize;
    }

    public ImageCache(Context context) {
        super(getBestMaxSize(context));

        NATIVE_SIZE = maxSize() / 80;
        mContext = context;
        mReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, final Intent intent) {
                getBgHandler().post(new Runnable() {

                    @Override
                    public void run() {

                    }
                });
            }
        };
        IntentFilter filter = new IntentFilter();

        context.registerReceiver(mReceiver, filter);
    }

    public Drawable getDrawable(final String key, final String loadPath,
            OnLoadedListener listener, final int width, final int height,
            final ScaleType type) {
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(loadPath)) {
            return null;
        }
        Bitmap bmp = getBitmap(key, loadPath, listener, width, height, type);
        return bmp == null ? null : new BitmapDrawable(bmp);
    }

    public Drawable fetchDrawable(String key, int width, int height,
            ScaleType type) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        Bitmap bmp = fetchBitmap(key, width, height, type);
        return bmp == null ? null : new BitmapDrawable(bmp);
    }

    public Bitmap fetchBitmap(String key, int width, int height, ScaleType type) {
        return fetchBitmap(key, width, height, type, true);
    }

    public Bitmap fetchBitmap(String key, int width, int height,
            ScaleType type, boolean allowLowQuality) {
        return fetchBitmap(key, null, null, width, height, type,
                allowLowQuality, false);
    }

    public Bitmap fetchBitmap(final String key, String path,
            OnLoadedListener listener, final int width, final int height,
            ScaleType type, boolean allowLowQuality, boolean includeFileCache) {
        ValueNode<Bitmap> node = getEntry(key);
        Bitmap bitmap = node == null ? null : node.mValue;

        BitmapValueNode bpNode = null;
        if (node instanceof BitmapValueNode) {
            bpNode = (BitmapValueNode) node;
        }

        final LoadReq req = new LoadReq(path, width, height, type, listener);
        if (bpNode == null || bpNode.isSizeNeedChange(req)) {
            if (!allowLowQuality) {
                bitmap = null;
            }

            if (bpNode == null) {
                BitmapValueNode newNode = getNode(null);
                newNode.addReq(req);
                newNode.width = width;
                newNode.height = height;
                // putEntry(String.format("%s_w%d_h%d", key, width, height),
                // newNode);
                putEntry(key, newNode);
            } else {
                bpNode.addReq(req);
            }

            // load image
            getBgHandler().postAtFrontOfQueue(new Runnable() {
                @Override
                public void run() {
                    String path = null;
//                    try {
//                        path = mThumbService.getThumb(key, width, height);
//                    } catch (Exception e) {
//                        Log.w(LOG_TAG, "Failed get thumb to service.");
//                    }
                    File file = null;
                    if (!TextUtils.isEmpty(path)) {
                        file = new File(path);
                    }
                    if (file != null && file.exists()) {
                        _onDataLoaded(key, file, req);
                    }
                }
            });
        }

        return bitmap;
    }
    

    public Bitmap getBitmap(String key, String loadPath,
            OnLoadedListener listener, int width, int height, ScaleType type) {
        return getBitmap(key, loadPath, listener, width, height, type, true);
    }

    public Bitmap getBitmap(final String key, final String loadPath,
            OnLoadedListener listener, final int width, final int height,
            final ScaleType type, boolean allowLowQuality) {
        // ValueNode<Bitmap> node = getEntry(String.format("%s_w%d_h%d", key,
        // width, height));
        ValueNode<Bitmap> node = getEntry(key);
        Bitmap bitmap = node == null ? null : node.mValue;

        BitmapValueNode bpNode = null;
        if (node instanceof BitmapValueNode) {
            bpNode = (BitmapValueNode) node;
        }

        final LoadReq req = new LoadReq(loadPath, width, height, type, listener);
        if (bpNode == null || bpNode.isSizeNeedChange(req)) {
            if (!allowLowQuality) {
                bitmap = null;
            }

            if (bpNode == null) {
                BitmapValueNode newNode = getNode(null);
                newNode.addReq(req);
                newNode.width = width;
                newNode.height = height;
                // putEntry(String.format("%s_w%d_h%d", key, width, height),
                // newNode);
                putEntry(key, newNode);
            } else {
                bpNode.addReq(req);
            }

            // load image
            getBgHandler().postAtFrontOfQueue(new Runnable() {
                @Override
                public void run() {
                    String path = null;
                    try {
//                        path = mThumbService.getThumb(key, width, height);
                    } catch (Exception e) {
                        Log.w(LOG_TAG, "Failed get thumb to service.");
                    }
                    File file = null;
                    if (!TextUtils.isEmpty(path)) {
                        file = new File(path);
                    }
                    if (file != null && file.exists()) {
                        _onDataLoaded(key, file, req);
                    } else {
//                        try {
//                            Bundle extra = new Bundle();
//                            extra.putSerializable(EXTRA_TYPE, type);
//                            mThumbService.loadThumb(key, loadPath, width,
//                                    height, extra, true);
//                        } catch (RemoteException e) {
//                            Log.w(LOG_TAG, "Failed call load to service.");
//                        }
                    }
                }
            });
        }

        return bitmap;
    }

    @Override
    protected void entryRemoved(boolean evicted, String key,
            ValueNode<Bitmap> oldNode, ValueNode<Bitmap> newNode) {
        if (evicted || newNode == null || oldNode == null
                || !(oldNode instanceof BitmapValueNode)
                || !(newNode instanceof BitmapValueNode)) {
            return;
        }
        BitmapValueNode oldN = (BitmapValueNode) oldNode;
        BitmapValueNode newN = (BitmapValueNode) newNode;

        if (oldN.mValue != null) {
            if (newN.mValue == null
                    || newN.scale > oldN.scale
                    || (newN.scale > 0 && newN.scale == oldN.scale
                            && newN.height == oldN.height && newN.width == oldN.width)) {
                newN.copyData(oldN);
            }
        }

        newN.mergeReq(oldN);
        HashSet<Pair<String, OnLoadedListener>> listeners = newN.removeFitReq();
        notifyListener(listeners, key, true);
    }

    public void safePut(String key, File file) {
        safePut(key, file, -1, -1, ScaleType.MATRIX);
    }

    public void safePut(String key, File file, int width, int height,
            ScaleType type) {
        onDataLoaded(key, file, new LoadReq(null, width, height, type, null));
    }


	protected void onDataLoaded(final String key, final File file,
            final LoadReq req) {
        getBgHandler().post(new Runnable() {
            @Override
            public void run() {
                _onDataLoaded(key, file, req);
            }
        });
    }

    private void _onDataLoaded(String key, File file, LoadReq req) {
        // key = String.format("%s_w%d_h%d", key, req.width, req.height);
        if (file == null) {
            safeRemove(key, req);
            return;
        }

        BitmapFactory.Options preloadOpt = new BitmapFactory.Options();
        preloadOpt.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), preloadOpt);

        final int imageHeight = preloadOpt.outHeight;
        final int imageWidth = preloadOpt.outWidth;

        if (imageWidth < 0 || imageHeight < 0) {
            safeRemove(key, req);
            return;
        }

        int scale = 1;
        if (req != null && (req.width > 0 || req.height > 0)) {
            scale = computeBestScale(imageWidth, imageHeight, req.width,
                    req.height, req.type);
        }
        scale = getPowerSize(scale);
        final int targetWidth = imageWidth / scale;
        final int targetHeight = imageHeight / scale;
        boolean inNative = targetWidth * targetHeight * BYTE_PRE_PIX >= NATIVE_SIZE;

        BitmapFactory.Options loadOpt = new BitmapFactory.Options();
        loadOpt.inSampleSize = scale;
        if (inNative) {
            try {
                loadOpt.getClass().getField("inNativeAlloc")
                        .setBoolean(loadOpt, true);
            } catch (Exception e) {
                // Log.w(LOG_TAG, "Failed set inNativeAlloc", e);
                inNative = false;
            }
        }
        Bitmap bm = null;
        try {
            bm = BitmapFactory.decodeFile(file.getAbsolutePath(), loadOpt);
        } catch (Throwable t) {
            try {
                trimToSize(maxSize() >> 1);
                System.gc();
                bm = BitmapFactory.decodeFile(file.getAbsolutePath(), loadOpt);
            } catch (Throwable t1) {
                Log.w(LOG_TAG, "Failed decode bitmap file.", t1);
            }
        }

        if (bm == null) {
            safeRemove(key, req);
            return;
        }
        BitmapValueNode node = getNode(bm);
        node.width = req.width;
        node.height = req.height;
        node.inNative = inNative;
        node.scale = loadOpt.inSampleSize;
        putEntry(key, node);
    }

    private static int getPowerSize(int size) {
        int i = 1;
        while (i <= size) {
            i = i << 1;
        }
        return i >> 1;
    }

    private void notifyListener(
            final HashSet<Pair<String, OnLoadedListener>> listeners,
            final String key, final boolean success) {
        if (listeners == null) {
            return;
        }

        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                _notifyListener(listeners, key, success);
            }
        });
    }

    private static void _notifyListener(
            HashSet<Pair<String, OnLoadedListener>> listeners, String key,
            boolean success) {
        for (Pair<String, OnLoadedListener> listenerEntry : listeners) {
            try {
                String path = listenerEntry.first;
                OnLoadedListener listener = listenerEntry.second;

                listener.onLoaded(key, path, success);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Meet Exception when notify listener", e);
            }
        }
    }

    private void safeRemove(String key, LoadReq req) {
        ValueNode<Bitmap> node = getEntry(key);
        if (node == null || node.mValue == null) {
            remove(key);
            return;
        }

        if (!(node instanceof BitmapValueNode)) {
            return;
        }
        BitmapValueNode bpNode = (BitmapValueNode) node;
        HashSet<Pair<String, OnLoadedListener>> listeners = bpNode
                .removeReq(req);
        notifyListener(listeners, key, false);
    }

    private static int computeBestScale(int imageWidth, int imageHeight,
            int width, int height, ScaleType type) {
        int result = 1;
        switch (type) {
            case MATRIX:
            case CENTER:
                result = 1;
                break;
            case FIT_XY:
            case CENTER_CROP: {
                float xScale = width > 0 ? (float) imageWidth / width
                        : Float.MAX_VALUE;
                float yScale = height > 0 ? (float) imageHeight / height
                        : Float.MAX_VALUE;
                int scale = (int) Math.min(xScale, yScale);
                result = Math.max(1, scale);
                break;
            }
            case FIT_CENTER:
            case FIT_START:
            case FIT_END:
            case CENTER_INSIDE:
            default: {
                float xScale = width > 0 ? (float) imageWidth / width : 1;
                float yScale = height > 0 ? (float) imageHeight / height : 1;
                int scale = (int) Math.max(xScale, yScale);
                result = Math.max(1, scale);
                break;
            }
        }
        return result;
    }

    @Override
    protected ValueNode<Bitmap> create(String key) {
        return super.create(key);
    }

    @Override
    protected BitmapValueNode getNode(Bitmap value) {
        BitmapValueNode result = new BitmapValueNode();
        if (value == null) {
            return result;
        }

        result.setValue(value);
        result.width = value.getWidth();
        result.height = value.getHeight();
        return result;
    }

    @Override
    protected int sizeOf(String key, ValueNode<Bitmap> node) {
        // Native data bytes will always 16KB
        if (((BitmapValueNode) node).inNative) {
            return 16 * 1024;
        }
        Bitmap bitmap = (node == null ? null : node.mValue);
        // int count = (bitmap == null ? 0 : bitmap.getByteCount());
        int rowSize = (bitmap == null ? 0 : bitmap.getRowBytes()
                * bitmap.getHeight());
        // return count + rowSize;
        return rowSize;
    }

    private Handler getBgHandler() {
        if (mBgHandler == null) {
            if (mBgThread == null || !mBgThread.isAlive()) {
                mBgThread = new HandlerThread("Image cache Thread");
                mBgThread.setDaemon(true);
                mBgThread.start();
            }

            mBgHandler = new Handler(mBgThread.getLooper());
        }
        return mBgHandler;
    }

    public void release() {
        if (mReceiver != null) {
            mContext.unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }
    
    @Override
    public void trimToSize(int maxSize){
        super.trimToSize(maxSize);
    }
}
