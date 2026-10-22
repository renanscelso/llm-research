package br.com.renan.llmresearch.provider;

import org.springframework.ai.chat.model.ChatModel;

/** Adaptador Google Gemini. */
public class GeminiClient extends ChatModelLlmClient {
    public GeminiClient(String model, ChatModel chatModel) {
        super("gemini", model, chatModel);
    }
}
