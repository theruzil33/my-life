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
import com.theruzil.tracker.habit.TimeOfDay;

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
        assertThat(activities.get(0).name()).isEqualTo("Чистил зубы");
        assertThat(activities.get(0).timeOfDay()).containsExactly(TimeOfDay.MORNING);
    }

    @Test
    void emptyNameContinuesPreviousActivity() {
        List<ImportActivity> activities = parse(
                "Чистил зубы,Утром,Привычка,1",
                ",Вечером,Привычка,1");

        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).name()).isEqualTo("Чистил зубы");
        assertThat(activities.get(0).timeOfDay()).containsExactlyInAnyOrder(TimeOfDay.MORNING, TimeOfDay.EVENING);
        assertThat(activities.get(0).lines()).hasSize(2);
    }

    @Test
    void variantIsIncludedInName() {
        List<ImportActivity> activities = parse("Крем для рук,На работе,Привычка,1");

        assertThat(activities.get(0).name()).isEqualTo("Крем для рук (На работе)");
        assertThat(activities.get(0).timeOfDay()).isEmpty();
    }

    @Test
    void activityWithoutVariantUsesPlainName() {
        List<ImportActivity> activities = parse("Зарядка,,Привычка,1");

        assertThat(activities.get(0).name()).isEqualTo("Зарядка");
        assertThat(activities.get(0).timeOfDay()).isEmpty();
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
    void morningVariantBecomesTimeOfDay() {
        List<ImportActivity> activities = parse("Витамины,Утром,Привычка,1");

        assertThat(activities.get(0).name()).isEqualTo("Витамины");
        assertThat(activities.get(0).timeOfDay()).containsExactly(TimeOfDay.MORNING);
    }

    @Test
    void dayAndEveningVariantsBecomeTimeOfDay() {
        List<ImportActivity> activities = parse(
                "Прогулка,Днём,Привычка,1",
                ",Вечером,Привычка,1");

        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).timeOfDay()).containsExactlyInAnyOrder(TimeOfDay.DAY, TimeOfDay.EVENING);
    }

    @Test
    void recognizesVariantsWithoutCaseAndSpaces() {
        List<ImportActivity> activities = parse("Прогулка,ВЕЧЕРОМ,Привычка,1",
                "Чтение,\u00a0EVENING\u00a0,Привычка,1");

        assertThat(activities).extracting(ImportActivity::timeOfDay)
                .containsExactly(SetOf.evening(), SetOf.evening());
    }

    @Test
    void groupsRowsOfSameActivityWithDifferentVariants() {
        List<ImportActivity> activities = parse(
                "Чистил зубы,Утром,Привычка,1",
                ",Вечером,Привычка,1",
                "Зарядка,,Привычка,1");

        assertThat(activities).extracting(ImportActivity::name).containsExactly("Чистил зубы", "Зарядка");
        assertThat(activities.get(0).lines()).hasSize(2);
    }

    @Test
    void doesNotGroupSameNameOfDifferentTypes() {
        List<ImportActivity> activities = parse(
                "Чтение,Утром,Привычка,1",
                "Чтение,Вечером,Задача,1");

        assertThat(activities).hasSize(2);
        assertThat(activities).extracting(ImportActivity::type)
                .containsExactly(ActivityType.HABIT, ActivityType.TASK);
    }

    @Test
    void keepsRowsWithoutVariantForActivityWithTimeOfDay() {
        List<ImportActivity> activities = parse(
                "Чистил зубы,Утром,Привычка,1",
                "Чистил зубы,,Привычка,1");

        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).timeOfDay()).containsExactly(TimeOfDay.MORNING);
        assertThat(activities.get(0).lines()).extracting(ImportActivityLine::timeOfDay)
                .containsExactly(TimeOfDay.MORNING, null);
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
        ImportActivity teeth = activities.stream()
                .filter(activity -> activity.name().equals("Чистил зубы"))
                .findFirst()
                .orElseThrow();
        assertThat(teeth.timeOfDay()).containsExactlyInAnyOrder(TimeOfDay.MORNING, TimeOfDay.EVENING);
    }

    private List<ImportActivity> parse(String... lines) {
        return parser.parse(new StringReader(String.join("\n", lines)));
    }

    private static final class SetOf {

        private static java.util.Set<TimeOfDay> evening() {
            return java.util.Set.of(TimeOfDay.EVENING);
        }
    }
}
