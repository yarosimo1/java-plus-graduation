# Explore With Me

Микросервисное приложение для публикации, поиска и взаимодействия с событиями.

Проект построен на базе **Spring Boot и Spring Cloud** и демонстрирует декомпозицию монолитного приложения на отдельные сервисы с использованием API Gateway, Service Discovery, Config Server и межсервисного взаимодействия.

**Status:** Completed

---

## Tech Stack

### Backend

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen?logo=springboot)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.3-brightgreen?logo=spring)

* Spring Boot
* Spring MVC
* Spring Data JPA
* Spring Security
* Spring Validation
* Spring Cloud Gateway
* Spring Cloud Config
* Eureka
* OpenFeign
* MapStruct

### Data & Infrastructure

![PostgreSQL](https://img.shields.io/badge/PostgreSQL-blue?logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-blue?logo=docker)
![Maven](https://img.shields.io/badge/Maven-C71A36?logo=apachemaven)

* PostgreSQL
* Hibernate
* Docker / Docker Compose
* Maven

### Testing & Code Quality

* JUnit 5
* Mockito
* Checkstyle
* SpotBugs
* JaCoCo

---

## About the Project

Explore With Me — платформа для работы с событиями.

Пользователи могут публиковать события, искать интересующие мероприятия и взаимодействовать с ними. Архитектура проекта разделена на несколько сервисов, каждый из которых отвечает за отдельную область приложения.

Основная задача проекта — показать переход от монолитной архитектуры к распределённой системе с независимыми бизнес-компонентами.

---

## Architecture

Внешние клиенты взаимодействуют с системой через единую точку входа — **API Gateway**.

```text
                         ┌──────────────────┐
                         │     Client       │
                         └────────┬─────────┘
                                  │
                                  ▼
                         ┌──────────────────┐
                         │  API Gateway     │
                         │      :8080        │
                         └────────┬─────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              │                   │                   │
              ▼                   ▼                   ▼
       ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
       │ Event Service│   │Request Service│  │ User Service │
       └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
              │                  │                   │
              ▼                  ▼                   ▼
         PostgreSQL         PostgreSQL          PostgreSQL

                     ┌──────────────────┐
                     │  Eureka Server   │
                     │      :8761       │
                     └──────────────────┘

                     ┌──────────────────┐
                     │  Config Server   │
                     │      :8888       │
                     └──────────────────┘
```

Каждый основной сервис имеет собственную базу данных (**Database per Service**).

---

## Services

### Event Service

Отвечает за работу с событиями:

* создание и изменение событий;
* получение событий;
* поиск и фильтрация;
* категории;
* комментарии;
* compilations.

### Request Service

Отвечает за заявки пользователей на участие в событиях:

* создание заявки;
* получение заявок;
* изменение статуса;
* подтверждение и отклонение заявок.

### User Service

Отвечает за управление пользователями:

* создание пользователей;
* получение пользователей;
* изменение данных;
* удаление пользователей.

### Stats Service

Отдельный контур статистики:

* `stats-client` — клиент для взаимодействия со статистикой;
* `stats-dto` — общие DTO;
* `stats-server` — сервер статистики.

---

## Infrastructure

### API Gateway

Единая точка входа для внешних клиентов.

```text
Client
  │
  ▼
Gateway :8080
  │
  ├── /events/**
  ├── /users/**
  ├── /admin/users/**
  ├── /stats/**
  └── ...
```

Внешнему клиенту не требуется знать адреса отдельных микросервисов.

### Eureka

Используется для service discovery.

```text
Eureka :8761
```

Сервисы регистрируются в Eureka и могут находить друг друга по имени.

### Config Server

Централизованное хранение конфигурации сервисов.

```text
Config Server :8888
```

---

## Service Communication

Для синхронного взаимодействия между сервисами используется **OpenFeign**.

Например:

```text
Event Service
      │
      │ HTTP / OpenFeign
      ▼
Request Service
```

Для внутренних API используются отдельные internal endpoints.

---

## Project Structure

```text
java-plus-graduation/
│
├── core/
│   ├── event-service/
│   ├── request-service/
│   ├── user-service/
│   └── interactionapi/
│
├── infra/
│   ├── gateway-server/
│   ├── config-server/
│   └── discovery-server/
│
├── stats-service/
│   ├── stats-client/
│   ├── stats-dto/
│   └── stats-server/
│
├── docker-compose.yml
└── pom.xml
```

---

## Key Technical Decisions

### Database per Service

Каждый бизнес-сервис работает со своей базой данных.

Это позволяет отделить данные и ответственность сервисов друг от друга.

### API Gateway

Внешний трафик проходит через Gateway, а внутренние сервисы не являются основной точкой доступа для клиентов.

### Service Discovery

Eureka позволяет сервисам находить друг друга по имени без жёсткой привязки к конкретным адресам.

### Centralized Configuration

Конфигурация вынесена в отдельный Config Server.

### Microservice Decomposition

Система разделена по бизнес-ответственности:

```text
Events
Requests
Users
Statistics
```

---

## Running the Project

Для запуска используется Docker Compose.

Основные инфраструктурные компоненты:

```text
Gateway        :8080
Eureka         :8761
Config Server  :8888
```

После запуска внешние запросы выполняются через:

```text
http://localhost:8080
```

---

## What This Project Demonstrates

* проектирование микросервисной архитектуры;
* декомпозицию приложения по бизнес-областям;
* Spring Boot;
* Spring Cloud;
* API Gateway;
* Service Discovery;
* Config Server;
* OpenFeign;
* Database per Service;
* PostgreSQL;
* JPA / Hibernate;
* REST API;
* валидацию и обработку ошибок;
* Docker Compose;
* модульную структуру Maven;
* автоматические проверки качества кода и тестовое покрытие.
