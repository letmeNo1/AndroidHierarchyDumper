package hank.dump_hierarchy;


import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class HierarchyTest {
    private static final String TAG ="hank_auto" ;
    private String path;
    private ServerSocket serverSocket;


    private void init(){
        Configurator.getInstance().setWaitForIdleTimeout(1);
        Configurator.getInstance().setWaitForSelectorTimeout(1);
        Context context= InstrumentationRegistry.getInstrumentation().getTargetContext();
        path=context.getExternalCacheDir().getPath();
        Log.i(TAG, "init: path = " + path);
    }

    @Test
    public void TestCase1() {
        init();
        Thread serverThread = new Thread(() -> {
            try {
                InetAddress serverAddress = InetAddress.getByName("localhost");
                serverSocket = new ServerSocket(9000, 0, serverAddress);
                Log.i(TAG, "Server is listening on: " + serverAddress.getHostAddress());

                while (true) {
                    Log.i(TAG, "Start Receive request: ");
                    Socket socket = serverSocket.accept();
                    handleClientRequest(socket);

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();
        try {
            Thread.sleep(990000); // 适当调整等待时间
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }


        private boolean handleClientRequest(Socket socket) {
            try {
                InputStream in;
                OutputStream outputStream = socket.getOutputStream();
                Log.i(TAG, "Receive a message");
                in = socket.getInputStream();
                InputStreamReader reader = new InputStreamReader(in);
                String msg = "";
                try {
                    char[] buf = new char[1024000];
                    int cnt = reader.read(buf);
                    if (cnt > 0) {
                        msg = new String(buf, 0, cnt);
                        System.out.println(msg);
                    }
                } catch(Exception ignored){

                }

                Log.i(TAG, "Content: " + msg);
//
//                // 在这里解析 request 字符串，根据通信协议处理请求参数和操作
                if (msg.contains("content=print")) {
                    handlePrintRequest(outputStream);
                } else if (msg.contains("dump")) {
                    handleDumpRequest(outputStream);
                } else if (msg.contains("get_root")) {
                    handleStatusRequest(outputStream);
                } else {
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

        private void handleDumpRequest(OutputStream outputStream) throws IOException {
            String response = dumpWindowHierarchy(true, "xxx.xml");
            outputStream.write(response.getBytes());
        }

        private void handleStatusRequest(OutputStream outputStream) throws IOException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
            String rst = dumpWindowHierarchy2();

            outputStream.write(rst.getBytes());
            Log.i(TAG, "init: path = "+rst);

        }

        private void handlePrintRequest(OutputStream outputStream) throws IOException {
            String response = "200\n";
            outputStream.write(response.getBytes());
            Log.i(TAG, "init: path = ");
        }


    public String dumpWindowHierarchy(boolean pressed, String fileName)  {
        UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mDevice.setCompressedLayoutHeirarchy(pressed);

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
        return file.getPath();
    }

    public String dumpWindowHierarchy2() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Class<?> clazz = Class.forName("androidx.test.uiautomator.UiDevice");
        Method method = clazz.getDeclaredMethod("getWindowRoots");
        method.setAccessible(true);
        AccessibilityNodeInfo[] roots = (AccessibilityNodeInfo[]) method.invoke(mDevice);
//        mDevice.waitForIdle();
        return Arrays.toString(roots);
    }
    private String convertBufferedReaderToString(BufferedReader reader) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
            if (line.isEmpty()) {
                break; // 遇到空行，停止读取
            }
            stringBuilder.append(line);
        }
        return stringBuilder.toString();
    }



}

//

