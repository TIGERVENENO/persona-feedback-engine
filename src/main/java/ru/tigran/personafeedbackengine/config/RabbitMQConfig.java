package ru.tigran.personafeedbackengine.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PERSONA_GENERATION_QUEUE = "persona.generation.queue";
    public static final String FEEDBACK_GENERATION_QUEUE = "feedback.generation.queue";
    public static final String EXCHANGE_NAME = "persona-feedback-exchange";

    @Bean
    public Queue personaGenerationQueue() {
        return new Queue(PERSONA_GENERATION_QUEUE, true);
    }

    @Bean
    public Queue feedbackGenerationQueue() {
        return new Queue(FEEDBACK_GENERATION_QUEUE, true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Binding personaBinding(Queue personaGenerationQueue, DirectExchange exchange) {
        return BindingBuilder.bind(personaGenerationQueue)
                .to(exchange)
                .with("persona.generation");
    }

    @Bean
    public Binding feedbackBinding(Queue feedbackGenerationQueue, DirectExchange exchange) {
        return BindingBuilder.bind(feedbackGenerationQueue)
                .to(exchange)
                .with("feedback.generation");
    }
}
