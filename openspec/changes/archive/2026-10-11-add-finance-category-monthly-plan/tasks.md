## 1. Backend finance: миграция БД и домен

- [x] 1.1 Добавить миграцию `backend/finance/src/main/resources/db/migration/V8__add_planned_amount_to_finance_categories.sql` (`ALTER TABLE finance_categories ADD COLUMN planned_amount NUMERIC(19, 2)`); проверить: миграция применяется в тестах без ошибок Flyway.
- [x] 1.2 В `category/Category` добавить nullable-поле `BigDecimal plannedAmount` (колонка `planned_amount`), учесть его в конструкторе и в методе редактирования (`edit`); проверить: `./mvnw -q -DskipTests compile` в `backend/finance` проходит.
- [x] 1.3 В `category/dto/CategoryRequest` добавить `plannedAmount`, в `category/dto/CategoryResponse` — `plannedAmount`; проверить: `./mvnw -q -DskipTests compile` в `backend/finance` проходит.

## 2. Backend finance: бизнес-логика

- [x] 2.1 В `category/CategoryService` при создании брать `plannedAmount` из запроса, при редактировании — обновлять план вместе с названием и группой, включать план в ответ `toResponse`, `null` трактовать как отсутствие плана; проверить: unit-тесты группы 3 зелёные.
- [x] 2.2 В `category/CategoryService` валидировать план для создания и редактирования: если значение задано, оно MUST быть строго больше нуля и иметь не более двух знаков после запятой, иначе `InvalidCategoryDataException`; проверить: unit-тесты группы 3 зелёные.

## 3. Backend finance: unit-тесты

- [x] 3.1 `category/CategoryServiceTest`: создание с планом сохраняет и возвращает план; создание без плана возвращает отсутствие плана; план допустим для `INCOME` и `EXPENSE`; проверить: `./mvnw -q -Dtest=CategoryServiceTest test` в `backend/finance` проходит.
- [x] 3.2 `CategoryServiceTest`: создание и редактирование с нулевым, отрицательным планом или планом с более чем двумя знаками после запятой отклоняются `InvalidCategoryDataException`; проверить: `./mvnw -q -Dtest=CategoryServiceTest test` проходит.
- [x] 3.3 `CategoryServiceTest`: редактирование меняет план и сохраняет прежний тип; пустой план убирает план; `list` возвращает план каждой категории и отсутствие плана для категорий без него; проверить: `./mvnw -q -Dtest=CategoryServiceTest test` проходит.
- [x] 3.4 `category/CategoryRepositoryTest`: поле `plannedAmount` сохраняется и читается, включая отсутствие плана; проверить: `./mvnw -q -Dtest=CategoryRepositoryTest test` проходит.

## 4. Backend finance: интеграционные тесты

- [x] 4.1 `web/CategoryControllerTest`: `POST /categories` с планом возвращает `plannedAmount`; `POST` без плана возвращает `null`; проверить: `./mvnw -q -Dtest=CategoryControllerTest test` в `backend/finance` проходит.
- [x] 4.2 `CategoryControllerTest`: `POST`/`PUT` с нулевым или отрицательным планом отвечает `400`; `PUT` с новым планом меняет его; `PUT` с `null` убирает план; проверить: `./mvnw -q -Dtest=CategoryControllerTest test` проходит.

## 5. Веб-клиент: API и хелперы дат

- [x] 5.1 В `client/web/src/api/financeApi.js` передавать `plannedAmount` в теле создания и редактирования категории (число или `null`, пустое значение — `null`); проверить: `npm run lint` в `client/web` проходит, тесты группы 8 зелёные.
- [x] 5.2 В `client/web/src/finances/dates.js` добавить функцию преобразования выбранного месяца `YYYY-MM` в диапазон `{ from, to }` (первое и последнее число месяца); проверить: unit-тест функции в `test/finances` зелёный.

## 6. Веб-клиент: поле плана в формах категорий

- [x] 6.1 В `client/web/src/finances/CategoryFormModal.jsx` добавить необязательное поле «Плановая сумма в месяц» с валидацией положительного числа не более чем с двумя знаками после запятой и передачей `plannedAmount` в `onSubmit`; проверить: `npm run lint` в `client/web` проходит, тесты группы 8 зелёные.
- [x] 6.2 В `client/web/src/finances/EditCategoryModal.jsx` добавить то же поле, предзаполнив его текущим планом; пустое поле означает удаление плана; валидация и блокировка повторной отправки сохраняются; проверить: `npm run lint` в `client/web` проходит, тесты группы 8 зелёные.

## 7. Веб-клиент: месячная статистика с планом и дельтой

- [x] 7.1 В `client/web/src/finances/StatisticsTab.jsx` заменить поля диапазона «С/По» на выбор месяца и года (`<input type="month">`), по умолчанию — текущий месяц; `from`/`to` вычислять через новую функцию хелпера и передавать в `listOperationsByRange`; проверить: `npm run build` в `client/web` проходит, тесты группы 8 зелёные.
- [x] 7.2 В таблице `StatisticsTab.jsx` добавить после заголовка «Категория» колонки «План» и «Дельта» и по две ячейки в каждой строке: план категории и `план − сумма операций категории за выбранный месяц`; при отсутствии плана обе ячейки пустые, отрицательная дельта показывается со знаком минус, значения — с двумя знаками после запятой; проверить: `npm run build` в `client/web` проходит, тесты группы 8 зелёные.
- [x] 7.3 При необходимости добавить в `client/web/src/App.css` стили колонок «План» и «Дельта», не меняя группировку, границы и прокрутку таблицы; проверить: `npm run build` в `client/web` проходит.

## 8. Веб-клиент: тесты

- [x] 8.1 Обновить `client/web/test/api/financeApi.test.js`: создание и редактирование категории передают `plannedAmount`, пустое значение уходит как `null`; проверить: `npx vitest run test/api/financeApi.test.js` в `client/web` проходит.
- [x] 8.2 Обновить `client/web/test/finances/StatisticsTab.test.jsx`: выбор месяца и года вместо диапазона, по умолчанию текущий месяц, отсутствие полей произвольного диапазона; колонки «План» и «Дельта» показывают план, положительную/отрицательную дельту и пустые ячейки без плана; проверить: `npx vitest run test/finances/StatisticsTab.test.jsx` проходит.
- [x] 8.3 Обновить `client/web/test/pages/FinancesPage.test.jsx`: поле плана доступно в формах создания и редактирования, валидируется и передаётся в API, пустое поле убирает план; проверить: `npx vitest run test/pages/FinancesPage.test.jsx` проходит.
- [x] 8.4 Прогнать полный набор тестов и линт клиента; проверить: `npm run lint` и `npx vitest run` в `client/web` проходят.

## 9. Сквозная проверка

- [x] 9.1 При запущенных gateway (8080), finance (8082) и web: войти, создать категории прихода и ухода с планом и без, открыть «Статистику» — таблица показывает только выбранный месяц, у каждой категории видны «План» и «Дельта», дельта равна плану минус факт месяца, при отсутствии плана обе колонки пустые, а поля произвольного диапазона отсутствуют; проверить: поведение совпадает со сценариями спек `finance-categories` и `web-finances`.

## 10. Веб-клиент: план и действия в списке категорий

- [x] 10.1 В `client/web/src/finances/CategoriesTab.jsx` показывать плановую сумму категории (с двумя знаками после запятой, если план задан) после кнопок «Редактировать» и «Удалить», обернув обе кнопки в общий контейнер действий; в `client/web/src/App.css` расположить кнопки последовательно рядом друг с другом и выровнять колонки (имя, тип, действия, план) между категориями; проверить: `npm run lint` в `client/web` проходит, тест группы 10 зелёный.
- [x] 10.2 Обновить `client/web/test/pages/FinancesPage.test.jsx`: кнопки идут последовательно, план показывается после них и не показывается без плана; прогнать `npm run lint`, `npm run build` и `npx vitest run` в `client/web`; проверить: всё зелёное.
