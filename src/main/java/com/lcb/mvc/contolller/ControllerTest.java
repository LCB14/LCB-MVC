package com.lcb.mvc.contolller;

import com.lcb.mvc.annotation.Controller;
import com.lcb.mvc.annotation.RequestMapping;
import com.lcb.mvc.annotation.ResponseBody;
import com.lcb.mvc.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author changbao.li
 * @Description 自己实现spring mvc controller测试
 * @Date 2019-08-14 23:35
 */
@Controller
public class ControllerTest {

    @RequestMapping("/getUser.do")
    @ResponseBody
    public Object getUser(HttpServletRequest request, HttpServletResponse response, User user, String str) {
        System.out.println("user:" + user.toString());
        System.out.println("str:" + str);
        return user;
    }

    @RequestMapping("/index.do")
    public String getUser() {
        return "index.html";
    }
}
