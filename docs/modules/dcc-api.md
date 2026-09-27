# dcc-api

`dcc-api` owns the HTTP-facing surface: Spring MVC endpoints, request/response DTOs and OpenAPI documentation. Keep transport mapping here and delegate business behavior to reusable services rather than embedding persistence logic in controllers.
