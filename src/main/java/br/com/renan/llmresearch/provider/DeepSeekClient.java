package br.com.renan.llmresearch.provider;

import org.springframework.ai.chat.model.ChatModel;

/** Adaptador DeepSeek via endpoint compatível com OpenAI. */
public class DeepSeekClient extends ChatModelLlmClient {
    public DeepSeekClient(String model, ChatModel chatModel) {
        super("deepseek", model, chatModel);
    }
}
