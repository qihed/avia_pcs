package ru.mirea.avia.dto;

import jakarta.validation.constraints.*;

public record NoteRequest(
  @NotBlank @Size(max = 2000) String text,
  boolean pinned
) {}
