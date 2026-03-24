package com.schedule.app.ai.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Utility for streaming text word-by-word over SSE.
 * Sends each word followed by a space so the frontend
 * can simply concatenate tokens without any extra logic.
 */
public class SseTextStreamer {

    private SseTextStreamer() {}

    /**
     * Splits {@code text} by whitespace and sends each word
     * as a separate SSE "text" event with a trailing space.
     *
     * @param text    the full response text to stream
     * @param emitter the active SSE emitter
     * @param delayMs pause between tokens in milliseconds (e.g. 20)
     */
    public static void stream(String text, SseEmitter emitter, long delayMs) throws Exception {
        if (text == null || text.isBlank()) return;

        String[] words = text.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) continue;

            // Every word gets a trailing space so tokens concatenate naturally:
            // "Привет " + "мир " → "Привет мир "
            String token = word + " ";

            emitter.send(SseEmitter.event().name("text").data(token));

            if (delayMs > 0) Thread.sleep(delayMs);
        }
    }
}