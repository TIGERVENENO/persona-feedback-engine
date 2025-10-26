package ru.tigran.personafeedbackengine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Saga Service для обработки длительных процессов с поддержкой откатов
 * Реализует Saga pattern для обеспечения согласованности данных при частичных сбоях
 */
@Slf4j
@Service
public class SagaService {

    /**
     * Выполняет серию операций с поддержкой компенсирующих действий (откатов)
     * Если какая-то операция упадет, будут выполнены все компенсирующие действия в обратном порядке
     *
     * @param operations список операций для выполнения (в порядке выполнения)
     * @throws Exception если операция не успешна и компенсирующие действия также не помогли
     */
    public void executeSaga(List<SagaOperation> operations) {
        List<SagaCompensation> compensations = new ArrayList<>();

        try {
            for (SagaOperation operation : operations) {
                log.debug("Executing saga operation: {}", operation.getName());

                try {
                    // Выполняем операцию
                    operation.execute();
                    log.debug("Saga operation succeeded: {}", operation.getName());

                    // Если у операции есть компенсирующее действие, добавляем его
                    if (operation.getCompensation() != null) {
                        compensations.add(operation.getCompensation());
                    }
                } catch (Exception e) {
                    log.error("Saga operation failed: {}", operation.getName(), e);

                    // Откатываем все успешные операции в обратном порядке
                    rollback(compensations);
                    throw e;
                }
            }

            log.info("Saga completed successfully with {} operations", operations.size());
        } catch (Exception e) {
            log.error("Saga execution failed", e);
            throw new RuntimeException("Saga execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Откатывает операции в обратном порядке
     */
    private void rollback(List<SagaCompensation> compensations) {
        log.info("Rolling back {} compensations", compensations.size());

        // Выполняем компенсирующие действия в обратном порядке
        Collections.reverse(compensations);

        for (SagaCompensation compensation : compensations) {
            try {
                log.debug("Executing compensation: {}", compensation.getName());
                compensation.execute();
                log.debug("Compensation succeeded: {}", compensation.getName());
            } catch (Exception e) {
                log.error("Compensation failed: {} - This is critical!", compensation.getName(), e);
                // Продолжаем откат несмотря на ошибку, но логируем её
            }
        }
    }

    /**
     * Интерфейс для операции в saga
     */
    public interface SagaOperation {
        void execute() throws Exception;
        String getName();
        SagaCompensation getCompensation();
    }

    /**
     * Интерфейс для компенсирующего действия (отката)
     */
    public interface SagaCompensation {
        void execute() throws Exception;
        String getName();
    }

    /**
     * Простая реализация SagaOperation
     */
    public static class SimpleSagaOperation implements SagaOperation {
        private final String name;
        private final Runnable action;
        private final SagaCompensation compensation;

        public SimpleSagaOperation(String name, Runnable action, SagaCompensation compensation) {
            this.name = name;
            this.action = action;
            this.compensation = compensation;
        }

        @Override
        public void execute() throws Exception {
            action.run();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public SagaCompensation getCompensation() {
            return compensation;
        }
    }

    /**
     * Простая реализация SagaCompensation
     */
    public static class SimpleSagaCompensation implements SagaCompensation {
        private final String name;
        private final Runnable action;

        public SimpleSagaCompensation(String name, Runnable action) {
            this.name = name;
            this.action = action;
        }

        @Override
        public void execute() throws Exception {
            action.run();
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
