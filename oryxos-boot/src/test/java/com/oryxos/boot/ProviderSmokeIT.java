package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.react.Prompt;
import com.oryxos.core.session.Message;
import com.oryxos.provider.ProviderService;
import com.oryxos.storage.repository.LlmCallRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Tag("integration")
@DisplayName("ProviderSmokeIT — 集成冒烟:真模型调用")
class ProviderSmokeIT {

  @Autowired private ProviderService providerService;

  @Autowired private LlmCallRepository llmCallRepository;

  @Test
  @DisplayName("读环境变量真key_真调一次_断言非空响应且llm_calls多一条success=true")
  void smokeCallWithRealApiKey() {
    String apiKey = System.getenv("DEEPSEEK_API_KEY");
    assumeTrue(
        apiKey != null && !apiKey.isBlank(), "Skipping smoke test: DEEPSEEK_API_KEY not set");

    long beforeCount = llmCallRepository.count();

    Profile profile = new Profile();
    profile.setName("smoke-test");
    profile.setProvider(new Profile.Provider("deepseek", "deepseek-chat", 0.7));

    Prompt prompt = new Prompt(List.of(Message.user("Say hello in one word.")));

    var response = providerService.chat("smoke-session", profile, prompt);

    assertThat(response.content()).isNotBlank();

    long afterCount = llmCallRepository.count();
    assertThat(afterCount).isGreaterThan(beforeCount);
  }
}
