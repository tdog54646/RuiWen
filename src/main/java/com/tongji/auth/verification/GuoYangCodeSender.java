package com.tongji.auth.verification;

import com.tongji.auth.util.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import java.util.HashMap;
import java.util.Map;


/**
 * 验证码发送器接口。
 * 对接阿里云
 */
@Slf4j
@Component
public class GuoYangCodeSender implements CodeSender{

    @Value("${code.send.guo-yang.code-key}")
    static String appCode;

    /**
     * 记录验证码发送信息到日志。
     *
     * @param scene         验证码场景。
     * @param identifier    标识（手机号或邮箱）。
     * @param code          验证码内容。
     * @param expireMinutes 有效期（分钟）。
     */
    @Override
    public void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes) {



        log.info("Send verification code scene={} identifier={} code={} expireMinutes={}", scene, identifier, code, expireMinutes);
            String host = "https://gyytz.market.alicloudapi.com";
            String path = "/sms/smsSend";
            String method = "POST";
            String appcode = appCode;
            Map<String, String> headers = new HashMap<String, String>();
            //最后在header中的格式(中间是英文空格)为Authorization:APPCODE 83359fd73fe94948385f570e3c139105
            headers.put("Authorization", "APPCODE " + appcode);
            Map<String, String> querys = new HashMap<String, String>();
            querys.put("mobile", identifier);
             String format = String.format("**code**:{},**minute**:{}", code, expireMinutes);
            querys.put("param", format);

//smsSignId（短信前缀）和templateId（短信模板），可登录国阳云控制台自助申请。参考文档：http://help.guoyangyun.com/Problem/Qm.html

            querys.put("smsSignId", "2e65b1bb3d054466b82f0c9d125465e2");
            querys.put("templateId", "908e94ccf08b4476ba6c876d13f084ad");
            Map<String, String> bodys = new HashMap<String, String>();


            try {
                /**
                 * 重要提示如下:
                 * HttpUtils请从\r\n\t    \t* https://github.com/aliyun/api-gateway-demo-sign-java/blob/master/src/main/java/com/aliyun/api/gateway/demo/util/HttpUtils.java\r\n\t    \t* 下载
                 *
                 * 相应的依赖请参照
                 * https://github.com/aliyun/api-gateway-demo-sign-java/blob/master/pom.xml
                 */
                HttpResponse response = HttpUtils.doPost(host, path, method, headers, querys, bodys);
                log.info("验证码response："+ EntityUtils.toString(response.getEntity()));
                //获取response的body
                //System.out.println(EntityUtils.toString(response.getEntity()));
            } catch (Exception e) {
                e.printStackTrace();
            }

    }
}
