package ru.tigran.personafeedbackengine.dto;

public record SessionStatusInfo(
    long completed,
    long failed,
    long total
) {}
