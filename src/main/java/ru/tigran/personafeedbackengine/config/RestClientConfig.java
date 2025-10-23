package ru.tigran.personafeedbackengine.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestFactory(new org.springframework.http.client.ClientHttpRequestFactory() {
                    private final org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                            new org.springframework.http.client.SimpleClientHttpRequestFactory();

                    @Override
                    public org.springframework.http.client.ClientHttpRequest createRequest(java.net.URI uri, org.springframework.http.HttpMethod httpMethod) throws java.io.IOException {
                        factory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
                        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
                        return factory.createRequest(uri, httpMethod);
                    }
                });
    }

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
