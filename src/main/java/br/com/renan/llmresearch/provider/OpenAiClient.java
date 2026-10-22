package br.com.renan.llmresearch.provider;

import org.springframework.ai.chat.model.ChatModel;

/** Adaptador OpenAI / ChatGPT via API. */
public class OpenAiClient extends ChatModelLlmClient {
    public OpenAiClient(String model, ChatModel chatModel) {
        super("openai", model, chatModel);
    }
}
