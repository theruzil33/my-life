package com.theruzil.tracker.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.theruzil.tracker.habit.ActivityType;

class ProductivityCsvParserTest {

    private final ProductivityCsvParser parser = new ProductivityCsvParser();

    @Test
    void recognizesActivityTypes() {
        List<ImportActivity> activities = parse(
                "Чистил зубы,Утром,Привычка,1",
                "Вузовские дела,,Задача,1");

        assertThat(activities).extracting(ImportActivity::type).containsExactly(ActivityType.HABIT, ActivityType.TASK);
    }

    @Test
    void skipsHeaderAndTotalsRows() {
        List<ImportActivity> activities = parse(
                "Задачи,,,1,2,3",
                ",,,0,0,0",
                "Чистил зубы,Утром,Привычка,,,1");

        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).name()).isEqualTo("Чистил зубы (Утром)");
    }

    @Test
    void emptyNameContinuesPreviousActivity() {
        List<ImportActivity> activities = parse(
                "Чистил зубы,Утром,Привычка,1",
                ",Вечером,Привычка,1");

        assertThat(activities).extracting(ImportActivity::name)
                .containsExactly("Чистил зубы (Утром)", "Чистил зубы (Вечером)");
    }

    @Test
    void variantIsIncludedInName() {
        List<ImportActivity> activities = parse("Крем для рук,Вечером,Привычка,1");

        assertThat(activities.get(0).name()).isEqualTo("Крем для рук (Вечером)");
    }

    @Test
    void activityWithoutVariantUsesPlainName() {
        List<ImportActivity> activities = parse("Зарядка,,Привычка,1");

        assertThat(activities.get(0).name()).isEqualTo("Зарядка");
    }

    @Test
    void mapsDayColumnsToDayNumbers() {
        List<ImportActivity> activities = parse("Зарядка,,Привычка,1,,1");

        assertThat(activities.get(0).dailyValues()).containsOnlyKeys(1, 3);
    }

    @Test
    void skipsZeroAndEmptyDays() {
        List<ImportActivity> activities = parse("Зарядка,,Привычка,0,,1");

        assertThat(activities.get(0).dailyValues()).containsOnlyKeys(3);
    }

    @Test
    void rejectsFileWithoutActivities() {
        assertThatThrownBy(() -> parse("Задачи,,,1,2,3", ",,,0,0,0"))
                .isInstanceOf(InvalidImportDataException.class);
    }

    @Test
    void rejectsInvalidNumber() {
        assertThatThrownBy(() -> parse("Зарядка,,Привычка,abc"))
                .isInstanceOf(InvalidImportDataException.class);
    }

    @Test
    void parsesRealExportFile() throws Exception {
        Path file = Path.of("..", "..", "Продуктивность.csv");
        Assumptions.assumeTrue(Files.exists(file), "Продуктивность.csv отсутствует");

        List<ImportActivity> activities;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            activities = parser.parse(reader);
        }

        assertThat(activities).isNotEmpty();
        assertThat(activities).extracting(ImportActivity::type)
                .contains(ActivityType.HABIT, ActivityType.TASK);
        assertThat(activities).extracting(ImportActivity::name)
                .contains("Чистил зубы (Утром)", "Чистил зубы (Вечером)");
    }

    private List<ImportActivity> parse(String... lines) {
        return parser.parse(new StringReader(String.join("\n", lines)));
    }
}
