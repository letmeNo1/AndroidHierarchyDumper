package nico.dump_hierarchy;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.MotionEvent;
import androidx.core.view.InputDeviceCompat;
import androidx.test.uiautomator.Configurator;

/* loaded from: classes9.dex */
public class TouchController {
    private static final boolean DEBUG;
    private static final String LOG_TAG;
    private static final int MOTION_EVENT_INJECTION_DELAY_MILLIS = 5;
    private long mDownTime;
    private final Instrumentation mInstrumentation;
    private final KeyCharacterMap mKeyCharacterMap = KeyCharacterMap.load(-1);

    static {
        String simpleName = TouchController.class.getSimpleName();
        LOG_TAG = simpleName;
        DEBUG = android.util.Log.isLoggable(simpleName, 3);
    }

    public TouchController(Instrumentation instrumentation) {
        this.mInstrumentation = instrumentation;
    }

    public boolean isScreenOn() {
        PowerManager pm = (PowerManager) getContext().getSystemService("power");
        return pm.isScreenOn();
    }

    private boolean injectEventSync(InputEvent event) {
        return getUiAutomation().injectInputEvent(event, true);
    }

    public boolean touchDown(float x, float y) {
        if (DEBUG) {
            android.util.Log.d(LOG_TAG, "touchDown (" + x + ", " + y + ")");
        }
        long jUptimeMillis = SystemClock.uptimeMillis();
        this.mDownTime = jUptimeMillis;
        MotionEvent event = getMotionEvent(jUptimeMillis, jUptimeMillis, 0, x, y);
        return injectEventSync(event);
    }

    public boolean touchUp(float x, float y) {
        if (DEBUG) {
            android.util.Log.d(LOG_TAG, "touchUp (" + x + ", " + y + ")");
        }
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = getMotionEvent(this.mDownTime, eventTime, 1, x, y);
        this.mDownTime = 0L;
        return injectEventSync(event);
    }

    public boolean touchMove(float x, float y) {
        if (DEBUG) {
            android.util.Log.d(LOG_TAG, "touchMove (" + x + ", " + y + ")");
        }
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = getMotionEvent(this.mDownTime, eventTime, 2, x, y);
        return injectEventSync(event);
    }

    private static MotionEvent getMotionEvent(long downTime, long eventTime, int action, float x, float y) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = Configurator.getInstance().getToolType();
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.pressure = 1.0f;
        coords.size = 1.0f;
        coords.x = x;
        coords.y = y;
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, 1, new MotionEvent.PointerProperties[]{properties}, new MotionEvent.PointerCoords[]{coords}, 0, 0, 1.0f, 1.0f, 0, 0, InputDeviceCompat.SOURCE_TOUCHSCREEN, 0);
        return event;
    }

    public boolean performMultiPointerGesture(MotionEvent.PointerCoords[]... touches) {
        if (touches.length < 2) {
            throw new IllegalArgumentException("Must provide coordinates for at least 2 pointers");
        }
        int maxSteps = 0;
        for (int x = 0; x < touches.length; x++) {
            maxSteps = maxSteps < touches[x].length ? touches[x].length : maxSteps;
        }
        int x2 = touches.length;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[x2];
        MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[touches.length];
        for (int x3 = 0; x3 < touches.length; x3++) {
            MotionEvent.PointerProperties prop = new MotionEvent.PointerProperties();
            prop.id = x3;
            prop.toolType = Configurator.getInstance().getToolType();
            properties[x3] = prop;
            pointerCoords[x3] = touches[x3][0];
        }
        long downTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), 0, 1, properties, pointerCoords, 0, 0, 1.0f, 1.0f, 0, 0, InputDeviceCompat.SOURCE_TOUCHSCREEN, 0);
        boolean ret = true & injectEventSync(event);
        for (int x4 = 1; x4 < touches.length; x4++) {
            MotionEvent event2 = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), getPointerAction(5, x4), x4 + 1, properties, pointerCoords, 0, 0, 1.0f, 1.0f, 0, 0, InputDeviceCompat.SOURCE_TOUCHSCREEN, 0);
            ret &= injectEventSync(event2);
        }
        for (int i = 1; i < maxSteps - 1; i++) {
            for (int x5 = 0; x5 < touches.length; x5++) {
                if (touches[x5].length > i) {
                    pointerCoords[x5] = touches[x5][i];
                } else {
                    pointerCoords[x5] = touches[x5][touches[x5].length - 1];
                }
            }
            MotionEvent event3 = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), 2, touches.length, properties, pointerCoords, 0, 0, 1.0f, 1.0f, 0, 0, InputDeviceCompat.SOURCE_TOUCHSCREEN, 0);
            ret &= injectEventSync(event3);
            SystemClock.sleep(5L);
        }
        for (int x6 = 0; x6 < touches.length; x6++) {
            pointerCoords[x6] = touches[x6][touches[x6].length - 1];
        }
        for (int x7 = 1; x7 < touches.length; x7++) {
            MotionEvent event4 = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), getPointerAction(6, x7), x7 + 1, properties, pointerCoords, 0, 0, 1.0f, 1.0f, 0, 0, InputDeviceCompat.SOURCE_TOUCHSCREEN, 0);
            ret &= injectEventSync(event4);
        }
        android.util.Log.i(LOG_TAG, "x " + pointerCoords[0].x);
        MotionEvent event5 = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), 1, 1, properties, pointerCoords, 0, 0, 1.0f, 1.0f, 0, 0, InputDeviceCompat.SOURCE_TOUCHSCREEN, 0);
        return ret & injectEventSync(event5);
    }

    private int getPointerAction(int motionEnvent, int index) {
        return (index << 8) + motionEnvent;
    }

    UiAutomation getUiAutomation() {
        return getInstrumentation().getUiAutomation();
    }

    Context getContext() {
        return getInstrumentation().getContext();
    }

    Instrumentation getInstrumentation() {
        return this.mInstrumentation;
    }
}
