package com.theruzil.tracker.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.theruzil.tracker.habit.ActivityType;
import com.theruzil.tracker.habit.HabitService;
import com.theruzil.tracker.habit.dto.ImportedActivityResult;
import com.theruzil.tracker.importing.dto.ImportResultResponse;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    private static final Long USER_ID = 10L;
    private static final int YEAR = 2026;
    private static final int MONTH = 5;

    @Mock
    private HabitService habitService;

    private ImportService importService;

    @BeforeEach
    void setUp() {
        importService = new ImportService(new ProductivityCsvParser(), habitService);
    }

    @Test
    void createsMissingActivityAndSavesCompletions() {
        when(habitService.importActivity(eq(USER_ID), eq(ActivityType.TASK), eq("Продукты"),
                eq(LocalDate.of(2026, 5, 1)), anyCollection()))
                .thenReturn(new ImportedActivityResult(true, 2));

        ImportResultResponse result = importService.importCsv(USER_ID, YEAR, MONTH,
                csv(activity("Продукты", "", "Задача", "10", "", "20")));

        assertThat(result.createdActivities()).isEqualTo(1);
        assertThat(result.savedCompletions()).isEqualTo(2);
        assertThat(capturedDates()).containsExactlyInAnyOrder(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3));
    }

    @Test
    void reusesExistingActivityWithoutCreatingDuplicate() {
        when(habitService.importActivity(eq(USER_ID), eq(ActivityType.HABIT), eq("Зарядка"),
                eq(LocalDate.of(2026, 5, 1)), anyCollection()))
                .thenReturn(new ImportedActivityResult(false, 1));

        ImportResultResponse result = importService.importCsv(USER_ID, YEAR, MONTH,
                csv(activity("Зарядка", "", "Привычка", "1")));

        assertThat(result.createdActivities()).isZero();
        assertThat(result.savedCompletions()).isEqualTo(1);
    }

    @Test
    void importsDistinctActivitiesSeparately() {
        when(habitService.importActivity(any(), any(), any(), any(), anyCollection()))
                .thenReturn(new ImportedActivityResult(true, 0));

        ImportResultResponse result = importService.importCsv(USER_ID, YEAR, MONTH,
                csv(activity("Зарядка", "", "Привычка", "1"),
                        activity("Чтение", "", "Задача", "1")));

        assertThat(result.createdActivities()).isEqualTo(2);
    }

    @Test
    void passesOnlyPositiveDaysToService() {
        when(habitService.importActivity(any(), any(), any(), any(), anyCollection()))
                .thenReturn(new ImportedActivityResult(true, 1));

        importService.importCsv(USER_ID, YEAR, MONTH, csv(activity("Зарядка", "", "Привычка", "0", "", "1")));

        assertThat(capturedDates()).containsExactly(LocalDate.of(2026, 5, 3));
    }

    @Test
    void ignoresDaysBeyondMonthLength() {
        when(habitService.importActivity(any(), any(), any(), any(), anyCollection()))
                .thenReturn(new ImportedActivityResult(true, 1));
        String[] days = new String[29];
        java.util.Arrays.fill(days, "");
        days[27] = "7";
        days[28] = "5";

        importService.importCsv(USER_ID, 2026, 2, csv(activity("Зарядка", "", "Привычка", days)));

        assertThat(capturedDates()).containsExactly(LocalDate.of(2026, 2, 28));
    }

    @Test
    void propagatesErrorFromService() {
        when(habitService.importActivity(any(), any(), any(), any(), anyCollection()))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> importService.importCsv(USER_ID, YEAR, MONTH,
                csv(activity("Зарядка", "", "Привычка", "1"))))
                .isInstanceOf(IllegalStateException.class);

        verify(habitService).importActivity(any(), any(), any(), any(), anyCollection());
    }

    @Test
    void rejectsFileWithoutActivities() {
        assertThatThrownBy(() -> importService.importCsv(USER_ID, YEAR, MONTH, csv("Задачи,,,1,2,3")))
                .isInstanceOf(InvalidImportDataException.class);
        verifyNoInteractions(habitService);
    }

    @Test
    void rejectsInvalidMonthOrYear() {
        assertThatThrownBy(() -> importService.importCsv(USER_ID, 2026, 13, csv(activity("Зарядка", "", "Привычка",
                "1"))))
                .isInstanceOf(InvalidImportDataException.class);
        assertThatThrownBy(() -> importService.importCsv(USER_ID, null, 5, csv(activity("Зарядка", "", "Привычка",
                "1"))))
                .isInstanceOf(InvalidImportDataException.class);
        assertThatThrownBy(() -> importService.importCsv(USER_ID, 2026, null, csv(activity("Зарядка", "", "Привычка",
                "1"))))
                .isInstanceOf(InvalidImportDataException.class);
        verifyNoInteractions(habitService);
    }

    @Test
    void rejectsMissingFile() {
        assertThatThrownBy(() -> importService.importCsv(USER_ID, YEAR, MONTH, null))
                .isInstanceOf(InvalidImportDataException.class);
        verifyNoInteractions(habitService);
    }

    @SuppressWarnings("unchecked")
    private List<LocalDate> capturedDates() {
        ArgumentCaptor<Collection<LocalDate>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(habitService).importActivity(any(), any(), any(), any(), captor.capture());
        return List.copyOf(captor.getValue());
    }

    private static InputStream csv(String... lines) {
        return new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private static String activity(String name, String variant, String type, String... days) {
        return name + "," + variant + "," + type + "," + String.join(",", days);
    }
}
