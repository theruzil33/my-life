package com.theruzil.finance.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import com.theruzil.finance.category.CategoryType;

class FinanceCsvParserTest {

    private final FinanceCsvParser parser = new FinanceCsvParser();

    @Test
    void parsesBothSectionsWithTypes() {
        String csv = String.join("\n",
                "Статьи доходов",
                "Зарплата,Зарплата,247349,,,",
                "Статьи расходов",
                "Продукты,,0,,,");

        List<ImportArticle> articles = parse(csv);

        assertThat(articles).hasSize(2);
        assertThat(articles.get(0).type()).isEqualTo(CategoryType.INCOME);
        assertThat(articles.get(0).name()).isEqualTo("Зарплата");
        assertThat(articles.get(0).group()).isEqualTo("Зарплата");
        assertThat(articles.get(1).type()).isEqualTo(CategoryType.EXPENSE);
    }

    @Test
    void carriesGroupDownWithinSection() {
        String csv = String.join("\n",
                "Статьи доходов",
                "Зарплата,Зарплата,,,,",
                ",Аванс,,,,",
                ",Отпусктные,,,,");

        List<ImportArticle> articles = parse(csv);

        assertThat(articles).extracting(ImportArticle::name).containsExactly("Зарплата", "Аванс", "Отпусктные");
        assertThat(articles).extracting(ImportArticle::group).containsExactly("Зарплата", "Зарплата", "Зарплата");
    }

    @Test
    void articleWithoutNameUsesGroupAndHasNoGroup() {
        List<ImportArticle> articles = parse("Статьи расходов\nПродукты,,0,,,");

        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).name()).isEqualTo("Продукты");
        assertThat(articles.get(0).group()).isNull();
    }

    @Test
    void resetsGroupAtNewSection() {
        String csv = String.join("\n",
                "Статьи доходов",
                "Работа,Оклад,,,,",
                "Статьи расходов",
                ",Свет,,,,");

        List<ImportArticle> articles = parse(csv);

        assertThat(articles).hasSize(2);
        assertThat(articles.get(1).name()).isEqualTo("Свет");
        assertThat(articles.get(1).group()).isNull();
    }

    @Test
    void quotesProtectCommasInsideFields() {
        List<ImportArticle> articles = parse("Статьи расходов\nЕда,\"Продукты, свежие\",0,,,");

        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).name()).isEqualTo("Продукты, свежие");
        assertThat(articles.get(0).group()).isEqualTo("Еда");
    }

    @Test
    void parsesDecimalCommaAndSpaceSeparators() {
        List<ImportArticle> articles = parse("Статьи расходов\nПродукты,,0,,,\"3966,21\",1 234");

        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).dailyAmounts().get(1)).isEqualByComparingTo("3966.21");
        assertThat(articles.get(0).dailyAmounts().get(2)).isEqualByComparingTo("1234");
    }

    @Test
    void parsesPlanAndSkipsNonPositiveDays() {
        List<ImportArticle> articles = parse("Статьи расходов\nПродукты,,250, ,,0,10,\"-5\"");

        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).plannedAmount()).isEqualByComparingTo(new BigDecimal("250"));
        assertThat(articles.get(0).dailyAmounts()).containsOnlyKeys(2);
    }

    @Test
    void rejectsFileWithoutSections() {
        assertThatThrownBy(() -> parse("просто,строка,без,разделов"))
                .isInstanceOf(InvalidImportDataException.class);
    }

    @Test
    void rejectsInvalidNumber() {
        assertThatThrownBy(() -> parse("Статьи расходов\nПродукты,,0,,,abc"))
                .isInstanceOf(InvalidImportDataException.class);
    }

    @Test
    void parsesRealExportFile() throws Exception {
        Path file = Path.of("..", "..", "Деньги.csv");
        Assumptions.assumeTrue(Files.exists(file), "Деньги.csv отсутствует");

        List<ImportArticle> articles;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            articles = parser.parse(reader);
        }

        assertThat(articles).isNotEmpty();
        assertThat(articles).extracting(ImportArticle::type)
                .contains(CategoryType.INCOME, CategoryType.EXPENSE);
    }

    private List<ImportArticle> parse(String csv) {
        return parser.parse(new StringReader(csv));
    }
}
