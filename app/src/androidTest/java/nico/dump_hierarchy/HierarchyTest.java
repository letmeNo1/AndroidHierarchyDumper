package nico.dump_hierarchy;

import android.app.UiAutomation;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;

import org.junit.Test;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class HierarchyTest {
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
                int port = portString != null ? Integer.parseInt(portString) : 9000; // 使用默认值9000，如果没有提供参数
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
            }
            else {
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
        String response = "200\n";
        outputStream.write(response.getBytes());
        Log.i(TAG, "init: path = ");
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

    public String getWindowRoots() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Class<?> clazz = Class.forName("androidx.test.uiautomator.UiDevice");
        Method method = clazz.getDeclaredMethod("getWindowRoots");
        method.setAccessible(true);
        AccessibilityNodeInfo[] roots = (AccessibilityNodeInfo[]) method.invoke(mDevice);
//        mDevice.waitForIdle();
        return Arrays.toString(roots);
    }
}

//
