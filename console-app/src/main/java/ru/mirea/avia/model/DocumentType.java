package ru.mirea.avia.model;

/** Тип документа пассажира. */
public enum DocumentType {
    PASSPORT_RF("паспорт РФ"),
    INTERNATIONAL_PASSPORT("загранпаспорт"),
    BIRTH_CERTIFICATE("свидетельство о рождении"),
    FOREIGN_DOCUMENT("иностранный документ");

    private final String title;

    DocumentType(String title) {
        this.title = title;
    }

    public String getTitle() { return title; }
}
