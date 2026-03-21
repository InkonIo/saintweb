package com.schedule.app.ai.dto;

import lombok.Data;

@Data
public class AiFillRequest {
    private Long scheduleId;
    private String instructions;
}