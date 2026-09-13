package com.omnicare.platform.conversation.prompt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the prompt pieces together. */
@Configuration
class PromptConfig {

    @Bean
    TokenEstimator tokenEstimator() {
        return new TokenEstimator();
    }

    /**
     * @param tokenBudget how much of the model's context window a prompt may
     *                    use. Set well below the model's actual limit: the
     *                    estimate is approximate and under-counts non-Latin
     *                    scripts, and the answer needs room too — the budget
     *                    covers only what is sent.
     */
    @Bean
    PromptBuilder promptBuilder(@Value("${omnicare.llm.token-budget:8000}") int tokenBudget,
                                TokenEstimator estimator) {
        return new PromptBuilder(SystemPrompt.current(), tokenBudget, estimator);
    }
}
