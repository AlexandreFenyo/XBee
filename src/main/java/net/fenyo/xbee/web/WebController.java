package net.fenyo.xbee.web;

import java.io.IOException;

import javax.servlet.ServletContext;

import net.fenyo.xbee.serial.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

// excellente doc sur les controller : "16.3 Implementing Controllers" de spring-framework-reference.pdf
@Controller("webController")
public class WebController {
    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    private ServletContext context;

    @Autowired
    private SerialPortHandler serialPortHandler;

    @RequestMapping("/intro")
    public ModelAndView intro(@RequestParam(value = "statusString", required = false) final String status_string) {
        ModelAndView mav = new ModelAndView();
        mav.addObject("statusString", status_string);
        mav.setViewName("say-hello");
        return mav;
    }

    @RequestMapping("/sendFiles")
    public ModelAndView sendFiles(@RequestParam(value = "param", required = true) final String param)
            throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView();
        boolean success = true;

        final String[] params = param.split(" ");
        int argcnt = 1;
        while (argcnt + 1 < params.length) {
            final boolean ret = serialPortHandler.sendFile(context.getRealPath("WEB-INF/" + params[argcnt]),
                    new Integer(params[argcnt + 1]));
            if (ret == false) {
                log.error("can not send file " + params[argcnt]);
                success = false;
                break;
            }
            argcnt += 2;
        }
        ;

        mav.addObject("statusString", success ? "success" : "error");
        mav.setViewName("send-files");
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
        mav.setViewName("send-remote-at");
        return mav;
    }

    @RequestMapping("/sendRemoteATAlexNoAck")
    public ModelAndView sendRemoteATAlexNoAck(@RequestParam(value = "param", required = true) final String param)
            throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView();

        serialPortHandler.sendRemoteATCommandFrameSingleQueryAck(0x13a200, 0x409b7abb, param.substring(0, 2)
                + new String(new byte[] { (byte) (param.charAt(2) - new Character('0').charValue()) }), false);
        mav.addObject("statusString", "success");
        mav.setViewName("send-remote-at");
        return mav;
    }

    @RequestMapping("/sendRemoteATAlex")
    public ModelAndView sendRemoteATAlex(@RequestParam(value = "param", required = true) final String param)
            throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView();

        boolean done = false;
        for (int retries = 0; retries < 3; retries++) {
            if (retries > 0)
                log.warn("retry nb " + retries);
            byte[] ret = serialPortHandler.sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409b7abb,
                    param.substring(0, 2)
                            + new String(new byte[] { (byte) (param.charAt(2) - new Character('0').charValue()) }));

            if (ret == null)
                log.warn("timeout sending remote AT command to Chambre Alex");
            else {
                ret = serialPortHandler.sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409b7abb,
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
        mav.setViewName("send-remote-at");
        return mav;
    }

    @RequestMapping("/shutdown")
    public ModelAndView shutdown() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView("redirect:intro");
        mav.addObject("statusString", "");
        Runtime.getRuntime().exec(new String[] { "shutdown", "/s", "/t", "20" });
        return mav;
    }

    @RequestMapping("/reboot")
    public ModelAndView reboot() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView("redirect:intro");
        mav.addObject("statusString", "");
        Runtime.getRuntime().exec(new String[] { "shutdown", "/r" });
        return mav;
    }

    @RequestMapping("/restartvnc")
    public ModelAndView restartvnc() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView("redirect:intro");
        mav.addObject("statusString", "");
        Runtime.getRuntime().exec(new String[] { "d:\\alex\\bin\\restartvnc.bat" });
        return mav;
    }

    @RequestMapping("/checkAT")
    public ModelAndView checkAT() throws IOException, InterruptedException {
        // log.debug("check AT called");

        ModelAndView mav = new ModelAndView("redirect:intro");
        mav.addObject("statusString", serialPortHandler.checkAT());
        return mav;
    }

    @RequestMapping("/checkAT2")
    public ModelAndView checkAT2() throws IOException, InterruptedException {
        // log.debug("check AT called");

        ModelAndView mav = new ModelAndView("redirect:intro");
        mav.addObject("statusString", serialPortHandler.checkAT2());
        return mav;
    }

    @RequestMapping("/checkAT3")
    public ModelAndView checkAT3() throws IOException, InterruptedException {
        // log.debug("check AT called");

        ModelAndView mav = new ModelAndView("redirect:intro");
        mav.addObject("statusString", serialPortHandler.checkAT3());
        return mav;
    }

    @RequestMapping("/checkAT4")
    public ModelAndView checkAT4() throws IOException, InterruptedException {
        // log.debug("check AT called");

        ModelAndView mav = new ModelAndView("redirect:intro");
        mav.addObject("statusString", serialPortHandler.checkAT4());
        return mav;
    }

    @RequestMapping("/checkAT5")
    public ModelAndView checkAT5() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView("redirect:intro");
        mav.addObject("statusString", serialPortHandler.checkAT5());
        return mav;
    }

    @RequestMapping("/checkAT6")
    public ModelAndView checkAT6() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView("redirect:intro");
        mav.addObject("statusString", serialPortHandler.checkAT6());
        return mav;
    }

    @RequestMapping("/checkAT7")
    public ModelAndView checkAT7() throws IOException, InterruptedException {
        ModelAndView mav = new ModelAndView("redirect:intro");
        mav.addObject("statusString", serialPortHandler.checkAT7());
        return mav;
    }
}
