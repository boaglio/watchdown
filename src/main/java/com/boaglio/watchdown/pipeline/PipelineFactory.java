package com.boaglio.watchdown.pipeline;

import com.boaglio.watchdown.cli.ConsoleReporter;
import com.boaglio.watchdown.cli.Version;
import com.boaglio.watchdown.config.OllamaConfig;
import com.boaglio.watchdown.config.WatchdownConfig;
import com.boaglio.watchdown.download.MediaProbe;
import com.boaglio.watchdown.download.YtDlpDownloader;
import com.boaglio.watchdown.process.ProcessRunner;
import com.boaglio.watchdown.render.MarkdownRenderer;
import com.boaglio.watchdown.summarize.OllamaSummarizer;
import com.boaglio.watchdown.transcribe.CaptionParser;
import com.boaglio.watchdown.transcribe.WhisperTranscriber;
import java.time.Clock;
import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds the per-run objects once the configuration is resolved.
 *
 * <p>The resolved config is the single source of truth for Ollama too: the chat model and its
 * options are built from {@link OllamaConfig} here, not from {@code spring.ai.ollama.*}
 * properties, so that {@code --model} and the config file actually take effect.
 */
@Component
public class PipelineFactory {

    private final ProcessRunner runner;
    private final tools.jackson.databind.json.JsonMapper mapper;
    private final Clock clock;
    private final Resource chunkPrompt;
    private final Resource finalPrompt;
    private final Resource sectionsPrompt;

    public PipelineFactory(ProcessRunner runner,
            tools.jackson.databind.json.JsonMapper mapper,
            Clock clock,
            @Value("classpath:prompts/chunk-summary.st") Resource chunkPrompt,
            @Value("classpath:prompts/final-summary.st") Resource finalPrompt,
            @Value("classpath:prompts/sections-retry.st") Resource sectionsPrompt) {
        this.runner = runner;
        this.mapper = mapper;
        this.clock = clock;
        this.chunkPrompt = chunkPrompt;
        this.finalPrompt = finalPrompt;
        this.sectionsPrompt = sectionsPrompt;
    }

    public Pipeline create(WatchdownConfig config, ConsoleReporter reporter, boolean force) {
        return new Pipeline(
                new YtDlpDownloader(runner, mapper, config.ytDlp()),
                new WhisperTranscriber(runner, mapper, config.whisper()),
                new CaptionParser(mapper),
                new MediaProbe(runner),
                new OllamaSummarizer(chatClient(config), chunkPrompt, finalPrompt, sectionsPrompt, config.summary()),
                new MarkdownRenderer(clock),
                new Cache(config.cacheDir(), force),
                config,
                reporter,
                mapper,
                Version.current());
    }

    public Doctor doctor(WatchdownConfig config) {
        return new Doctor(runner, ollamaApi(config.ollama()), config);
    }

    public OllamaApi ollamaApi(OllamaConfig config) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(config.timeout());
        return OllamaApi.builder()
                .baseUrl(config.baseUrl())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
    }

    private ChatClient chatClient(WatchdownConfig config) {
        OllamaConfig ollama = config.ollama();
        OllamaChatOptions.Builder options = OllamaChatOptions.builder();
        options.model(ollama.model());
        options.temperature(ollama.temperature());
        options.numCtx(ollama.numCtx());
        OllamaChatModel model = OllamaChatModel.builder()
                .ollamaApi(ollamaApi(ollama))
                .options(options.build())
                .build();
        return ChatClient.builder(model).build();
    }
}
