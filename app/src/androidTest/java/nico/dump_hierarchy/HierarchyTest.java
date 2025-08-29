package nico.dump_hierarchy;

import static androidx.test.InstrumentationRegistry.getContext;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
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
import androidx.test.uiautomator.Until;

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
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
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
            String requestBody = request.get("body"); // 新增：获取POST请求体
            Map<String, String> params = parseQueryParams(request.get("query"));

            try {  // 新增内层try块
                switch (path) {
                    case "/execute_json_script":
                        if ("POST".equals(method)) {
                            // 调用handleExecuteJsonScript处理POST请求体中的JSON
                            handleExecuteJsonScript(os, requestBody);
                        } else {
                            sendResponse(os, 405, "application/json",
                                    "{\"success\":false, \"message\":\"仅支持POST方法\"}");
                        }
                        break;
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
                    case "/input":
                        // 支持GET和POST（参数可放在URL或请求体）
                        if ("GET".equals(method) || "POST".equals(method)) {
                            handleInputRequest(os, params);
                        } else {
                            sendResponse(os, 405, "application/json",
                                    "{\"success\":false, \"message\":\"仅支持GET/POST方法\"}");
                        }
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

        // 读取请求行
        String requestLine = reader.readLine();
        if (requestLine == null) return request;
        String[] parts = requestLine.split(" ");
        if (parts.length >= 2) {
            request.put("method", parts[0]);
            request.put("path", parts[1]);
            if (parts.length >= 3) request.put("protocol", parts[2]);
        }

        // 解析路径和查询参数
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

        // 读取头部信息（重点：获取Content-Length）
        int contentLength = 0;
        while (true) {
            String line = reader.readLine();
            if (line == null || line.isEmpty()) break;
            // 解析Content-Length头部，用于读取Body
            if (line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
            }
        }

        // 读取POST Body（根据Content-Length读取）
        if (contentLength > 0) {
            char[] bodyChars = new char[contentLength];
            reader.read(bodyChars, 0, contentLength);
            request.put("body", new String(bodyChars));
        } else {
            request.put("body", ""); // 空Body
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

    /**
     * 执行单个动作
     * @param actionType 动作类型：click/find_and_click/find_and_input/swipe_sequence
     * @param params 动作参数
     * @param elementCache 元素缓存（供动作间共享）
     * @return 执行结果（JSON字符串）
     */
    private String executeAction(String actionType, Map<String, Object> params, Map<String, UiObject2> elementCache) {
        Gson gson = new Gson();
        Map<String, Object> result = new HashMap<>();

        try {
            switch (actionType) {
                // 1. 坐标点击（直接按x,y点击）
                case "click":
                    // 参数校验：必须包含x和y
                    if (!params.containsKey("x") || !params.containsKey("y")) {
                        result.put("success", false);
                        result.put("message", "click动作缺少参数x或y");
                        return gson.toJson(result);
                    }

                    float x = ((Number) params.get("x")).floatValue();
                    float y = ((Number) params.get("y")).floatValue();

                    // 执行点击（按下→延迟→抬起）
                    boolean clickSuccess = touchController.touchDown(x, y);
                    SystemClock.sleep(50); // 模拟按下时长
                    clickSuccess &= touchController.touchUp(x, y);

                    result.put("success", clickSuccess);
                    result.put("message", clickSuccess ? "坐标点击成功" : "坐标点击失败");
                    result.put("x", x);
                    result.put("y", y);
                    return gson.toJson(result);

                // 2. 查找并点击（先找元素，再点击中心）
                case "find_and_click":
                    // 参数校验：必须包含type和value
                    if (!params.containsKey("type") || !params.containsKey("value")) {
                        result.put("success", false);
                        result.put("message", "find_and_click缺少参数type或value");
                        return gson.toJson(result);
                    }

                    String findType = (String) params.get("type");
                    String findValue = (String) params.get("value");
                    int findTimeout = params.containsKey("timeout") ?
                            ((Number) params.get("timeout")).intValue() : 5000;

                    // 查找元素
                    UiObject2 element = findElementWithTimeout(findType, findValue, findTimeout);
                    if (element == null) {
                        result.put("success", false);
                        result.put("message", "未找到元素：" + findType + "=" + findValue);
                        return gson.toJson(result);
                    }

                    // 点击元素中心
                    Rect bounds = element.getVisibleBounds();
                    float centerX = bounds.centerX();
                    float centerY = bounds.centerY();
                    boolean findClickSuccess = touchController.touchDown(centerX, centerY);
                    SystemClock.sleep(50);
                    findClickSuccess &= touchController.touchUp(centerX, centerY);

                    // 缓存元素供后续动作使用
                    elementCache.put("last_found", element);

                    result.put("success", findClickSuccess);
                    result.put("message", findClickSuccess ? "查找并点击成功" : "查找成功但点击失败");
                    result.put("element_bounds", bounds.toShortString());
                    result.put("click_x", centerX);
                    result.put("click_y", centerY);
                    return gson.toJson(result);

                // 3. 查找并输入（先找元素，再输入文本）
                case "find_and_input":
                    // 参数校验：必须包含type、value、text
                    if (!params.containsKey("type") || !params.containsKey("value") || !params.containsKey("text")) {
                        result.put("success", false);
                        result.put("message", "find_and_input缺少参数type/value/text");
                        return gson.toJson(result);
                    }

                    String inputType = (String) params.get("type");
                    String inputValue = (String) params.get("value");
                    String inputText = (String) params.get("text");
                    boolean clear = params.containsKey("clear") ?
                            (boolean) params.get("clear") : true; // 默认清空
                    int inputTimeout = params.containsKey("timeout") ?
                            ((Number) params.get("timeout")).intValue() : 5000;

                    // 查找输入框元素
                    UiObject2 inputElement = findElementWithTimeout(inputType, inputValue, inputTimeout);
                    if (inputElement == null) {
                        result.put("success", false);
                        result.put("message", "未找到输入元素：" + inputType + "=" + inputValue);
                        return gson.toJson(result);
                    }

                    // 执行输入
                    if (clear) inputElement.clear();
                    SystemClock.sleep(200); // 等待清空
                    inputElement.setText(inputText);
                    SystemClock.sleep(300); // 等待输入完成

                    // 验证输入结果
                    String actualText = inputElement.getText();
                    boolean inputSuccess = inputText.equals(actualText);

                    result.put("success", inputSuccess);
                    result.put("message", inputSuccess ? "输入成功" : "输入失败（实际值：" + actualText + "）");
                    result.put("input_text", inputText);
                    result.put("actual_text", actualText);
                    return gson.toJson(result);

                // 4. 滑动序列（按下→多步滑动→抬起）
                case "swipe_sequence":
                    // 参数校验：必须包含startX、startY和steps（滑动步骤数组）
                    if (!params.containsKey("startX") || !params.containsKey("startY") || !params.containsKey("steps")) {
                        result.put("success", false);
                        result.put("message", "swipe_sequence缺少参数startX/startY/steps");
                        return gson.toJson(result);
                    }

                    float startX = ((Number) params.get("startX")).floatValue();
                    float startY = ((Number) params.get("startY")).floatValue();
                    List<Map<String, Number>> steps = (List<Map<String, Number>>) params.get("steps");

                    if (steps.isEmpty()) {
                        result.put("success", false);
                        result.put("message", "steps数组不能为空（至少需要1步滑动）");
                        return gson.toJson(result);
                    }

                    // 执行滑动序列：按下起点→分步滑动→抬起终点
                    boolean swipeSuccess = touchController.touchDown(startX, startY);
                    SystemClock.sleep(50); // 按下延迟

                    // 总滑动时长（默认500ms，可通过参数自定义）
                    int totalDuration = params.containsKey("duration") ?
                            ((Number) params.get("duration")).intValue() : 500;
                    int stepDelay = totalDuration / steps.size();

                    // 执行每步滑动
                    for (Map<String, Number> step : steps) {
                        float stepX = step.get("x").floatValue();
                        float stepY = step.get("y").floatValue();
                        swipeSuccess &= touchController.touchMove(stepX, stepY);
                        SystemClock.sleep(stepDelay); // 每步间隔
                    }

                    // 抬起（终点为最后一步的坐标）
                    Map<String, Number> lastStep = steps.get(steps.size() - 1);
                    float endX = lastStep.get("x").floatValue();
                    float endY = lastStep.get("y").floatValue();
                    swipeSuccess &= touchController.touchUp(endX, endY);

                    result.put("success", swipeSuccess);
                    result.put("message", swipeSuccess ? "滑动序列执行成功" : "滑动序列执行失败");
                    result.put("start", Map.of("x", startX, "y", startY));
                    result.put("end", Map.of("x", endX, "y", endY));
                    result.put("step_count", steps.size());
                    return gson.toJson(result);

                default:
                    result.put("success", false);
                    result.put("message", "未知动作类型：" + actionType);
                    return gson.toJson(result);
            }
        } catch (ClassCastException e) {
            result.put("success", false);
            result.put("message", "参数类型错误：" + e.getMessage());
            return gson.toJson(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "动作执行失败：" + e.getMessage());
            return gson.toJson(result);
        }
    }

    private void handleExecuteJsonScript(OutputStream os, String jsonContent) throws IOException {
        Gson gson = new Gson();
        try {
            // 解析JSON脚本为动作列表：[{type: "...", params: {...}}, ...]
            Type actionListType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> actions = gson.fromJson(jsonContent, actionListType);

            if (actions == null || actions.isEmpty()) {
                sendResponse(os, 400, "application/json",
                        "{\"success\":false, \"message\":\"JSON脚本为空或格式错误\"}");
                return;
            }

            // 元素缓存：存储查找的元素供后续动作复用
            Map<String, UiObject2> elementCache = new HashMap<>();
            // 执行结果列表：记录每个动作的详细执行情况
            List<Map<String, Object>> executionResults = new ArrayList<>();
            boolean allSuccess = true;

            // 按顺序执行每个动作
            for (int i = 0; i < actions.size(); i++) {
                Map<String, Object> action = actions.get(i);
                Map<String, Object> result = new HashMap<>();
                result.put("actionIndex", i);
                result.put("actionType", action.getOrDefault("type", "unknown"));

                try {
                    // 基础校验：动作必须包含type和params
                    if (!action.containsKey("type") || !action.containsKey("params")) {
                        result.put("success", false);
                        result.put("message", "动作缺少type或params字段");
                        executionResults.add(result);
                        allSuccess = false;
                        continue;
                    }

                    // 执行动作并获取结果
                    String actionType = (String) action.get("type");
                    Map<String, Object> params = (Map<String, Object>) action.get("params");
                    String actionResultJson = executeAction(actionType, params, elementCache);

                    // 解析动作结果并记录
                    Map<String, Object> actionResult = gson.fromJson(actionResultJson, new TypeToken<Map<String, Object>>(){}.getType());
                    result.putAll(actionResult);
                    executionResults.add(result);

                    // 若当前动作失败，标记整体失败
                    if (!(boolean) actionResult.getOrDefault("success", false)) {
                        allSuccess = false;
                        // 可根据需求改为break（停止后续动作）或continue（继续执行）
                        // break;
                    }

                } catch (Exception e) {
                    result.put("success", false);
                    result.put("message", "动作执行异常：" + e.getMessage());
                    executionResults.add(result);
                    allSuccess = false;
                }
            }

            // 构建总响应
            Map<String, Object> totalResponse = new HashMap<>();
            totalResponse.put("success", allSuccess);
            totalResponse.put("totalActions", actions.size());
            totalResponse.put("results", executionResults);
            sendResponse(os, 200, "application/json", gson.toJson(totalResponse));

        } catch (Exception e) {
            sendResponse(os, 400, "application/json",
                    "{\"success\":false, \"message\":\"解析JSON脚本失败：" + e.getMessage() + "\"}");
        }
    }

    private void handleInputRequest(OutputStream os, Map<String, String> params) throws IOException {
        try {
            // 解析元素查找参数
            String type = params.get("type");
            String value = params.get("value");
            int timeout = parseTimeout(params.get("timeout"), 5000);

            // 解析输入参数
            String inputText = params.get("text");
            boolean clearBeforeInput = Boolean.parseBoolean(params.getOrDefault("clear", "true")); // 默认清空

            if (type == null || value == null || inputText == null) {
                sendResponse(os, 400, "application/json",
                        "{\"success\":false, \"message\":\"缺少参数（type/value/text）\"}");
                return;
            }

            // 查找输入元素
            UiObject2 inputElement = findElementWithTimeout(type, value, timeout);
            if (inputElement == null) {
                sendResponse(os, 404, "application/json",
                        "{\"success\":false, \"message\":\"未找到输入元素\"}");
                return;
            }

            // 执行输入操作
            if (clearBeforeInput) {
                inputElement.clear(); // 清空
                SystemClock.sleep(200); // 等待清空完成
            }
            inputElement.setText(inputText); // 输入文本
            SystemClock.sleep(300); // 等待输入完成

            // 验证结果
            String actualText = inputElement.getText();
            boolean success = inputText.equals(actualText);

            // 返回结果
            sendResponse(os, 200, "application/json",
                    String.format("{\"success\":%b, \"message\":\"%s\", \"actual_text\":\"%s\"}",
                            success,
                            success ? "输入成功" : "输入失败",
                            actualText)
            );

        } catch (Exception e) {
            sendResponse(os, 500, "application/json",
                    String.format("{\"success\":false, \"message\":\"输入操作异常：%s\"}", e.getMessage()));
        }
    }

    private UiObject2 findElementWithTimeout(String type, String value, int timeout) throws IOException {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        BySelector selector = buildBySelector(type, value);
        // 使用 UiAutomator 的超时等待机制（等待元素出现，最多等待 timeout 毫秒）
        return device.wait(Until.findObject(selector), timeout);
    }

    // 带超时的多个元素查找
    private List<UiObject2> findElementsWithTimeout(String type, String value, int timeout) throws IOException {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        BySelector selector = buildBySelector(type, value);
        // 等待元素出现后再查找（最多等待 timeout 毫秒）
        if (device.wait(Until.hasObject(selector), timeout)) {
            return device.findObjects(selector);
        }
        return Arrays.asList(); // 超时返回空列表
    }

    private void handleFindElementRequest(OutputStream os, Map<String, String> params) throws IOException {
        String type = params.get("type");
        String value = params.get("value");
        // 获取超时参数（默认5000毫秒）
        int timeout = parseTimeout(params.get("timeout"), 5000);

        try {
            UiObject2 element = findElementWithTimeout(type, value, timeout);
            if (element != null) {
                String json = String.format("{%s}", getElementAttributes(element));
                sendResponse(os, 200, "application/json", json);
            } else {
                sendResponse(os, 404, "text/plain", "Element not found within timeout");
            }
        } catch (Exception e) {
            sendResponse(os, 400, "text/plain", e.getMessage());
        }
    }

    private void handleFindElementsRequest(OutputStream os, Map<String, String> params) throws IOException {
        String type = params.get("type");
        String value = params.get("value");
        // 获取超时参数（默认5000毫秒）
        int timeout = parseTimeout(params.get("timeout"), 5000);

        try {
            List<UiObject2> elements = findElementsWithTimeout(type, value, timeout);
            if (!elements.isEmpty()) {
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < elements.size(); i++) {
                    json.append("{").append(getElementAttributes(elements.get(i))).append("}");
                    if (i < elements.size() - 1) json.append(",");
                }
                json.append("]");
                sendResponse(os, 200, "application/json", json.toString());
            } else {
                sendResponse(os, 404, "text/plain", "Elements not found within timeout");
            }
        } catch (Exception e) {
            sendResponse(os, 400, "text/plain", e.getMessage());
        }
    }

    private int parseTimeout(String timeoutStr, int defaultValue) {
        if (timeoutStr == null || timeoutStr.isEmpty()) {
            return defaultValue;
        }
        try {
            int timeout = Integer.parseInt(timeoutStr);
            return Math.max(timeout, 0); // 确保超时时间非负
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void handleGetRootRequest(OutputStream os) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String rst = getWindowRoots();
        sendResponse(os, 200, "text/plain", rst);
    }

    private void sendResponse(OutputStream os, int statusCode, String contentType, String content) throws IOException {
        byte[] contentBytes = content.getBytes("UTF-8"); // 明确指定编码（如UTF-8）
        String header = String.format("HTTP/1.1 %d %s\r\n" +
                        "Content-Type: %s; charset=UTF-8\r\n" + // 补充字符集说明
                        "Content-Length: %d\r\n" +
                        "\r\n",
                statusCode, getStatusMessage(statusCode), contentType, contentBytes.length); // 使用字节数组长度

        os.write(header.getBytes("UTF-8"));
        os.write(contentBytes);
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