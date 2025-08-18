package nico.dump_hierarchy;

import static androidx.test.InstrumentationRegistry.getContext;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class HierarchyTest extends AccessibilityService {
    private static final String TAG = "hank_auto";
    private String path;
    private AccessibilityEvent lastWindowChangeEvent = null;
    private ServerSocket serverSocket;
    private TouchController touchController;  // 添加TouchController实例
    private final AtomicBoolean uiChanged = new AtomicBoolean(false);

    private void init() {
        Configurator.getInstance().setWaitForIdleTimeout(1);
        Configurator.getInstance().setWaitForSelectorTimeout(1);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File filesDir = context.getFilesDir();
        touchController = new TouchController(InstrumentationRegistry.getInstrumentation());

        path = filesDir.getPath();
    }

    private final UiAutomation.AccessibilityEventFilter checkWindowUpdate = event -> {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            lastWindowChangeEvent = event;
            return true;
        }
        return false;
    };

    @Test
    public void TestCase1() {
        init();
        startWatchingUiChanges();
        startHttpServer();
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void startHttpServer() {
        Thread serverThread = new Thread(() -> {
            try {
                Bundle arguments = InstrumentationRegistry.getArguments();
                int port = arguments.getInt("port", 8000);
                InetAddress serverAddress = InetAddress.getByName("localhost");
                serverSocket = new ServerSocket(port, 0, serverAddress);
                Log.i(TAG, "HTTP Server running on " + serverAddress.getHostAddress() + ":" + port);

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleClient(clientSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();
    }

    private void handleClient(Socket socket) {
        try (InputStream is = socket.getInputStream();
             OutputStream os = socket.getOutputStream()) {  // os在此块内有效

            Map<String, String> request = parseHttpRequest(is);
            String method = request.get("method");
            String path = request.get("path");
            Map<String, String> params = parseQueryParams(request.get("query"));

            try {  // 新增内层try块
                switch (path) {
                    case "/status": // 状态检查路由
                        handleHealthRequest(os);
                        break;
                    case "/dump":
                        handleDumpRequest(os, params);
                        break;
                    case "/screenshot":
                        handlePicRequest(os, params);
                        break;
                    case "/is_ui_change":
                        handleIsUiChangeRequest(os);
                        break;
                    case "/find_element":
                        handleFindElementRequest(os, params);
                        break;
                    case "/find_elements":
                        handleFindElementsRequest(os, params);
                        break;
                    case "/get_root":
                        handleGetRootRequest(os);
                        break;
                    case "/click":
                        handleClickRequest(os,params);
                        break;
                    case "/touch_down": // 新增：单点按下
                        handleTouchDownRequest(os, params);
                        break;
                    case "/touch_up": // 新增：单点抬起
                        handleTouchUpRequest(os, params);
                        break;
                    case "/touch_move": // 新增：滑动
                        handleTouchMoveRequest(os, params);
                        break;
                    default:
                        sendResponse(os, 404, "text/plain", "Not Found");
                }
            } catch (Exception e) {  // 捕获所有异常
                sendResponse(os, 500, "text/plain", "Internal Server Error");
                e.printStackTrace();
            }

        } catch (IOException e) {
            // 处理socket创建异常
            e.printStackTrace();
        }
    }

    private void handleHealthRequest(OutputStream os) throws IOException {
        String responseText = "server is running";
        sendResponse(os, 200, "text/plain", responseText);
    }

    /**
     * 处理点击请求：通过坐标直接调用TouchController执行点击
     */
    private void handleClickRequest(OutputStream os, Map<String, String> params) throws IOException {
        // 获取坐标参数（从请求参数中解析x和y）
        String xStr = params.get("x");
        String yStr = params.get("y");

        // 参数校验
        if (xStr == null || yStr == null) {
            sendResponse(os, 400, "text/plain", "Missing parameters: x and y are required");
            return;
        }

        try {
            // 转换坐标为浮点数
            float x = Float.parseFloat(xStr);
            float y = Float.parseFloat(yStr);

            // 调用TouchController执行点击（按下->抬起）
            boolean downSuccess = touchController.touchDown(x, y);
            // 增加微小延迟，模拟真实点击的按下-抬起间隔
            SystemClock.sleep(50);
            boolean upSuccess = touchController.touchUp(x, y);

            // 根据执行结果返回响应
            if (downSuccess && upSuccess) {
                sendResponse(os, 200, "text/plain", "Click at (" + x + ", " + y + ") success");
            } else {
                sendResponse(os, 500, "text/plain", "Click failed (down: " + downSuccess + ", up: " + upSuccess + ")");
            }
        } catch (NumberFormatException e) {
            // 处理坐标格式错误
            sendResponse(os, 400, "text/plain", "Invalid coordinates: x and y must be numbers");
        } catch (Exception e) {
            // 处理其他异常
            sendResponse(os, 500, "text/plain", "Click error: " + e.getMessage());
        }
    }


    private Map<String, String> parseHttpRequest(InputStream is) throws IOException {
        Map<String, String> request = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        String requestLine = reader.readLine();
        if (requestLine == null) return request;

        String[] parts = requestLine.split(" ");
        if (parts.length >= 2) {
            request.put("method", parts[0]);
            request.put("path", parts[1]);
            if (parts.length >= 3) request.put("protocol", parts[2]);
        }

        // 对 path 进行空值检查
        String path = request.get("path");
        if (path != null) {
            int qIndex = path.indexOf('?');
            if (qIndex != -1) {
                request.put("query", path.substring(qIndex + 1));
                request.put("path", path.substring(0, qIndex));
            } else {
                request.put("query", "");
            }
        } else {
            request.put("query", "");
        }

        while (true) {
            String line = reader.readLine();
            if (line == null || line.isEmpty()) break;
        }

        return request;
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx != -1) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    params.put(key, value);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return params;
    }

    /**
     * 处理单点按下请求（仅按下不抬起）
     */
    private void handleTouchDownRequest(OutputStream os, Map<String, String> params) throws IOException {
        // 解析坐标参数
        String xStr = params.get("x");
        String yStr = params.get("y");
        if (xStr == null || yStr == null) {
            sendResponse(os, 400, "text/plain", "Missing parameters: x and y are required");
            return;
        }

        try {
            float x = Float.parseFloat(xStr);
            float y = Float.parseFloat(yStr);
            // 调用TouchController执行按下
            boolean success = touchController.touchDown(x, y);
            if (success) {
                sendResponse(os, 200, "text/plain", "TouchDown at (" + x + ", " + y + ") success");
            } else {
                sendResponse(os, 500, "text/plain", "TouchDown failed");
            }
        } catch (NumberFormatException e) {
            sendResponse(os, 400, "text/plain", "Invalid coordinates: x and y must be numbers");
        } catch (Exception e) {
            sendResponse(os, 500, "text/plain", "TouchDown error: " + e.getMessage());
        }
    }

    /**
     * 处理单点抬起请求（需与之前的touchDown对应）
     */
    private void handleTouchUpRequest(OutputStream os, Map<String, String> params) throws IOException {
        // 解析坐标参数（需与按下坐标一致，确保抬起对应点）
        String xStr = params.get("x");
        String yStr = params.get("y");
        if (xStr == null || yStr == null) {
            sendResponse(os, 400, "text/plain", "Missing parameters: x and y are required");
            return;
        }

        try {
            float x = Float.parseFloat(xStr);
            float y = Float.parseFloat(yStr);
            // 调用TouchController执行抬起
            boolean success = touchController.touchUp(x, y);
            if (success) {
                sendResponse(os, 200, "text/plain", "TouchUp at (" + x + ", " + y + ") success");
            } else {
                sendResponse(os, 500, "text/plain", "TouchUp failed");
            }
        } catch (NumberFormatException e) {
            sendResponse(os, 400, "text/plain", "Invalid coordinates: x and y must be numbers");
        } catch (Exception e) {
            sendResponse(os, 500, "text/plain", "TouchUp error: " + e.getMessage());
        }
    }

    /**
     * 处理滑动请求（从起点(x1,y1)滑动到终点(x2,y2)，支持自定义滑动时长）
     */
    /**
     * 处理触摸移动请求（基于MotionEvent的连续移动，需配合touchDown和touchUp使用）
     */
    private void handleTouchMoveRequest(OutputStream os, Map<String, String> params) throws IOException {
        // 解析当前移动坐标参数
        String xStr = params.get("x");
        String yStr = params.get("y");

        // 参数校验
        if (xStr == null || yStr == null) {
            sendResponse(os, 400, "text/plain", "Missing parameters: x and y are required");
            return;
        }

        try {
            // 转换坐标为浮点数
            float x = Float.parseFloat(xStr);
            float y = Float.parseFloat(yStr);

            // 调用TouchController执行移动（基于MotionEvent的单点连续移动）
            boolean success = touchController.touchMove(x, y);

            // 根据执行结果返回响应
            if (success) {
                sendResponse(os, 200, "text/plain", "TouchMove to (" + x + ", " + y + ") success");
            } else {
                sendResponse(os, 500, "text/plain", "TouchMove failed");
            }
        } catch (NumberFormatException e) {
            // 处理坐标格式错误
            sendResponse(os, 400, "text/plain", "Invalid coordinates: x and y must be numbers");
        } catch (Exception e) {
            // 处理其他异常
            sendResponse(os, 500, "text/plain", "TouchMove error: " + e.getMessage());
        }
    }


    private void handleDumpRequest(OutputStream os, Map<String, String> params) throws IOException {
        boolean compressed = Boolean.parseBoolean(params.getOrDefault("compressed", "false"));
        File dumpFile = dumpWindowHierarchy(compressed, "dump.xml");
        sendFileResponse(os, "application/xml", dumpFile);
    }

    private void handlePicRequest(OutputStream os, Map<String, String> params) throws IOException {
        int quality = Integer.parseInt(Objects.requireNonNull(params.getOrDefault("quality", "80")));
        File screenshot = takeScreenshot(quality);
        sendFileResponse(os, "image/png", screenshot);
    }

    private void handleIsUiChangeRequest(OutputStream os) throws IOException {
        String result = is_ui_change();
        sendResponse(os, 200, "text/plain", result);
    }

    private void handleFindElementRequest(OutputStream os, Map<String, String> params) throws IOException {
        String type = params.get("type");
        String value = params.get("value");

        try {
            UiObject2 element = findElement(type, value);
            if (element != null) {
                String json = String.format("{%s}", getElementAttributes(element));
                sendResponse(os, 200, "application/json", json);
            } else {
                sendResponse(os, 404, "text/plain", "Element not found");
            }
        } catch (Exception e) {
            sendResponse(os, 400, "text/plain", e.getMessage());
        }
    }

    private void handleFindElementsRequest(OutputStream os, Map<String, String> params) throws IOException {
        String type = params.get("type");
        String value = params.get("value");

        try {
            List<UiObject2> elements = findElements(type, value);
            if (!elements.isEmpty()) {
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < elements.size(); i++) {
                    json.append("{").append(getElementAttributes(elements.get(i))).append("}");
                    if (i < elements.size() - 1) json.append(",");
                }
                json.append("]");
                sendResponse(os, 200, "application/json", json.toString());
            } else {
                sendResponse(os, 404, "text/plain", "Elements not found");
            }
        } catch (Exception e) {
            sendResponse(os, 400, "text/plain", e.getMessage());
        }
    }

    private void handleGetRootRequest(OutputStream os) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String rst = getWindowRoots();
        sendResponse(os, 200, "text/plain", rst);
    }

    private void sendResponse(OutputStream os, int statusCode, String contentType, String content) throws IOException {
        String header = String.format("HTTP/1.1 %d %s\r\n" +
                        "Content-Type: %s\r\n" +
                        "Content-Length: %d\r\n" +
                        "\r\n",
                statusCode, getStatusMessage(statusCode), contentType, content.length());

        os.write(header.getBytes());
        os.write(content.getBytes());
        os.flush();
    }

    private void sendFileResponse(OutputStream os, String contentType, File file) throws IOException {
        long contentLength = file.length();
        String header = String.format("HTTP/1.1 200 OK\r\n" +
                "Content-Type: %s\r\n" +
                "Content-Length: %d\r\n" +
                "\r\n", contentType, contentLength);

        os.write(header.getBytes());

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) != -1) {
                os.write(buffer, 0, length);
            }
        }
        os.flush();
    }

    private String getStatusMessage(int statusCode) {
        switch (statusCode) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 500: return "Internal Server Error";
            default: return "Unknown";
        }
    }

    private File dumpWindowHierarchy(boolean compressed, String fileName) {
        UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mDevice.setCompressedLayoutHeirarchy(compressed);

        File file = new File(path, fileName);
        try {
            if (!file.exists()) file.createNewFile();
            mDevice.dumpWindowHierarchy(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    private File takeScreenshot(int quality) throws IOException {
        File screenshotFile = new File(path, "screenshot.png");
        UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mDevice.takeScreenshot(screenshotFile, 0.1f, quality);
        return screenshotFile;
    }

    private BySelector buildBySelector(String type, String value) throws IOException {
        BySelector bySelector = null;

        switch (type) {
            case "text":
                bySelector = By.text(value);
                break;
            case "textContains":
                bySelector = By.textContains(value);
                break;
            case "textStartsWith":
                bySelector = By.textStartsWith(value);
                break;
            case "id":
                bySelector = By.res(value);
                break;
            case "class":
                bySelector = By.clazz(value);
                break;
            case "content_desc":
                bySelector = By.desc(value);
                break;
            case "content_descContains":
                bySelector = By.descContains(value);
                break;
            case "pkg":
                bySelector = By.pkg(value);
                break;
            case "checkable":
                bySelector = By.checkable(Boolean.parseBoolean(value));
                break;
            case "checked":
                bySelector = By.checked(Boolean.parseBoolean(value));
                break;
            case "clickable":
                bySelector = By.clickable(Boolean.parseBoolean(value));
                break;
            case "enabled":
                bySelector = By.enabled(Boolean.parseBoolean(value));
                break;
            case "focusable":
                bySelector = By.focusable(Boolean.parseBoolean(value));
                break;
            case "focused":
                bySelector = By.focused(Boolean.parseBoolean(value));
                break;
            case "scrollable":
                bySelector = By.scrollable(Boolean.parseBoolean(value));
                break;
            case "selected":
                bySelector = By.selected(Boolean.parseBoolean(value));
                break;
            default:
                throw new IOException("Unknown selector type");
        }
        return bySelector;
    }

    private String getElementAttributes(UiObject2 uiObject) {
        return  "\"text\": \"" + (uiObject.getText() != null ? uiObject.getText() : "") + "\", " +
                "\"id\": \"" + (uiObject.getResourceName() != null ? uiObject.getResourceName() : "") + "\", " +
                "\"class_name\": \"" + (uiObject.getClassName() != null ? uiObject.getClassName() : "") + "\", " +
                "\"package\": \"" + (uiObject.getApplicationPackage() != null ? uiObject.getApplicationPackage() : "") + "\", " +
                "\"content_desc\": \"" + (uiObject.getContentDescription() != null ? uiObject.getContentDescription() : "") + "\", " +
                "\"checkable\": \"" + uiObject.isCheckable() + "\", " +
                "\"checked\": \"" + uiObject.isChecked() + "\", " +
                "\"clickable\": \"" + uiObject.isClickable() + "\", " +
                "\"enabled\": \"" + uiObject.isEnabled() + "\", " +
                "\"focusable\": \"" + uiObject.isFocusable() + "\", " +
                "\"focused\": \"" + uiObject.isFocused() + "\", " +
                "\"scrollable\": \"" + uiObject.isScrollable() + "\", " +
                "\"long_clickable\": \"" + uiObject.isLongClickable() + "\", " +
                "\"selected\": \"" + uiObject.isSelected() + "\", " +
                "\"bounds\": \"" + uiObject.getVisibleBounds().toShortString() + "\"";
    }

    private UiObject2 findElement(String type, String value) throws IOException {
        BySelector selector = buildBySelector(type, value);
        UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        int retryCount = 3;
        while (retryCount > 0) {
            UiObject2 element = mDevice.findObject(selector);
            if (element != null) return element;
            retryCount--;
        }
        return null;
    }

    private List<UiObject2> findElements(String type, String value) throws IOException {
        BySelector selector = buildBySelector(type, value);
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).findObjects(selector);
    }


    public String getWindowRoots() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Class<?> clazz = Class.forName("androidx.test.uiautomator.UiDevice");
        Method method = clazz.getDeclaredMethod("getWindowRoots");
        method.setAccessible(true);
        AccessibilityNodeInfo[] roots = (AccessibilityNodeInfo[]) method.invoke(mDevice);
        return Arrays.toString(roots);
    }

    private void startWatchingUiChanges() {
        // 添加循环控制标志，避免无限循环无法终止
        AtomicBoolean isRunning = new AtomicBoolean(true);

        Thread watcherThread = new Thread(() -> {
            while (isRunning.get()) { // 用标志位控制循环
                try {
                    // 延长超时时间到15秒，减少频繁超时
                    InstrumentationRegistry.getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                            () -> {}, // 前置操作（无实际逻辑可留空）
                            checkWindowUpdate, // 事件条件
                            15000 // 超时时间延长至15000ms
                    );
                    // 只有收到符合条件的事件时，才标记UI变化
                    uiChanged.set(true);
                    Log.d("WatcherThread", "检测到UI变化");
                } catch (TimeoutException e) {
                    // 仅打印超时日志，不抛出异常，让线程继续循环
                    Log.w("WatcherThread", "超时未检测到UI事件，继续等待...");
                    // 可选：超时后也可以标记UI变化（根据业务需求）
                    // uiChanged.set(false);
                } catch (Exception e) {
                    // 捕获其他异常（如UiAutomation被销毁），终止循环
                    Log.e("WatcherThread", "监听线程发生错误，停止监听", e);
                    isRunning.set(false); // 终止循环
                }
            }
        });
        watcherThread.start();
    }


    private String is_ui_change() {
        if (lastWindowChangeEvent != null && uiChanged.getAndSet(false)) {
            lastWindowChangeEvent = null;
            return "true";
        } else {
            return "false";
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            lastWindowChangeEvent = event;
            uiChanged.set(true);
        }
    }

    @Override
    public void onInterrupt() {
        // Handle interrupt
    }
}