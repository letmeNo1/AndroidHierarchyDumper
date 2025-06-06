package nico.dump_hierarchy;

import androidx.test.uiautomator.UiDevice;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;

public class FileScreenshotWriter {
    private final UiDevice device;
    private static final String LOG_TAG = FileScreenshotWriter.class.getSimpleName();

    public FileScreenshotWriter(UiDevice device) {
        this.device = device;
    }

    /**
     * 直接将截图写入传入的File对象（无路径拼接）
     * @param targetFile 目标文件对象（需提前创建或指定路径）
     * @param quality JPG压缩质量 (0-100)
     * @return 是否成功写入
     */
    public boolean writeScreenshotToFile(File targetFile, int quality) {
        Bitmap screenshot = null;
        BufferedOutputStream bos = null;

        try {
            // 捕获屏幕截图（通过反射）
            screenshot = getUiAutomation().takeScreenshot();
            if (screenshot == null) {
                Log.e(LOG_TAG, "截图失败，返回null");
                return false;
            }

            // 直接使用传入的File对象写入
            bos = new BufferedOutputStream(new FileOutputStream(targetFile));
            screenshot.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            bos.flush();

            return true;
        } catch (Exception e) {
            Log.e(LOG_TAG, "写入截图失败", e);
            return false;
        } finally {
            // 释放资源
            if (screenshot != null) {
                screenshot.recycle();
            }
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    Log.w(LOG_TAG, "关闭输出流失败", e);
                }
            }
        }
    }

    /**
     * 通过反射获取UiAutomation实例
     */
    private android.app.UiAutomation getUiAutomation() throws Exception {
        Class<?> clazz = device.getClass();
        Method method = clazz.getDeclaredMethod("getUiAutomation");
        method.setAccessible(true);
        return (android.app.UiAutomation) method.invoke(device);
    }
}