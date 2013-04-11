package greendroid.image.ext;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;

public class ObjectCache<K, V> {
    private static final String LOG_TAG = "ObjectCache";

    public static class ValueNode<T> {
        protected T mValue;
        protected int mSize = 0;

        private ValueNode<T> setSize(int size) {
            mSize = size;
            return this;
        }

        public ValueNode<T> setValue(T value) {
            mValue = value;
            return this;
        }
    }

    private final int mMaxSize;
    private final int mFitSize;
    private final HashMap<K, ValueNode<V>> mMap;
    private final ArrayList<K> mList;

    /** Size of this cache in units. Not necessarily the number of elements. */
    private int mSize;

    /** Statistic counts */
    private int mPutCount;
    private int mCreateCount;
    private int mEvictionCount;
    private int mHitCount;
    private int mMissCount;

    /**
     * @param maxSize for caches that do not override {@link #sizeOf}, this is
     *            the maximum number of entries in the cache. For all other
     *            caches, this is the maximum sum of the sizes of the entries in
     *            this cache.
     */
    public ObjectCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }
        mMaxSize = maxSize;
        mFitSize = Math.max(1, mMaxSize * 4 / 5);
        mMap = new HashMap<K, ValueNode<V>>();
        mList = new ArrayList<K>();
    }

    protected synchronized ValueNode<V> getEntry(K key) {
        ValueNode<V> mapValue = mMap.get(key);
        if (mapValue != null) {
            mList.remove(key);
            mList.add(0, key);
            mHitCount++;
            return mapValue;
        }
        mMissCount++;
        return null;
    }

    protected ValueNode<V> putEntry(K key, ValueNode<V> node) {
        ValueNode<V> previous;
        synchronized (this) {
            mPutCount++;
            previous = mMap.put(key, node);
            mList.remove(key);
            mList.add(0, key);
            if (previous != null) {
                mSize -= previous.mSize;
                entryRemoved(false, key, previous, node);
            }

            node.setSize(safeSizeOf(key, node));
            mSize += node.mSize;
        }

        trimToSize(mMaxSize);
        return previous;
    }

    /**
     * Returns the value for {@code key} if it exists in the cache or can be
     * created by {@code #create}. If a value was returned, it is moved to the
     * head of the queue. This returns null if a value is not cached and cannot
     * be created.
     */
    public final V get(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        ValueNode<V> mapValue = getEntry(key);
        if (mapValue != null) {
            return mapValue.mValue;
        }

        /*
         * Attempt to create a value. This may take a long time, and the map may
         * be different when create() returns. If a conflicting value was added
         * to the map while create() was working, we leave that value in the map
         * and release the created value.
         */
        ValueNode<V> createdValue = create(key);
        if (createdValue == null) {
            return null;
        }

        synchronized (this) {
            mCreateCount++;
            mMap.put(key, createdValue);
            mList.remove(key);
            mList.add(0, key);
            createdValue.setSize(safeSizeOf(key, createdValue));
            mSize += createdValue.mSize;
        }

        trimToSize(mMaxSize);
        return createdValue.mValue;
    }

    /**
     * Caches {@code value} for {@code key}. The value is moved to the head of
     * the queue.
     * 
     * @return the previous value mapped by {@code key}.
     */
    public final V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("key == null || value == null");
        }

        ValueNode<V> node = getNode(value);
        ValueNode<V> previous = putEntry(key, node);
        return previous == null ? null : previous.mValue;
    }

    /**
     * @param maxSize the maximum size of the cache before returning. May be -1
     *            to evict even 0-sized elements.
     */
    protected void trimToSize(int maxSize) {
        int limit = 0;
        if (maxSize >= mMaxSize && mSize > mMaxSize) {
            maxSize = mFitSize;
            limit = 1;
        }
        if (maxSize < 0) {
            maxSize = 0;
        }

        ArrayList<K> list = mList;
        HashMap<K, ValueNode<V>> map = mMap;
        boolean cleanZeroSize = false;
        while (true) {
            K key;
            ValueNode<V> node;
            synchronized (this) {
                if (mSize < 0 || (map.isEmpty() && mSize != 0)) {
                    throw new IllegalStateException(getClass().getName()
                            + ".sizeOf() is reporting inconsistent results!");
                }

                if (mSize <= maxSize || map.size() <= limit) {
                    break;
                }

                int removeIndex = -1;
                if (!cleanZeroSize) {
                    for (int i = list.size() - 1; i >= 0; i--) {
                        key = list.get(i);
                        node = map.get(key);
                        if (node!=null && node.mSize > 0) {
                            removeIndex = i;
                            break;
                        }
                    }
                }
                if (removeIndex < 0) {
                    cleanZeroSize = true;
                    removeIndex = list.size() - 1;
                }

                key = list.remove(removeIndex);
                node = map.remove(key);
                if (node != null) {
                    mSize -= node.mSize;
                }
                mEvictionCount++;
            }

            entryRemoved(true, key, node, null);
        }
    }

    /**
     * Removes the entry for {@code key} if it exists.
     * 
     * @return the previous value mapped by {@code key}.
     */
    public final V remove(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        ValueNode<V> previous;
        synchronized (this) {
            previous = mMap.remove(key);
            mList.remove(key);
            if (previous != null) {
                mSize -= previous.mSize;
            }
        }

        if (previous != null) {
            entryRemoved(false, key, previous, null);
        }

        return previous == null ? null : previous.mValue;
    }

    /**
     * Called after a cache miss to compute a value for the corresponding key.
     * Returns the computed value or null if no value can be computed. The
     * default implementation returns null.
     * <p>
     * The method is called without synchronization: other threads may access
     * the cache while this method is executing.
     * <p>
     * If a value for {@code key} exists in the cache when this method returns,
     * the created value will be released with {@link #entryRemoved} and
     * discarded. This can occur when multiple threads request the same key at
     * the same time (causing multiple values to be created), or when one thread
     * calls {@link #put} while another is creating a value for the same key.
     */
    protected ValueNode<V> create(K key) {
        return null;
    }

    protected ValueNode<V> getNode(V value) {
        return new ValueNode<V>().setValue(value);
    }

    /**
     * Called for entries that have been evicted or removed. This method is
     * invoked when a value is evicted to make space, removed by a call to
     * {@link #remove}, or replaced by a call to {@link #put}. The default
     * implementation does nothing.
     * <p>
     * The method is called without synchronization: other threads may access
     * the cache while this method is executing.
     * 
     * @param evicted true if the entry is being removed to make space, false if
     *            the removal was caused by a {@link #put} or {@link #remove}.
     * @param newValue the new value for {@code key}, if it exists. If non-null,
     *            this removal was caused by a {@link #put}. Otherwise it was
     *            caused by an eviction or a {@link #remove}.
     */
    protected void entryRemoved(boolean evicted, K key, ValueNode<V> oldNode,
            ValueNode<V> newNode) {
    }

    private int safeSizeOf(K key, ValueNode<V> node) {

        int result = 0;
        try {
            result = sizeOf(key, node);
        } catch (Throwable t) {
            Log.w(LOG_TAG, "Meet Exceptin when compute size:" + key + "="
                    + node, t);
        }
        if (result < 0) {
            throw new IllegalStateException("Negative size: " + key + "="
                    + node);
        }
        return result;
    }

    /**
     * Returns the size of the entry for {@code key} and {@code value} in
     * user-defined units. The default implementation returns 1 so that size is
     * the number of entries and max size is the maximum number of entries.
     * <p>
     * An entry's size must not change while it is in the cache.
     */
    protected int sizeOf(K key, ValueNode<V> node) {
        return 1;
    }

    /**
     * Clear the cache, calling {@link #entryRemoved} on each removed entry.
     */
    public final void cleanAll() {
        trimToSize(-1); // -1 will evict 0-sized elements
    }

    /**
     * For caches that do not override {@link #sizeOf}, this returns the number
     * of entries in the cache. For all other caches, this returns the sum of
     * the sizes of the entries in this cache.
     */
    public synchronized final int size() {
        return mSize;
    }

    /**
     * For caches that do not override {@link #sizeOf}, this returns the maximum
     * number of entries in the cache. For all other caches, this returns the
     * maximum sum of the sizes of the entries in this cache.
     */
    public synchronized final int maxSize() {
        return mMaxSize;
    }

    /**
     * Returns the number of times {@link #get} returned a value that was
     * already present in the cache.
     */
    public synchronized final int hitCount() {
        return mHitCount;
    }

    /**
     * Returns the number of times {@link #get} returned null or required a new
     * value to be created.
     */
    public synchronized final int missCount() {
        return mMissCount;
    }

    /**
     * Returns the number of times {@link #create(Object)} returned a value.
     */
    public synchronized final int createCount() {
        return mCreateCount;
    }

    /**
     * Returns the number of times {@link #put} was called.
     */
    public synchronized final int putCount() {
        return mPutCount;
    }

    /**
     * Returns the number of values that have been evicted.
     */
    public synchronized final int evictionCount() {
        return mEvictionCount;
    }

    /**
     * Returns a copy of the current contents of the cache, ordered from least
     * recently accessed to most recently accessed.
     */
    public synchronized final Map<K, ValueNode<V>> snapshot() {
        return new HashMap<K, ValueNode<V>>(mMap);
    }

    @Override
    public synchronized final String toString() {
        int accesses = mHitCount + mMissCount;
        int hitPercent = accesses != 0 ? (100 * mHitCount / accesses) : 0;
        return String.format(
                "LruCache[maxSize=%d,hits=%d,misses=%d,hitRate=%d%%]",
                mMaxSize, mHitCount, mMissCount, hitPercent);
    }

}
