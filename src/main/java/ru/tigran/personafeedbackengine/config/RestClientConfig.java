package ru.tigran.personafeedbackengine.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Конфигурация для HTTP клиентов (как синхронные RestClient, так и асинхронные WebClient)
 */
@Configuration
public class RestClientConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Конфигурирует асинхронный WebClient с timeouts
     * Используется для асинхронных HTTP запросов к AI провайдерам
     */
    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .responseTimeout(TIMEOUT)
                .doOnConnected(conn ->
                    conn.addHandlerLast(new ReadTimeoutHandler(TIMEOUT.getSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(TIMEOUT.getSeconds(), TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * Конфигурирует синхронный RestClient для обратной совместимости
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestFactory(new org.springframework.http.client.ClientHttpRequestFactory() {
                    private final org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                            new org.springframework.http.client.SimpleClientHttpRequestFactory();

                    @Override
                    public org.springframework.http.client.ClientHttpRequest createRequest(java.net.URI uri, org.springframework.http.HttpMethod httpMethod) throws java.io.IOException {
                        factory.setConnectTimeout((int) TIMEOUT.toMillis());
                        factory.setReadTimeout((int) TIMEOUT.toMillis());
                        return factory.createRequest(uri, httpMethod);
                    }
                });
    }

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
