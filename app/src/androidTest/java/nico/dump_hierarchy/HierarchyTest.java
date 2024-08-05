package nico.dump_hierarchy;

import static androidx.test.InstrumentationRegistry.getContext;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class HierarchyTest extends AccessibilityService {
    private static final String TAG ="hank_auto" ;
    private String path;
    private AccessibilityEvent lastWindowChangeEvent = null;

    private ServerSocket serverSocket;

    private void init(){
        Configurator.getInstance().setWaitForIdleTimeout(1);
        Configurator.getInstance().setWaitForSelectorTimeout(1);
        Context context= InstrumentationRegistry.getInstrumentation().getTargetContext();
        File filesDir = context.getFilesDir();
        path = filesDir.getPath();
    }

    private final UiAutomation.AccessibilityEventFilter checkWindowUpdate = event -> {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            lastWindowChangeEvent = event;
            return true;
        }
        return false;
    };

    private final AtomicBoolean uiChanged = new AtomicBoolean(false);

    private void startWatchingUiChanges() {
        Thread watcherThread = new Thread(() -> {
            while (true) {
                try {
                    InstrumentationRegistry.getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                            () -> {},
                            checkWindowUpdate,
                            5000
                    );
                    uiChanged.set(true);
                } catch (TimeoutException e) {
                    continue;
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

    @Test
    public void TestCase1() {
        init();
        startWatchingUiChanges();
        Thread serverThread = new Thread(() -> {
            try {
                Bundle arguments = InstrumentationRegistry.getArguments();
                String portString = arguments.getString("port");
                int port = portString != null ? Integer.parseInt(portString) : 8000; // 使用默认值9000，如果没有提供参数
                InetAddress serverAddress = InetAddress.getByName("localhost");
                serverSocket = new ServerSocket(port, 0, serverAddress);
                Log.i(TAG, "Server is listening on: " + serverAddress.getHostAddress() + ":" + portString);

                while (true) {
                    Log.i(TAG, "Start Receive request: ");
                    Socket socket = serverSocket.accept();
                    String msg;
                    OutputStream  outputStream;
                    Map<String, Object> result;
                    result = format_socket_msg(socket);
                    msg = (String) result.get("msg");
                    outputStream = (OutputStream) result.get("outputStream");
                    assert msg != null;
                    assert outputStream != null;

                    if (msg.contains("close")){
                        socket.close(); // 关闭socket
                        serverSocket.close(); // 关闭serverSocket
                        return;

                    }
                    handleClientRequest(socket,msg,outputStream);

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();
        while (serverThread.isAlive()) { // 检查serverThread线程是否还在运行
            try {
                Thread.sleep(1000); // 每秒检查一次
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private Map<String, Object> format_socket_msg(Socket socket){
        String msg;
        OutputStream  outputStream;
        Map<String, Object> result = new HashMap<>();
        try {
            InputStream in;
            outputStream = socket.getOutputStream();
            Log.i(TAG, "Receive a message");
            in = socket.getInputStream();
            InputStreamReader reader = new InputStreamReader(in);
            msg = "";
            try {
                char[] buf = new char[1024000];
                int cnt = reader.read(buf);
                if (cnt > 0) {
                    msg = new String(buf, 0, cnt);
                    System.out.println(msg);
                }
            } catch(Exception ignored){

            }
            result.put("outputStream", outputStream);
            result.put("msg", msg);

            Log.i(TAG, "Content: " + msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;

    }

    private boolean handleClientRequest(Socket socket,String msg,OutputStream outputStream) {
        try {
            if (msg.contains("print")) {
                handlePrintRequest(outputStream);
            } else if (msg.contains("dump")) {
                boolean compressed = Boolean.parseBoolean(msg.split(":")[1].trim());
                handleDumpRequest(outputStream,compressed);
            } else if (msg.contains("get_root")) {
                handleStatusRequest(outputStream);
            } else if (msg.contains("get_png_pic")) {
                Integer quality = Integer.parseInt(msg.split(":")[1].trim());
                handlePicRequest(outputStream,quality);
            }
            else if (msg.contains("is_ui_change")) {
                String png = is_ui_change();
                outputStream.write(png.getBytes());
            }else if (msg.contains("sys_tools")){
                Integer quality = Integer.parseInt(msg.split(":")[1].trim());
                handlePicRequest(outputStream,quality);
            }else if (msg.contains("handleMultiTouchRequest")){
                handleMultiTouchRequest(outputStream,0,0,0,0,0,0,0,0);
            } else if (msg.contains("find_element_by_query")) {
                String[] parts = msg.split(":", 3); // 限制分割次数为3，确保value包含所有内容
                String type = parts[1].trim();
                String value = parts[2].trim();

                handleFindElementRequest(outputStream, type,value);
            } else if (msg.contains("find_elements_by_query")) {
                String[] parts = msg.split(":", 3); // 限制分割次数为3，确保value包含所有内容
                String type = parts[1].trim();
                String value = parts[2].trim();

                handleFindElementsRequest(outputStream, type,value);
            }else {
                String response = "Unknown request\n";
                outputStream.write(response.getBytes());
            }
            outputStream.flush();
        } catch (IOException | NullPointerException | ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();  // 关闭客户端套接字
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private void handleDumpRequest(OutputStream outputStream,boolean compressed) throws IOException {
        File response = dumpWindowHierarchy(compressed, "xxx.xml");
        try (FileInputStream fis = new FileInputStream(response)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush(); // 确保所有的数据都被写入到OutputStream
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleStatusRequest(OutputStream outputStream) throws IOException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String rst = getWindowRoots();

        outputStream.write(rst.getBytes());
        Log.i(TAG, "init: path = "+rst);

    }

    private void handleMultiTouchRequest(OutputStream outputStream,int startX1, int startY1, int endX1, int endY1, int startX2, int startY2, int endX2, int endY2) throws IOException {
        // 定义第一个手指的滑动路径
        Path path1 = new Path();
        path1.moveTo(540, 860);
        path1.lineTo(540, 1060);

        // 定义第二个手指的滑动路径
        Path path2 = new Path();
        path2.moveTo(540, 1060);
        path2.lineTo(540, 860);

        // 创建手势描述并添加两个手指的滑动路径
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path1, 0, 500)); // 持续时间500ms
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path2, 0, 500)); // 持续时间500ms

        // 发送手势
        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                // 手势完成时的回调
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                // 手势取消时的回调
            }
        }, null);
        outputStream.write("ok".getBytes());
        Log.i(TAG, "init: path = "+"ok");
    }

    private void handlePicRequest(OutputStream outputStream,Integer quality) throws IOException {
        UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        File screenshotFile = new File(path, "screenshot.png");

        mDevice.takeScreenshot(screenshotFile,0.1f, 1);
        try (FileInputStream fis = new FileInputStream(screenshotFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush(); // 确保所有的数据都被写入到OutputStream
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handlePrintRequest(OutputStream outputStream) throws IOException {
        String response = "HTTP/1.1 200 OK\n";
        outputStream.write(response.getBytes());
        Log.i(TAG, "init: path = ");
    }

    private BySelector buildBySelector( String type, String value) throws IOException {
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

    private void handleFindElementRequest(OutputStream outputStream, String type, String value) throws IOException {
        UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        BySelector bySelector;

        try {
            bySelector = buildBySelector(type, value);
        } catch (IOException e) {
            outputStream.write(e.getMessage().getBytes());
            return;
        }

        UiObject2 uiObject = null;
        int retryCount = 3; // 重试次数
        while (retryCount > 0) {
            try {
                uiObject = mDevice.findObject(bySelector);
                if (uiObject != null) {
                    // 尝试访问对象属性
                    String text = uiObject.getText();
                    break; // 如果成功找到对象并访问其属性，则退出循环
                }
            } catch (StaleObjectException e) {
                // 捕获异常并重新查找对象
                mDevice.waitForIdle(3000);
            }
            retryCount--;
        }

        String response;
        if (uiObject != null) {
            response = "{" + getElementAttributes(uiObject) + "}";
            Log.d(TAG, "found element" + response);
        } else {
            response = "Element not found";
            Log.d(TAG, "Element not found" + response);
        }
        outputStream.write(response.getBytes());
    }

    private void handleFindElementsRequest(OutputStream outputStream, String type, String value) throws IOException {
        UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        BySelector bySelector;

        try {
            bySelector = buildBySelector(type, value);
        } catch (IOException e) {
            outputStream.write(e.getMessage().getBytes());
            return;
        }

        List<UiObject2> uiObjects = mDevice.findObjects(bySelector);
        StringBuilder response = new StringBuilder();
        if (!uiObjects.isEmpty()) {
            for (int i = 0; i < uiObjects.size(); i++) {
                UiObject2 uiObject = uiObjects.get(i);
                response.append("{").append(getElementAttributes(uiObject)).append("}");
                if (i < uiObjects.size() - 1) {
                    response.append(",");
                }
            }
        } else {
            response.append("Elements not found\n");
        }
        outputStream.write(response.toString().getBytes());
    }

    public File dumpWindowHierarchy(boolean compressed, String fileName)  {
        UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mDevice.setCompressedLayoutHeirarchy(compressed);

        File file=new File(path,fileName);
        // 获取屏幕的 View 层级结构
        try {
            if (!file.exists()){
                file.createNewFile();
            }
            mDevice.dumpWindowHierarchy(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
//        mDevice.waitForIdle();
        return file;
    }

    public void dumpWindowHierarchyAndPrint(boolean compressed) {
        UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mDevice.setCompressedLayoutHeirarchy(compressed);

        StringWriter stringWriter = new StringWriter();
        // 将 UI 层次结构转储到 StringWriter 中
        mDevice.dumpWindowHierarchy(String.valueOf(stringWriter));

        // 将 StringWriter 的内容打印到日志中
        String hierarchyDump = stringWriter.toString();
        Log.d(TAG, hierarchyDump);
    }

    public String getWindowRoots() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Class<?> clazz = Class.forName("androidx.test.uiautomator.UiDevice");
        Method method = clazz.getDeclaredMethod("getWindowRoots");
        method.setAccessible(true);
        AccessibilityNodeInfo[] roots = (AccessibilityNodeInfo[]) method.invoke(mDevice);
//        mDevice.waitForIdle();
        return Arrays.toString(roots);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {

    }

    @Override
    public void onInterrupt() {

    }
}