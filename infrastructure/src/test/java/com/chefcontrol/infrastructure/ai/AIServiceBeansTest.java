package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.port.AIService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los adaptadores de IA se crean según qué API key haya, así que en local (Gemini) nunca se instancia el de
 * Claude: un constructor que Spring no sabe elegir recién explota en staging. Esto los arma como lo hace el
 * component scan (withUserConfiguration, no withBean, que elige constructor de otra forma).
 */
class AIServiceBeansTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class)
            .withUserConfiguration(ClaudeAIService.class, GemmaAIService.class);

    @Test
    void withAnthropicKeyCreatesClaude() {
        runner.withPropertyValues("app.anthropic.api-key=relleno", "app.google.api-key=relleno")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).getBean(AIService.class).isInstanceOf(ClaudeAIService.class);
                });
    }

    @Test
    void withOnlyGoogleKeyCreatesGemma() {
        runner.withPropertyValues("app.google.api-key=relleno")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).getBean(AIService.class).isInstanceOf(GemmaAIService.class);
                });
    }
}
