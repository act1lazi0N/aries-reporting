package com.actilazion.ariesreportingproject;

import com.actilazion.ariesreportingproject.support.ApplicationTestPropertiesInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = ApplicationTestPropertiesInitializer.class)
class AriesReportingProjectApplicationTests {

    @Test
    void contextLoads() {
    }

}
