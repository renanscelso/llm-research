package br.com.renan.llmresearch.provider;

import org.springframework.ai.chat.model.ChatModel;

/** Adaptador xAI Grok (API compatível com OpenAI). */
public class GrokClient extends ChatModelLlmClient {
    public GrokClient(String model, ChatModel chatModel) {
        super("grok", model, chatModel);
    }
}
