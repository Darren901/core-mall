package com.coremall.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("DiscoveryService - 應用程式啟動")
class DiscoveryServiceApplicationTest {

    @Test
    @DisplayName("Spring Context 正常載入")
    void contextLoads() {
    }
}
