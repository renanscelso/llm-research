package br.com.renan.llmresearch.provider;

import org.springframework.ai.chat.model.ChatModel;

/** Adaptador Llama via endpoint compatível com OpenAI. */
public class LlamaClient extends ChatModelLlmClient {
    public LlamaClient(String model, ChatModel chatModel) {
        super("llama", model, chatModel);
    }
}
