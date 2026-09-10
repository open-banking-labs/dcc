# RabbitMQ definitions

Mounted read-only into the RabbitMQ container at `/etc/rabbitmq/definitions`
(see `docker-compose.yml`).

Put an exported `definitions.json` here to pre-create exchanges, queues,
bindings, users and policies at startup.

> **The mount alone does nothing.** RabbitMQ only loads this file if it is told
> where it is. The compose service does not currently do that, so today this
> mount is inert. To activate it, add to the `rabbitmq` service environment:

```yaml
RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS: >-
  -rabbitmq_management load_definitions "/etc/rabbitmq/definitions/definitions.json"
```

> This is left commented out deliberately: if `load_definitions` points at a
> file that does not exist, **RabbitMQ fails to start**. Enable it only once a
> real `definitions.json` is committed here.

Export the file from a running instance with:

```bash
docker-compose exec rabbitmq rabbitmqctl export_definitions /etc/rabbitmq/definitions/definitions.json
```
