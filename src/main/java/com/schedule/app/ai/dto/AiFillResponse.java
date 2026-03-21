package com.schedule.app.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiFillResponse {
    private int filledCells;
    private String summary; // "Иванов: 22р.д., Петрова: декрет..."
}