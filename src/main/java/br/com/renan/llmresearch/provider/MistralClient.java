package br.com.renan.llmresearch.provider;

import org.springframework.ai.chat.model.ChatModel;

/** Adaptador Mistral AI. */
public class MistralClient extends ChatModelLlmClient {
    public MistralClient(String model, ChatModel chatModel) {
        super("mistral", model, chatModel);
    }
}
