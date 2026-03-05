package com.schedule.app.enums;

public enum ShiftType {
    // Рабочие смены
    SHIFT_9_18("9-18"),
    SHIFT_9_21("9-21"),
    SHIFT_8_17("8-17"),
    SHIFT_8_20("8-20"),
    // Нерабочие
    V("В"),       // Выходной
    O("О"),       // Отпуск
    B("Б"),       // Больничный
    BS("БС"),     // Без содержания
    K("К"),       // Командировка
    D("Д");       // Декрет

    private final String code;

    ShiftType(String code) { this.code = code; }

    public String getCode() { return code; }

    public static ShiftType fromCode(String code) {
        for (ShiftType type : values()) {
            if (type.code.equalsIgnoreCase(code)) return type;
        }
        throw new IllegalArgumentException("Invalid shift type: " + code);
    }
}