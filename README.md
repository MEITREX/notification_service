# Notification Service

The Notification Service is a component designed to fulfill the following functions within the platform:

1. **Event ingestion & routing:** Consumes cross-service notification events (e.g., Media, Content) and routes them to target recipients (explicit user list or course-wide broadcast).
2. **Persistence & recipient tracking:** Persists notifications and creates recipient rows per target user with status management (`UNREAD`, `READ`, `DO_NOT_NOTIFY`).
3. **User preference filtering:** Respects per-user notification settings to mute specific categories; if the settings service is unavailable, it fails open to avoid missed notifications.
4. **Delivery interfaces:** Exposes unread counts, listing, mark-as-read (single/all), delete (single/all), and provides a live “notification added” stream.

## Notification Service

## Environment variables

### Relevant for deployment

| Name                         | Description                         | Default Value in Prod Environment                                                     |
|------------------------------|-------------------------------------|---------------------------------------------------------------------------------------|
| `spring.datasource.url`      | PostgreSQL database URL             | `jdbc:postgresql://notification-service-db-postgresql:5432/notification-service`     |
| `spring.datasource.username` | Database username                   | `postgres`                                                                            |
| `spring.datasource.password` | Database password                   | `*secret*`                                                                            |
| `DAPR_HTTP_PORT`             | Dapr HTTP Port                      | `3500`                                                                                |
| `DAPR_GRPC_PORT`             | Dapr gRPC Port                      | `50001`                                                                               |

### Other properties

| Name                               | Description                                       | Example / Default                    |
|------------------------------------|---------------------------------------------------|--------------------------------------|
| `services.course.base-url`         | Course Service base URL (member lookup)           | `http://course-service:8080`         |
| `services.user-settings.base-url`  | User Settings Service base URL (preference checks)| `http://user-settings-service:8080`  |
| `notifications.cleanup.enabled`    | Remove orphan notifications with no recipients    | `true`                               |
| `notifications.stream.buffer-size` | In-memory buffer size for the live publisher      | `256`                                |

## GraphQL API

The API is documented in the [`api.md` file](api.md).

It can be accessed at `/graphql` and explored via the GraphiQL Playground at `/graphiql`.

## Get started

A guide how to start development can be found in the
[wiki](https://meitrex.readthedocs.io/en/latest/dev-manuals/backend/get-started.html).
