
package greendroid.util;

import android.os.Build;

import junit.framework.AssertionFailedError;

public class AndroidBuild {
    /**
     * October 2008: The original, first, version of Android.
     */
    public static final int VER_BASE = 1;

    /**
     * February 2009: First Android update, officially called 1.1.
     */
    public static final int VER_BASE_1_1 = 2;

    /**
     * May 2009: Android 1.5.
     */
    public static final int VER_CUPCAKE = 3;

    /**
     * September 2009: Android 1.6.
     */
    public static final int VER_DONUT = 4;

    /**
     * November 2009: Android 2.0
     */
    public static final int VER_ECLAIR = 5;

    /**
     * December 2009: Android 2.0.1
     */
    public static final int VER_ECLAIR_0_1 = 6;

    /**
     * January 2010: Android 2.1
     */
    public static final int VER_ECLAIR_MR1 = 7;

    /**
     * June 2010: Android 2.2
     */
    public static final int VER_FROYO = 8;

    /**
     * November 2010: Android 2.3
     */
    public static final int VER_GINGERBREAD = 9;

    /**
     * February 2011: Android 2.3.3.
     */
    public static final int VER_GINGERBREAD_MR1 = 10;

    /**
     * February 2011: Android 3.0.
     */
    public static final int VER_HONEYCOMB = 11;

    /**
     * May 2011: Android 3.1.
     */
    public static final int VER_HONEYCOMB_MR1 = 12;

    /**
     * June 2011: Android 3.2.
     */
    public static final int VER_HONEYCOMB_MR2 = 13;

    /**
     * October 2011: Android 4.0.
     */
    public static final int VER_ICE_CREAM_SANDWICH = 14;

    /**
     * Android 4.0.3.
     */
    public static final int VER_ICE_CREAM_SANDWICH_MR1 = 15;

    /**
     * Android 4.1.
     */
    public static final int VER_JELLY_BEAN = 16;

    public static final int getVersion() {
        int result = 0;
        try {
            String version = Build.VERSION.SDK;
            result = Integer.valueOf(version);
        } catch (Exception e) {
            // ignore
        }
        return result;
    }

    public static final void assertVersion(int version) {
        int current = getVersion();
        if (current < version) {
            throw new AssertionFailedError(
                    String.format(
                            "Not support on current device. The limit version is %d, current is %d.",
                            version, current));
        }
    }
}
