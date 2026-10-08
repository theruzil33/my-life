## 1. Backend tracker: модель и миграция

- [x] 1.1 Добавить enum `ActivityType` (`HABIT`, `TASK`) и поле `type` в сущность `Habit` (`@Enumerated(EnumType.STRING)`, колонка `type`); конструктор создания принимает тип, `update` тип не меняет; проверка: `./mvnw -q -DskipTests compile` в `backend/tracker` проходит.
- [x] 1.2 Добавить миграцию `V3__add_tracker_habits_type.sql` с `ALTER TABLE tracker_habits ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'HABIT'`; проверка: приложение стартует и существующие привычки читаются с типом `HABIT`.

## 2. Backend tracker: бизнес-логика и API

- [x] 2.1 Расширить `HabitRepository`: фильтр по типу в `findScheduledOn` (необязательный тип) и получение активностей обоих типов в `findStartedBy`; проверка: `./mvnw -q -Dtest=HabitRepositoryTest test` проходит.
- [x] 2.2 Добавить поле `type` в `HabitRequest`, `HabitResponse`, `HabitOnDateResponse`, `HabitHistoryResponse`; проверка: `./mvnw -q -DskipTests compile` проходит.
- [x] 2.3 В `HabitService` валидировать обязательный `type` при создании (отклонять `null` и неизвестные значения), сохранять тип при редактировании, поддержать необязательный фильтр `type` в `listOnDate` и возвращать `type` в истории; проверка: unit-тесты группы 3.
- [x] 2.4 В `HabitController` добавить необязательный параметр `type` у `GET /tracker/habits` и передавать его в сервис; проверка: интеграционные тесты группы 4.

## 3. Backend tracker: unit-тесты

- [x] 3.1 Обновить `HabitServiceTest`: создание `TASK`, отклонение создания без типа и с неизвестным типом, сохранение типа при редактировании, список с фильтром по типу и без него, история с типами; проверка: `./mvnw -q -Dtest=HabitServiceTest test` проходит.
- [x] 3.2 Обновить/дополнить `HabitRepositoryTest` для фильтрации списка по типу; проверка: `./mvnw -q -Dtest=HabitRepositoryTest test` проходит.

## 4. Backend tracker: интеграционные тесты

- [x] 4.1 Обновить `HabitControllerTest`: успешное создание задачи (`201`) с типом `TASK`, тип в ответах списка/истории, фильтр `?type=`, отклонение запроса без типа (`400`); проверка: `./mvnw -q -Dtest=HabitControllerTest test` проходит.

## 5. Веб-клиент: API и общий компонент

- [x] 5.1 В `client/web/src/api/trackerApi.js` передавать `type` в `create` и в `listOnDate` (параметр запроса); проверка: unit-тесты группы 7.
- [x] 5.2 Обобщить `HabitsTab` в переиспользуемый компонент списка активностей с проп `type` и текстами вкладки; `HabitFormModal` принимает `type` и отправляет его при создании; проверка: `npm run lint` в `client/web` проходит.
- [x] 5.3 В `TrackerPage` добавить вкладку «Задачи» (`TASK`) рядом с «Привычки» (`HABIT`), сохранить выбранную дату после первого открытия вкладки; проверка: тесты группы 7.

## 6. Веб-клиент: статистика

- [x] 6.1 В `StatisticsTab` группировать записи истории по `type` (сначала `HABIT`, затем `TASK`) и отображать строку-заголовок группы, визуально разделяя типы в одной матрице; проверка: тесты группы 7.

## 7. Веб-клиент: тесты

- [x] 7.1 Обновить `test/api/trackerApi.test.js` под передачу `type` в создании и списке; проверка: `npx vitest run test/api/trackerApi.test.js` проходит.
- [x] 7.2 Обновить/добавить тесты вкладки активностей (`test/tracker/HabitsTab.test.jsx` и тест вкладки задач): список только выбранного типа, создание с нужным `type`, пустое состояние; проверка: `npx vitest run test/tracker` проходит.
- [x] 7.3 Обновить `test/tracker/StatisticsTab.test.jsx`: оба типа в одной матрице с заголовками групп; проверка: `npx vitest run test/tracker/StatisticsTab.test.jsx` проходит.
- [x] 7.4 Обновить `test/pages/TrackerPage.test.jsx` под новую вкладку «Задачи» и сохранение даты; проверка: `npx vitest run test/pages/TrackerPage.test.jsx` проходит.

## 8. Сквозная проверка

- [x] 8.1 Проверить через запущенные gateway (8080), tracker (8081) и web: создать задачу на вкладке «Задачи» и привычку на вкладке «Привычки», отметить выполнение обеих, убедиться, что списки не смешиваются, а вкладка «Статистика» показывает оба типа с разделением; проверка: поведение совпадает со сценариями спек `tracker-habits` и `web-tracker`.
