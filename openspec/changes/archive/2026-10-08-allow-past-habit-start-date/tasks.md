## 1. Backend tracker: бизнес-логика

- [x] 1.1 В `HabitService.validateDefinition` заменить проверку `request.startDate().isBefore(today())` на проверку будущей даты (`isAfter(today())`) с сообщением «Habit start date must not be in the future»; правило остаётся общим для `create` и `update`; проверка: сборка `./mvnw -q -DskipTests compile` в `backend/tracker` проходит.

## 2. Backend tracker: unit-тесты

- [x] 2.1 Обновить `HabitServiceTest` под новое правило: `createsHabitWithFutureStartDate` → сценарий «Отклонение создания с датой начала в будущем», `rejectsHabitWithPastStartDate` → «Создание привычки с прошлой датой начала» (успех), обновить `updatesHabitWithoutTouchingCompletions` (использовать дату не в будущем) и `rejectsUpdateWithPastStartDate` → «Отклонение редактирования с некорректной датой начала» (будущая дата); проверка: `./mvnw -q -Dtest=HabitServiceTest test` проходит.

## 3. Backend tracker: интеграционные тесты

- [x] 3.1 Обновить `HabitControllerTest`: заменить `rejectsHabitWithStartDateInThePast` на успешное создание с прошлой датой (`201`) и добавить отклонение создания с будущей датой (`400`), скорректировать `updatesHabit` (дата начала не в будущем); проверка: `./mvnw -q -Dtest=HabitControllerTest test` проходит.

## 4. Веб-клиент: форма создания

- [x] 4.1 В `client/web/src/tracker/validation.js` заменить проверку прошлого на `isAfter(startDate, todayIso())` и сообщение на «Дата начала не может быть позже сегодняшней»; проверка: unit-тесты группы 5.
- [x] 4.2 В `client/web/src/tracker/HabitFormModal.jsx` заменить у поля даты начала `min={today}` на `max={today}` (значение по умолчанию `todayIso()` сохранить); проверка: `npm run lint` в `client/web` проходит без ошибок.

## 5. Веб-клиент: тесты

- [x] 5.1 Обновить `client/web/test/tracker/validation.test.js`: прошлая дата проходит без ошибки, будущая дата даёт сообщение «Дата начала не может быть позже сегодняшней»; проверка: `npx vitest run test/tracker/validation.test.js` проходит.
- [x] 5.2 Обновить `client/web/test/tracker/HabitsTab.test.jsx`: тест «отклоняет создание с датой начала в прошлом» заменить на отклонение будущей даты, добавить успешное создание с прошлой датой; проверка: `npx vitest run test/tracker/HabitsTab.test.jsx` проходит.

## 6. Сквозная проверка

- [x] 6.1 Проверить через запущенные gateway (8080), tracker (8081) и web: создать привычку с прошлой датой начала — успех и она появляется в списке на сегодня; создать с будущей датой — `400`/сообщение об ошибке; проверить, что запрет будущих дат при выборе даты списка/статистики и при отметке выполнения не изменился; проверка: поведение совпадает со сценариями спек `tracker-habits` и `web-tracker`.
