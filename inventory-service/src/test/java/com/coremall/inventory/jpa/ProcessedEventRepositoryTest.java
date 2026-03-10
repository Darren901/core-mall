package com.coremall.inventory.jpa;

import com.coremall.inventory.jpa.entity.ProcessedEvent;
import com.coremall.inventory.jpa.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("ProcessedEventRepository - 冪等事件記錄")
class ProcessedEventRepositoryTest {

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("儲存 ProcessedEvent 後，existsById 返回 true")
    void shouldReturnTrueAfterSaving() {
        processedEventRepository.save(ProcessedEvent.of("msg-001"));

        assertThat(processedEventRepository.existsById("msg-001")).isTrue();
    }

    @Test
    @DisplayName("未儲存的 messageId，existsById 返回 false")
    void shouldReturnFalseForUnknownId() {
        assertThat(processedEventRepository.existsById("msg-unknown")).isFalse();
    }
}
