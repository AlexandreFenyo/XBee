package net.fenyo.xbee.web;

import java.io.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import net.fenyo.xbee.serial.*;
import org.apache.commons.logging.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.*;

// excellente doc sur les controller : "16.3 Implementing Controllers" de spring-framework-reference.pdf
@Controller("webController")
public class WebController {
    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    private SerialPortHandler serialPortHandler;

    @RequestMapping("/intro")
    public ModelAndView intro(@RequestParam(value = "statusString", required = false) final String status_string) {
        ModelAndView mav = new ModelAndView();
        mav.addObject("statusString", status_string);
        mav.setViewName("say-hello");
        return mav;
    }

    @RequestMapping("/tst")
    public ModelAndView tst(HttpServletResponse response) {
        ModelAndView mav = new ModelAndView();
        mav.setViewName("say-hello");
        
        Cookie myJsonCookie = new Cookie("cookiXeName", "{\"segmentation\":\"6\"}");
        Cookie myJsonCookie2 = new Cookie("cookXieName2", "{\"name\":\"someName\"}");
        
        log.debug("xXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
//        myJsonCookie.setHttpOnly(true);
//        myJsonCookie.setSecure(true);
        myJsonCookie.setPath("/");
//        myJsonCookie.setMaxAge(60*60);
//        response.addCookie(myJsonCookie);
//        response.addCookie(myJsonCookie2);
        response.addHeader("X-Cookie2", "{\"nom\":\"valeur\"");
        response.addIntHeader("toto", 123);
        return mav;
    }

    @RequestMapping("/sendRemoteAT")
    public ModelAndView sendRemoteAT(@RequestParam(value = "param", required = true) final String param)
            throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView();

        boolean done = false;
        // peut etre passer de 10 à 3 retries, pour les volets d'en bas
        for (int retries = 0; retries < 10; retries++) {
            if (retries > 0)
                log.warn("retry nb " + retries);
            byte[] ret = serialPortHandler.sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409A960C,
                    param.substring(0, 2)
                            + new String(new byte[] { (byte) (param.charAt(2) - new Character('0').charValue()) }));

            if (ret == null)
                log.warn("timeout sending remote AT command");
            else {
                ret = serialPortHandler.sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409A960C,
                        param.substring(0, 2));
                if (ret == null || !SerialPortHandler.bytesArrayToString(ret).equals("0" + param.substring(2, 3))) {
                    log.warn("invalid remote value: "
                            + (ret != null ? SerialPortHandler.bytesArrayToString(ret) : "null"));
                } else {
                    done = true;
                    log.debug("OK sent and checked");
                    break;
                }
            }
        }

        mav.addObject("statusString", done ? "success" : "error");
        mav.setViewName("display-status");
        return mav;
    }

    @RequestMapping("/sendRemoteATNoAck")
    public ModelAndView sendRemoteATNoAck(@RequestParam(value = "param", required = true) final String param)
            throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView();

        serialPortHandler.sendRemoteATCommandFrameSingleQueryAck(0x13a200, 0x409b7abb, param.substring(0, 2)
                + new String(new byte[] { (byte) (param.charAt(2) - new Character('0').charValue()) }), false);
        mav.addObject("statusString", "success");
        mav.setViewName("display-status");
        return mav;
    }

    @RequestMapping("/checkAT")
    public ModelAndView checkAT() throws IOException, InterruptedException {
        // log.debug("check AT called");

        ModelAndView mav = new ModelAndView("redirect:intro");
        mav.addObject("statusString", serialPortHandler.checkAT());
        return mav;
    }

    @RequestMapping("/setOnLED0")
    public ModelAndView setOnLED0() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView();
        serialPortHandler.state_led0 = true;
        mav.addObject("statusString", "success");
        mav.setViewName("display-status");
        return mav;
    }

    @RequestMapping("/setOnLED1")
    public ModelAndView setOnLED1() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView();
        serialPortHandler.state_led1 = true;
        mav.addObject("statusString", "success");
        mav.setViewName("display-status");
        return mav;
    }

    @RequestMapping("/setOnLED2")
    public ModelAndView setOnLED2() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView();
        serialPortHandler.state_led2 = true;
        mav.addObject("statusString", "success");
        mav.setViewName("display-status");
        return mav;
    }

    @RequestMapping("/setOnLED3")
    public ModelAndView setOnLED3() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView();
        serialPortHandler.state_led3 = true;
        mav.addObject("statusString", "success");
        mav.setViewName("display-status");
        return mav;
    }

    @RequestMapping("/setOffLED0")
    public ModelAndView setOffLED0() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView();
        serialPortHandler.state_led0 = false;
        mav.addObject("statusString", "success");
        mav.setViewName("display-status");
        return mav;
    }

    @RequestMapping("/setOffLED1")
    public ModelAndView setOffLED1() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView();
        serialPortHandler.state_led1 = false;
        mav.addObject("statusString", "success");
        mav.setViewName("display-status");
        return mav;
    }

    @RequestMapping("/setOffLED2")
    public ModelAndView setOffLED2() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView();
        serialPortHandler.state_led2 = false;
        mav.addObject("statusString", "success");
        mav.setViewName("display-status");
        return mav;
    }

    @RequestMapping("/setOffLED3")
    public ModelAndView setOffLED3() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView();
        serialPortHandler.state_led3 = false;
        mav.addObject("statusString", "success");
        mav.setViewName("display-status");
        return mav;
    }
}
