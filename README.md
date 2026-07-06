# Explore With Me — микросервисная декомпозиция

## Архитектура

Функциональность `main-service` разнесена по самостоятельным Spring Boot-микросервисам внутри группирующего Maven-модуля `core`:

| Модуль | Назначение | Внешние маршруты Gateway |
| --- | --- | --- |
| `core/events-service` | Управление мероприятиями и категориями публичного каталога | `/events/**`, `/users/*/events/**`, `/categories/**` |
| `core/request-service` | Управление заявками на участие и подтверждениями заявок владельцем события | `/users/*/requests/**`, `/users/*/events/*/requests/**` |
| `core/admin-service` | Администрирование пользователями | `/admin/users/**` |
| `core/additional-service` | Дополнительная функциональность: подборки, комментарии и административные операции над ними | `/compilations/**`, `/comments/**`, `/admin/categories/**`, `/admin/compilations/**`, `/admin/comments/**` |
| `core/main-service` | Тонкая точка запуска без бизнес-реализации; оставлена для совместимости на время миграции окружений | legacy fallback-маршруты |

Инфраструктурные сервисы находятся в `infra`:

* `infra/discovery-server` — Eureka Server для service discovery.
* `infra/config-server` — Spring Cloud Config Server. Конфигурации приложений лежат в `infra/config-server/src/main/resources/config`.
* `infra/gateway-server` — единая точка входа на порту `8080`; маршруты настроены в `infra/gateway-server/src/main/resources/application.yaml`.

Статистика сохранена в отдельном модуле `stats-service`; Gateway проксирует `/hit` и `/stats` в `stats-server`.

## Межсервисное взаимодействие

Все новые core-сервисы подключают:

* Eureka Client — регистрация и поиск сервисов по именам `events-service`, `request-service`, `admin-service`, `additional-service`.
* Spring Cloud OpenFeign — внутренние HTTP-клиенты должны объявляться через `@FeignClient(name = "<service-name>")`.
* Spring Cloud Config — загрузка общих настроек через `CONFIG_SERVER_URL` либо discovery-first lookup `config-server`.
* Resilience4j — базовые настройки retry/circuit breaker задаются в Config Server; при переносе бизнес-вызовов следует добавлять fallback-методы, возвращающие безопасные значения для некритичных зависимостей.

Рекомендуемые внутренние API для замены временных локальных read-model зависимостей на Feign-вызовы:

* `events-service`: `GET /internal/events/{eventId}`, `GET /internal/events?ids=...`, `PATCH /internal/events/{eventId}/confirmed-requests`.
* `request-service`: `GET /internal/events/{eventId}/requests/count`, `GET /internal/events/requests/counts?eventIds=...`, `GET /internal/users/{userId}/requests`.
* `admin-service`: `GET /internal/users/{userId}`, `GET /internal/users?ids=...`.
* `additional-service`: `GET /internal/comments/counts?eventIds=...`, `GET /internal/compilations/{compilationId}`.

Для защиты от N+1 внутренние методы со списками идентификаторов должны возвращать агрегированные данные за один запрос.

## Внешний API

Спецификация основного API находится в файле [`ewm-main-service-spec.json`](ewm-main-service-spec.json), статистики — в [`ewm-stats-service-spec.json`](ewm-stats-service-spec.json), дополнительной функциональности — в [`ewm-comments-spec.json`](ewm-comments-spec.json).


## Текущее состояние переноса

Исходный код бизнес-пакетов удалён из `core/main-service`: сервис больше содержит только класс запуска Spring Boot и ресурсы. Реализация контроллеров, сервисов, репозиториев, DTO и моделей перенесена в соответствующие bounded-context модули. До полного выделения отдельных баз данных некоторые сервисы содержат локальные read-model копии зависимых сущностей/репозиториев; их следующий шаг миграции — заменить на Feign-клиенты внутренних API, перечисленных выше.
