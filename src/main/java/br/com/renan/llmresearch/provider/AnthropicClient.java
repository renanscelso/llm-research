package br.com.renan.llmresearch.provider;

import org.springframework.ai.chat.model.ChatModel;

/** Adaptador Anthropic / Claude. */
public class AnthropicClient extends ChatModelLlmClient {
    public AnthropicClient(String model, ChatModel chatModel) {
        super("anthropic", model, chatModel);
    }
}
