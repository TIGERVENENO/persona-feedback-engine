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

    public static final String PERSONA_DLQ = "persona.generation.dlq";
    public static final String FEEDBACK_DLQ = "feedback.generation.dlq";
    public static final String DLX_EXCHANGE_NAME = "persona-feedback-dlx";

    @Bean
    public Queue personaGenerationQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(PERSONA_GENERATION_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE_NAME)
                .deadLetterRoutingKey("persona.generation.dlq")
                .build();
    }

    @Bean
    public Queue feedbackGenerationQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(FEEDBACK_GENERATION_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE_NAME)
                .deadLetterRoutingKey("feedback.generation.dlq")
                .build();
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue personaDLQ() {
        return new Queue(PERSONA_DLQ, true);
    }

    @Bean
    public Queue feedbackDLQ() {
        return new Queue(FEEDBACK_DLQ, true);
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

    @Bean
    public Binding personaDLQBinding(Queue personaDLQ, DirectExchange dlxExchange) {
        return BindingBuilder.bind(personaDLQ)
                .to(dlxExchange)
                .with("persona.generation.dlq");
    }

    @Bean
    public Binding feedbackDLQBinding(Queue feedbackDLQ, DirectExchange dlxExchange) {
        return BindingBuilder.bind(feedbackDLQ)
                .to(dlxExchange)
                .with("feedback.generation.dlq");
    }
}
