package br.com.renan.llmresearch.provider;

import org.springframework.ai.chat.model.ChatModel;

/** Adaptador Qwen via endpoint compatível com OpenAI. */
public class QwenClient extends ChatModelLlmClient {
    public QwenClient(String model, ChatModel chatModel) {
        super("qwen", model, chatModel);
    }
}
