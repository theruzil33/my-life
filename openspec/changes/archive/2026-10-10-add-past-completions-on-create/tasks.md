## 1. Backend tracker: бизнес-логика

- [x] 1.1 В `habit/dto/HabitRequest.java` добавить необязательное поле `Boolean markPastCompleted`; проверить: сборка `./mvnw -q -DskipTests compile` в `backend/tracker` проходит.
- [x] 1.2 В `HabitService.create` после сохранения активности при `Boolean.TRUE.equals(request.markPastCompleted())` вычислять дни от `startDate` включительно до `today().minusDays(1)`, отбирать только дни, где `habit.isScheduledOn(date)` истинно, и пакетно сохранять отметки через `HabitCompletionRepository`; убедиться, что `update` игнорирует поле; проверить: сборка `backend/tracker` проходит, unit-тесты группы 2 зелёные.

## 2. Backend tracker: unit-тесты

- [x] 2.1 В `HabitServiceTest` добавить тест: создание ежедневной активности с прошлой датой начала и признаком `true` сохраняет отметки на все дни от даты начала до вчера (сегодня отсутствует) через `completionRepository`; проверить: `./mvnw -q -Dtest=HabitServiceTest test` проходит.
- [x] 2.2 Добавить тест: создание активности с периодичностью `SPECIFIC_WEEKDAYS` и признаком `true` сохраняет отметки только на прошедшие дни, соответствующие выбранным дням недели; проверить: `./mvnw -q -Dtest=HabitServiceTest test` проходит.
- [x] 2.3 Добавить тесты: дата начала равна сегодня и признак `true` — автоматических отметок нет; признак отсутствует/`false` — автоматических отметок нет; тип `TASK` обрабатывается так же, как `HABIT`; проверить: `./mvnw -q -Dtest=HabitServiceTest test` проходит.
- [x] 2.4 Добавить тест: отклонённое создание (некорректные данные) с признаком `true` не вызывает сохранение отметок (`completionRepository` не используется); проверить: `./mvnw -q -Dtest=HabitServiceTest test` проходит.

## 3. Backend tracker: интеграционные тесты

- [x] 3.1 В `HabitControllerTest` добавить сценарий `POST /habits` с прошлой датой начала и `markPastCompleted: true`: ответ `201`, а последующий запрос истории/списка на прошлые дни показывает сохранённые отметки; проверить: `./mvnw -q -Dtest=HabitControllerTest test` проходит.
- [x] 3.2 Добавить сценарий `POST /habits` без поля `markPastCompleted`: поведение прежнее, отметок нет; проверить: `./mvnw -q -Dtest=HabitControllerTest test` проходит.

## 4. Веб-клиент: форма и API

- [x] 4.1 В `client/web/src/api/trackerApi.js` включить `markPastCompleted` в тело запроса создания в `activityBody` (поле опционально, при отсутствии не отправлять); проверить: `npm run lint` в `client/web` проходит, тесты группы 5 зелёные.
- [x] 4.2 В `client/web/src/tracker/HabitFormModal.jsx` добавить состояние чекбокса «Отметить прошлое выполненным» (по умолчанию снят), отрисовать чекбокс и передать его значение в `onSubmit`; проверить: `npm run lint` в `client/web` проходит, тесты группы 5 зелёные.
- [x] 4.3 В `client/web/src/tracker/ActivityListTab.jsx` прокинуть признак из формы в `trackerApi.create` (для вкладок «Привычки» и «Задачи»); проверить: тесты группы 5 зелёные.

## 5. Веб-клиент: тесты

- [x] 5.1 Обновить `client/web/test/api/trackerApi.test.js`: `create` с признаком отправляет `markPastCompleted: true`, без признака — не включает поле; проверить: `npx vitest run test/api/trackerApi.test.js` проходит.
- [x] 5.2 Обновить `client/web/test/tracker/HabitsTab.test.jsx`: чекбокс присутствует и снят по умолчанию, при установке и подтверждении создания запрос содержит признак `true`; проверить: `npx vitest run test/tracker/HabitsTab.test.jsx` проходит.
- [x] 5.3 Обновить `client/web/test/tracker/TasksTab.test.jsx`: чекбокс доступен и его состояние передаётся при создании задачи; проверить: `npx vitest run test/tracker/TasksTab.test.jsx` проходит.

## 6. Сквозная проверка

- [ ] 6.1 Проверить через запущенные gateway (8080), tracker (8081) и web: создать привычку с прошлой датой начала и установленной галочкой — в списке на прошлые даты появляются отметки, на сегодня отметки нет, статистика отражает прошлые отметки; создать задачу с галочкой — поведение совпадает; создать активность со снятой галочкой — отметок нет; проверка: поведение совпадает со сценариями спек `tracker-habits` и `web-tracker`.
