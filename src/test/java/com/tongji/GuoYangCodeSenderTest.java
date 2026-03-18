package com.tongji;

import com.tongji.auth.verification.CodeSender;
import com.tongji.auth.verification.VerificationScene;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class GuoYangCodeSenderTest {

    @Autowired
    private CodeSender codeSender;


    @Test
    public void sendCodeTest() {
            codeSender.sendCode(VerificationScene.LOGIN,"18851867010","123456",5);
    }
}
