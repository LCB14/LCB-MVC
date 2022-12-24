package com.lcb.mvc.servlet;

import com.alibaba.fastjson.JSON;
import com.lcb.mvc.annotation.Controller;
import com.lcb.mvc.annotation.RequestMapping;
import com.lcb.mvc.annotation.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author changbao.li
 * @since 14 八月 2019
 */
public class LcbDispatchServlet extends HttpServlet {

    /**
     * 获取LcbDispatchServlet.class文件所在目录
     */
    private static String CLASS_PATH = LcbDispatchServlet.class.getResource("/").getPath();

    /**
     * 期望被扫描的包名
     */
    private static String SCAN_PATH = StringUtils.EMPTY;

    /**
     * 最终要扫描的目录
     */
    private static String BASE_PATH = StringUtils.EMPTY;

    /**
     * key:请求URI，value：被标注RequestMapping注解的method对象
     */
    private static Map<String, Object> map = new ConcurrentHashMap();

    @Override
    public void init(ServletConfig config) throws ServletException {
        String path = parseXML(config);
        File file = new File(path);
        BASE_PATH = file.getPath();
        scanProject(file);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // localhost:80808/user/getUser.do -> /user/getUser.do
        String requestURI = req.getRequestURI();
        Method method = (Method) map.get(requestURI);
        if (method != null) {
            // 理论上应该从spring 容器中拿到该method所在类对象，此处就不在模拟spring了。
            try {
                Object object = getAimObject(method);
                Object[] objectParameters = getAimMethodParameters(method, req, resp);
                Object invoke = method.invoke(object, objectParameters);
                if (method.isAnnotationPresent(ResponseBody.class)) {
                    resp.getWriter().write(JSON.toJSONString(invoke));
                } else {
                    if (method.getReturnType() == String.class) {
                        // 进行请求转发
                        req.getRequestDispatcher("/" + (String) invoke).forward(req, resp);
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else {
            resp.setStatus(404);
        }
    }

    /**
     * 解析lcbMVC.xml配置文件,获取待扫描包的路径。
     *
     * @param config
     * @return
     */
    public String parseXML(ServletConfig config) {
        String contextConfigLocation = config.getInitParameter("contextConfigLocation");
        try {
            SAXReader saxReader = new SAXReader();
            CLASS_PATH = CLASS_PATH.replaceAll("%20", " ");
            File file = new File(CLASS_PATH + contextConfigLocation);
            Document doc = saxReader.read(file);
            Element rootElement = doc.getRootElement();
            Element packageScan = rootElement.element("packageScan");
            Attribute aPackage = packageScan.attribute("package");
            SCAN_PATH = aPackage.getValue();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return CLASS_PATH + SCAN_PATH;
    }

    /**
     * 扫描整个项目，获取所有controller映射
     *
     * @param file
     */
    public void scanProject(File file) {
        try {
            // 递归扫描
            if (file.isDirectory()) {
                for (File item : file.listFiles()) {
                    scanProject(item);
                }
            } else {
                // 判断是不是class文件
                String fileName = file.getName();
                if (fileName.substring(fileName.lastIndexOf(".")).equals(".class")) {
                    // 获取指定class文件的全限定名
                    String classPath = pathHandle(file);
                    Class<?> clazz = Class.forName(classPath);

                    // 判断是不是controller类
                    if (clazz.isAnnotationPresent(Controller.class)) {
                        RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
                        String url_path = StringUtils.EMPTY;
                        if (requestMapping != null) {
                            url_path = requestMapping.value();
                        }

                        for (Method method : clazz.getMethods()) {
                            String methodPath = StringUtils.EMPTY;
                            RequestMapping annotation = method.getAnnotation(RequestMapping.class);
                            if (annotation != null) {
                                methodPath = annotation.value();
                                if (map.putIfAbsent(url_path + methodPath, method) != null) {
                                    throw new RuntimeException("映射地址重复");
                                }
                                System.out.println(url_path + methodPath + "被成功映射到了" + clazz.getName() + "的" + method.getName() + "方法上");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 拼接指定class的全限定名
     *
     * @param file
     * @return
     */
    public String pathHandle(File file) {
        String path = file.getPath();
        path = path.replace(BASE_PATH, "");
        path = SCAN_PATH + path;
        path = path.replaceAll("/", ".");
        path = path.substring(0, path.lastIndexOf("."));
        return path;
    }

    /**
     * 获取Method实例所在的目标对象 -- 反射使用
     *
     * @param method
     * @return
     * @throws Exception
     */
    public Object getAimObject(Method method) throws Exception {
        Class<?> clazz = method.getDeclaringClass();
        Object object = clazz.newInstance();
        return object;
    }

    /**
     * 获取目标Method方法的所有参数值 -- 反射使用
     *
     * @param method
     * @param req
     * @param resp
     * @return
     * @throws Exception
     */
    public Object[] getAimMethodParameters(Method method, HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Parameter[] parameters = method.getParameters();
        // 定义一个实参数组
        Object[] objectParameters = new Object[parameters.length];
        for (int i = 0; i < objectParameters.length; i++) {
            String paramName = parameters[i].getName();
            Class paramType = parameters[i].getType();

            if (paramType == HttpServletRequest.class) {
                objectParameters[i] = req;
            } else if (paramType == HttpServletResponse.class) {
                objectParameters[i] = resp;
            } else if (paramType == String.class) {
                // 注意在jdk1.8之前的版本，得到的参数名不是方法真正的参数名而是arg%d形式
                objectParameters[i] = req.getParameter(paramName);
            } else {
                Object o = paramType.newInstance();
                for (Field declaredField : paramType.getDeclaredFields()) {
                    declaredField.setAccessible(true);
                    String name = declaredField.getName();
                    declaredField.set(o, req.getParameter(name));
                }
                objectParameters[i] = o;
            }
        }
        return objectParameters;
    }
}
