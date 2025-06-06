package nico.dump_hierarchy;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ScreenCaptureHelper {
    private static final int REQUEST_CODE = 1001;
    private static MediaProjection mediaProjection;
    private static VirtualDisplay virtualDisplay;
    private static ImageReader imageReader;
    private static int screenWidth, screenHeight, screenDensity;
    private static final Object lock = new Object();

    /**
     * 初始化屏幕捕获
     */
    public static void initScreenCapture() throws Exception {
        Instrumentation instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();

        // 获取屏幕参数（兼容所有 Android 版本）
        getScreenMetrics(context);

        // 获取 MediaProjectionManager
        MediaProjectionManager manager = (MediaProjectionManager)
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // 创建权限请求 Intent
        Intent permissionIntent = manager.createScreenCaptureIntent();

        // 启动 Activity 并模拟用户授权
        final CountDownLatch latch = new CountDownLatch(1);
        final Intent[] resultData = new Intent[1];

        new Handler(Looper.getMainLooper()).post(() -> {
            Activity activity = (Activity) context;
            Activity result = instrumentation.startActivitySync(permissionIntent);
            latch.countDown();
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new RuntimeException("Screen capture permission timeout");
        }

        if (resultData[0] == null) {
            throw new RuntimeException("Screen capture permission denied");
        }

        // 初始化 MediaProjection
        mediaProjection = manager.getMediaProjection(Activity.RESULT_OK, resultData[0]);

        // 创建 ImageReader
        imageReader = ImageReader.newInstance(
                screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

        // 创建 VirtualDisplay
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
    }

    /**
     * 兼容所有 Android 版本的屏幕参数获取方法
     */
    private static void getScreenMetrics(Context context) {
        try {
            // 尝试使用 UiDevice.getDisplayMetrics() (API 30+)
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            Method method = device.getClass().getMethod("getDisplayMetrics");
            DisplayMetrics metrics = (DisplayMetrics) method.invoke(device);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            screenDensity = metrics.densityDpi;
            return;
        } catch (Exception e) {
            // 回退到 WindowManager 方法
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();

            DisplayMetrics metrics = new DisplayMetrics();
            display.getMetrics(metrics);
            screenDensity = metrics.densityDpi;

            // 获取真实尺寸（包括导航栏）
            try {
                Point realSize = new Point();
                Method getRealSize = Display.class.getMethod("getRealSize", Point.class);
                getRealSize.invoke(display, realSize);
                screenWidth = realSize.x;
                screenHeight = realSize.y;
            } catch (Exception ex) {
                // 再回退到常规尺寸（可能不包括导航栏）
                screenWidth = metrics.widthPixels;
                screenHeight = metrics.heightPixels;
            }
        }
    }

    /**
     * 捕获当前屏幕
     */
    public static Bitmap captureScreen() {
        synchronized (lock) {
            Image image = imageReader.acquireLatestImage();
            if (image == null) {
                return null;
            }

            try {
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * screenWidth;

                Bitmap bitmap = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);

                return Bitmap.createBitmap(
                        bitmap, 0, 0, screenWidth, screenHeight);
            } finally {
                image.close();
            }
        }
    }

    /**
     * 释放资源
     */
    public static void release() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }
}