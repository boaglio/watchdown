package com.boaglio.watchdown.summarize;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** A {@link ChatModel} that returns canned answers, so the tests never reach a real Ollama. */
class StubChatModel implements ChatModel {

    private final Deque<String> answers = new ArrayDeque<>();
    private final List<String> prompts = new ArrayList<>();

    StubChatModel answering(String... canned) {
        answers.addAll(List.of(canned));
        return this;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt.getContents());
        String answer = answers.isEmpty() ? "" : answers.poll();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
    }

    List<String> prompts() {
        return List.copyOf(prompts);
    }

    int callCount() {
        return prompts.size();
    }
}
