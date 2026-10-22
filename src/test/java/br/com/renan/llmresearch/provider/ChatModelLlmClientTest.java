package br.com.renan.llmresearch.provider;

import br.com.renan.llmresearch.model.Question;
import br.com.renan.llmresearch.model.ResearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatModelLlmClientTest {

    @Test
    void returnsSuccessWithUsageMetadata() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse response = ChatResponse.builder()
                .generations(java.util.List.of(new Generation(new AssistantMessage("Olá, saúde!"))))
                .metadata(ChatResponseMetadata.builder()
                        .model("gpt-4.1-mini")
                        .usage(new DefaultUsage(11, 22))
                        .build())
                .build();
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        LlmClient client = new OpenAiClient("gpt-4.1-mini", chatModel);
        ResearchResult result = client.ask(new Question(1, "Pergunta"), "exec", 1);

        assertTrue(result.isSuccess());
        assertEquals("Olá, saúde!", result.response());
        assertEquals(11, result.inputTokens());
        assertEquals(22, result.outputTokens());
        assertEquals("gpt-4.1-mini", result.model());
    }

    @Test
    void returnsErrorWithoutInterruptingCaller() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 500 internal server error"));

        LlmClient client = new AnthropicClient("claude", chatModel);
        ResearchResult result = client.ask(new Question(2, "Pergunta"), "exec", 1);

        assertEquals(ResearchResult.STATUS_ERROR, result.status());
        assertTrue(result.error().contains("5xx"));
    }

    @Test
    void usesLastNonBlankGenerationWhenThinkingComesFirst() {
        ChatModel chatModel = mock(ChatModel.class);
        AssistantMessage thinking = AssistantMessage.builder()
                .content("")
                .properties(java.util.Map.of("signature", "sig"))
                .build();
        ChatResponse response = ChatResponse.builder()
                .generations(java.util.List.of(
                        new Generation(thinking),
                        new Generation(new AssistantMessage("Resposta final do Claude."))
                ))
                .metadata(ChatResponseMetadata.builder().model("claude-sonnet-5-5").build())
                .build();
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        LlmClient client = new AnthropicClient("claude-sonnet-5-5", chatModel);
        ResearchResult result = client.ask(new Question(1, "Pergunta"), "exec", 1);

        assertTrue(result.isSuccess());
        assertEquals("Resposta final do Claude.", result.response());
    }
}
