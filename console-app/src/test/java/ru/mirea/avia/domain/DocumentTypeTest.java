package ru.mirea.avia.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет форматы, нормализацию и маскирование номеров документов (NFR-09). */
class DocumentTypeTest {
    @Test
    void acceptsNumbersFromDemoDataset() {
        assertThat(DocumentType.PASSPORT_RF.matches("4510123456")).isTrue();
        assertThat(DocumentType.PASSPORT_RF.matches("45 10 123456")).isTrue();
        assertThat(DocumentType.INTERNATIONAL_PASSPORT.matches("750123456")).isTrue();
        assertThat(DocumentType.INTERNATIONAL_PASSPORT.matches("75 0123456")).isTrue();
        assertThat(DocumentType.BIRTH_CERTIFICATE.matches("VIII-MU-123456")).isTrue();
        assertThat(DocumentType.BIRTH_CERTIFICATE.matches("iv-мю-654321")).isTrue();
        assertThat(DocumentType.FOREIGN_DOCUMENT.matches("C01X00T47")).isTrue();
        assertThat(DocumentType.FOREIGN_DOCUMENT.matches("ab-12345")).isTrue();
    }

    @Test
    void rejectsWrongFormats() {
        assertThat(DocumentType.PASSPORT_RF.matches("45101234")).isFalse();
        assertThat(DocumentType.PASSPORT_RF.matches("45AB123456")).isFalse();
        assertThat(DocumentType.PASSPORT_RF.matches(null)).isFalse();
        assertThat(DocumentType.INTERNATIONAL_PASSPORT.matches("4510123456")).isFalse();
        assertThat(DocumentType.BIRTH_CERTIFICATE.matches("8-MU-123456")).isFalse();
        assertThat(DocumentType.BIRTH_CERTIFICATE.matches("VIIII-MU-123456")).isFalse();
        assertThat(DocumentType.BIRTH_CERTIFICATE.matches("VIII-MU-12345")).isFalse();
        assertThat(DocumentType.FOREIGN_DOCUMENT.matches("AB1")).isFalse();
        assertThat(DocumentType.FOREIGN_DOCUMENT.matches("A".repeat(21))).isFalse();
    }

    @Test
    void normalizesToStoredForm() {
        assertThat(DocumentType.PASSPORT_RF.normalize(" 45 10-123456 ")).isEqualTo("4510123456");
        assertThat(DocumentType.INTERNATIONAL_PASSPORT.normalize("75 0123456")).isEqualTo("750123456");
        assertThat(DocumentType.BIRTH_CERTIFICATE.normalize("viii-mu-123456")).isEqualTo("VIII-MU-123456");
        assertThat(DocumentType.BIRTH_CERTIFICATE.normalize("iv-мю- 654321")).isEqualTo("IV-МЮ-654321");
        assertThat(DocumentType.FOREIGN_DOCUMENT.normalize("c01x 00t-47")).isEqualTo("C01X00T47");
        assertThat(DocumentType.FOREIGN_DOCUMENT.normalize(null)).isEmpty();
    }

    @Test
    void masksDocumentNumbers() {
        assertThat(DocumentType.PASSPORT_RF.mask("4512889967")).isEqualTo("45 12 ****67");
        assertThat(DocumentType.PASSPORT_RF.mask("4510123456")).isEqualTo("45 10 ****56");
        assertThat(DocumentType.INTERNATIONAL_PASSPORT.mask("750123456")).isEqualTo("75*****56");
        assertThat(DocumentType.BIRTH_CERTIFICATE.mask("VIII-MU-123456")).isEqualTo("VIII-MU-****56");
        assertThat(DocumentType.FOREIGN_DOCUMENT.mask("C01X00T47")).isEqualTo("C0*****47");
    }

    @Test
    void masksShortOrMissingNumberCompletely() {
        assertThat(DocumentType.FOREIGN_DOCUMENT.mask("AB12")).isEqualTo("****");
        assertThat(DocumentType.PASSPORT_RF.mask(null)).isEqualTo("****");
    }

    @Test
    void formatsFullPassportNumber() {
        assertThat(DocumentType.PASSPORT_RF.format("4510123456")).isEqualTo("4510 123456");
        assertThat(DocumentType.PASSPORT_RF.format("45101234")).isEqualTo("45101234");
        assertThat(DocumentType.INTERNATIONAL_PASSPORT.format("750123456")).isEqualTo("750123456");
        assertThat(DocumentType.BIRTH_CERTIFICATE.format("VIII-MU-123456")).isEqualTo("VIII-MU-123456");
        assertThat(DocumentType.FOREIGN_DOCUMENT.format("C01X00T47")).isEqualTo("C01X00T47");
        assertThat(DocumentType.PASSPORT_RF.format(null)).isNull();
    }
}
