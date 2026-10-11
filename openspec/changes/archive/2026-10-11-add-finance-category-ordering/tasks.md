## 1. Backend finance: миграция БД и домен

- [x] 1.1 Добавить миграцию `backend/finance/src/main/resources/db/migration/V9__add_sort_order_to_finance_categories.sql`: добавить `sort_order INTEGER`, заполнить по `ROW_NUMBER() OVER (PARTITION BY user_id, type ORDER BY id)`, затем сделать `NOT NULL`; проверить: миграция применяется в тестах без ошибок Flyway.
- [x] 1.2 В `category/Category` добавить поле `Integer sortOrder` (колонка `sort_order`) с геттером; учесть в конструкторе создания и в `edit` так, чтобы редактирование не меняло порядок; проверить: `./mvnw -q -DskipTests compile` в `backend/finance` проходит.
- [x] 1.3 В `category/CategoryRepository` добавить метод выборки категорий пользователя (сортировка по `sort_order`, затем `id`) и метод получения максимального `sort_order` для пары `(userId, type)`; проверить: `./mvnw -q -DskipTests compile` в `backend/finance` проходит.

## 2. Backend finance: бизнес-логика

- [x] 2.1 В `category/CategoryService.create` назначать новой категории `sortOrder = max(sortOrder) + 1` для её пары `(userId, type)` (при отсутствии категорий — `1`); проверить: unit-тесты группы 3 зелёные.
- [x] 2.2 В `category/CategoryService.list` заменить компаратор `TYPE_THEN_ID` на сортировку `INCOME` → `EXPENSE`, затем по `sortOrder`, затем по `id`; проверить: unit-тесты группы 3 зелёные.
- [x] 2.3 Добавить DTO `category/dto/CategoryOrderRequest(String type, List<Long> categoryIds)` и метод `CategoryService.reorder(userId, request)`: валидировать тип, отсутствие дубликатов, принадлежность каждой категории пользователю (`404` для чужой/несуществующей), совпадение типа (`400`) и полноту набора категорий типа (`400`), затем присвоить `sortOrder` по индексу; проверить: unit-тесты группы 3 зелёные.
- [x] 2.4 В `web/CategoryController` добавить эндпоинт `PUT /categories/order` с кодом ответа `204`, вызывающий `CategoryService.reorder`; проверить: интеграционные тесты группы 4 зелёные.

## 3. Backend finance: unit-тесты

- [x] 3.1 `category/CategoryServiceTest`: новая категория получает порядок в конце своего типа; порядок прихода и ухода независимы; порядок не влияет на тип, название, группу и план; проверить: `./mvnw -q -Dtest=CategoryServiceTest test` в `backend/finance` проходит.
- [x] 3.2 `CategoryServiceTest`: `list` возвращает категории типа `INCOME` перед `EXPENSE` и внутри типа — в сохранённом порядке, а не по возрастанию `id`; проверить: `./mvnw -q -Dtest=CategoryServiceTest test` проходит.
- [x] 3.3 `CategoryServiceTest`: `reorder` успешно сохраняет порядок; порядок другого типа не меняется; чужая/несуществующая категория отклоняется `CategoryNotFoundException`; категория другого типа, дубликаты и неполный набор отклоняются `InvalidCategoryDataException`; проверить: `./mvnw -q -Dtest=CategoryServiceTest test` проходит.
- [x] 3.4 `category/CategoryRepositoryTest`: `sortOrder` сохраняется и читается; выборка по пользователю упорядочена по `sortOrder` и `id`; максимальный `sortOrder` для пары `(userId, type)` возвращается корректно; проверить: `./mvnw -q -Dtest=CategoryRepositoryTest test` проходит.

## 4. Backend finance: интеграционные тесты

- [x] 4.1 `web/CategoryControllerTest`: `PUT /categories/order` с полным упорядоченным списком своего типа отвечает `204`, а последующий `GET /categories` возвращает новый порядок; проверить: `./mvnw -q -Dtest=CategoryControllerTest test` в `backend/finance` проходит.
- [x] 4.2 `CategoryControllerTest`: `PUT /categories/order` с чужой/несуществующей категорией отвечает `404`; с категорией другого типа, дубликатом или неполным набором — `400`; проверить: `./mvnw -q -Dtest=CategoryControllerTest test` проходит.
- [x] 4.3 `CategoryControllerTest`: `PUT /categories/{id}` продолжает работать после добавления литерального маршрута `/categories/order`; проверить: `./mvnw -q -Dtest=CategoryControllerTest test` проходит.

## 5. Веб-клиент: API

- [x] 5.1 В `client/web/src/api/financeApi.js` добавить метод `reorderCategories(token, type, categoryIds)`, отправляющий `PUT /finances/categories/order` с телом `{ type, categoryIds }`; проверить: `npm run lint` в `client/web` проходит, тест группы 8 зелёный.

## 6. Веб-клиент: управление порядком на вкладке «Категории»

- [x] 6.1 В `client/web/src/finances/CategoriesTab.jsx` добавить у каждого блока кнопки перемещения вверх/вниз: отключать «вверх» у первого блока типа и «вниз» у последнего, на время запроса блокировать повторные перемещения; проверить: `npm run lint` в `client/web` проходит, тесты группы 8 зелёные.
- [x] 6.2 В `client/web/src/finances/CategoriesTab.jsx` реализовать перемещение блока: сформировать новый плоский порядок категорий своего типа (блок целиком, порядок внутри блока сохраняется), вызвать `financeApi.reorderCategories`, при успехе применить порядок, при ошибке показать сообщение и не менять порядок; проверить: `npm run lint` в `client/web` проходит, тесты группы 8 зелёные.
- [x] 6.3 При необходимости добавить в `client/web/src/App.css` класс для колонки кнопок перемещения, не нарушая выравнивание строк и существующую группировку; проверить: `npm run build` в `client/web` проходит.

## 7. Веб-клиент: единый порядок на вкладках «Операции» и «Статистика»

- [x] 7.1 В `client/web/src/finances/OperationsTab.jsx` убрать сортировку категорий одного типа по `id`: оставить сортировку только по типу (`EXPENSE` → `INCOME`), сохраняя порядок из ответа API; проверить: `npm run lint` в `client/web` проходит, тесты группы 8 зелёные.
- [x] 7.2 В `client/web/src/finances/StatisticsTab.jsx` убрать сортировку категорий одного типа по `id` тем же способом; проверить: `npm run lint` в `client/web` проходит, тесты группы 8 зелёные.

## 8. Веб-клиент: тесты

- [x] 8.1 Обновить `client/web/test/api/financeApi.test.js`: метод `reorderCategories` отправляет `PUT /finances/categories/order` с `{ type, categoryIds }`; проверить: `npx vitest run test/api/financeApi.test.js` в `client/web` проходит.
- [x] 8.2 Добавить `client/web/test/finances/CategoriesTab.test.jsx`: кнопки перемещения отображаются у блоков, перемещение блока вызывает API с новым порядком, группа перемещается целиком, на краях кнопки недоступны, ошибка API не меняет порядок; проверить: `npx vitest run test/finances/CategoriesTab.test.jsx` проходит.
- [x] 8.3 Обновить `client/web/test/finances/OperationsTab.test.jsx`: категории одного типа отображаются в порядке из ответа API, даже если он не совпадает с порядком по `id`; проверить: `npx vitest run test/finances/OperationsTab.test.jsx` проходит.
- [x] 8.4 Обновить `client/web/test/finances/StatisticsTab.test.jsx`: строки одного типа идут в порядке из ответа API, а не по `id`; проверить: `npx vitest run test/finances/StatisticsTab.test.jsx` проходит.
- [x] 8.5 Обновить `client/web/test/pages/FinancesPage.test.jsx`: изменение порядка на вкладке «Категории» отражается в перечитываемом списке; проверить: `npx vitest run test/pages/FinancesPage.test.jsx` проходит.
- [x] 8.6 Прогнать полный набор тестов и линт клиента; проверить: `npm run lint` и `npx vitest run` в `client/web` проходят.

## 9. Сквозная проверка

- [x] 9.1 При запущенных gateway (8080), finance (8082) и web: войти, создать несколько категорий прихода и ухода (в том числе с группами), на вкладке «Категории» переместить блоки кнопками «выше»/«низ»; убедиться, что порядок сохраняется после перезагрузки и совпадает на вкладках «Операции» и «Статистика», группы перемещаются целиком, порядок прихода и ухода независим, а на краях типа кнопки недоступны; проверить: поведение совпадает со сценариями спек `finance-categories` и `web-finances`.
</content>
