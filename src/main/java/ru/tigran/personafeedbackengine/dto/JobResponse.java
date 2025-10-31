package ru.tigran.personafeedbackengine.dto;

import java.util.List;

/**
 * Generic response for asynchronous job operations.
 *
 * Supports both single and batch operations:
 * - Batch persona generation: jobIds contains multiple persona IDs
 * - Single feedback session: jobIds contains single session ID
 *
 * @param jobIds List of job IDs (persona IDs for batch generation, or List.of(sessionId) for feedback)
 * @param status Initial job status (e.g., "GENERATING", "PENDING", "COMPLETED")
 */
public record JobResponse(
        List<Long> jobIds,
        String status
) {
    /**
     * Convenience constructor for single job ID (e.g., feedback session).
     * Wraps the ID in a list for consistency.
     *
     * @param jobId Single job ID
     * @param status Job status
     * @return JobResponse with jobIds containing single ID
     */
    public static JobResponse single(Long jobId, String status) {
        return new JobResponse(List.of(jobId), status);
    }

    /**
     * Gets the first job ID (useful for backward compatibility or single-job operations).
     *
     * @return First job ID from the list
     * @throws IndexOutOfBoundsException if jobIds is empty
     */
    public Long getFirstJobId() {
        return jobIds.get(0);
    }
}
