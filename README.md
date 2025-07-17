# REST Router Service

This REST Router Service is built using Java 21 and Spring Boot, utilizing modern non-blocking I/O principles. It provides efficient routing of HTTP requests to multiple backend services with support for dynamic service configuration, rate limiting, and custom routing strategies.

## Features

- **Non-blocking I/O**: Built on Spring WebFlux using Project Reactor for asynchronous request handling.
- **Rate Limiting**: Configurable per service using Resilience4j.
- **Dynamic Service Updates**: Manage services via a REST API without needing to restart.
- **Custom Routing Strategies**: Supports routing based on traffic weights and HTTP headers.
- **Plugin Architecture**: Easily extendable with custom pre-processing and post-processing logic.

## Rate Limiting

The router implements client-specific rate limiting using Resilience4j. Each service can define both default and client-specific rate limits.

### Rate Limit Configuration

Rate limits can be configured at two levels:
1. **Default Rate Limit**: Applied when no client-specific limit exists or when no client ID is provided
2. **Client-Specific Rate Limits**: Applied based on client identification via headers

Example configuration:
```yaml
router:
  services:
    servicea:
      baseUrl: http://servicea-backend:8080
      clientIdHeader: X-Client-Id  # Custom header for client identification
      defaultRateLimit:
        limit: 100    # 100 requests
        period: MINUTE  # per minute
      clientRateLimits:
        premium-client:
          limit: 1000   # 1000 requests
          period: MINUTE # per minute
        basic-client:
          limit: 100    # 100 requests
          period: MINUTE # per minute
```

### Client Identification

Clients are identified using a configurable header (default: `X-Client-Id`). You can customize this header per service:

```yaml
services:
  servicea:
    clientIdHeader: X-API-Key  # Custom header name
```

### Making Requests

Include the client ID header in your requests to apply client-specific rate limits:

```bash
# Premium client (1000 requests per minute)
curl -H "X-Client-Id: premium-client" http://localhost:8080/servicea/endpoint

# Basic client (100 requests per minute)
curl -H "X-Client-Id: basic-client" http://localhost:8080/servicea/endpoint

# No client ID (uses default rate limit - 100 requests per minute)
curl http://localhost:8080/servicea/endpoint
```

### Dynamic Updates

Rate limits can be updated dynamically using the admin API:

```bash
curl -X PUT http://localhost:8080/admin/services/servicea \
  -H "Content-Type: application/json" \
  -d '{
    "baseUrl": "http://servicea-backend:8080",
    "clientIdHeader": "X-Client-Id",
    "defaultRateLimit": {
      "limit": 50,
      "period": "MINUTE"
    },
    "clientRateLimits": {
      "premium-client": {
        "limit": 500,
        "period": "MINUTE"
      }
    }
  }'
```

### Rate Limit Periods

Supported rate limit periods:
- `SECOND` - Per second rate limiting
- `MINUTE` - Per minute rate limiting
- `HOUR` - Per hour rate limiting
- `DAY` - Per day rate limiting

### Error Handling

When rate limits are exceeded, the service returns:
- HTTP Status: `429 Too Many Requests`
- Response Header: `X-RateLimit-Retry-After` with the number of nanoseconds until the rate limit resets

## How It Works

### Request Flow

1. **Routing**: Incoming requests are analyzed to determine the target service. The service name is extracted from the request path.
2. **Rate Limiting**: Requests are rate-limited per service using Resilience4j.
3. **Route Selection**: Routes are selected based on defined strategies, such as weighted traffic distribution and header-based routing.
4. **Forwarding**: Requests are forwarded to the appropriate backend service and the response is returned to the client.
5. **Logging**: Requests without payloads are logged to a specified destination.

### Configuration

- **Service Configuration** is done through a YAML configuration file or via dynamic updates through the admin REST API.
- **Routing Strategies** can be based on random traffic weight distribution (`WeightedTrafficStrategy`) or specific header values (`HeaderBasedStrategy`).

### Example Configuration

```yaml
router:
  services:
    servicea:
      baseUrl: http://servicea-backend:8080
      clientIdHeader: X-Client-Id  # Header to identify clients
      defaultRateLimit:
        limit: 100
        period: MINUTE
      clientRateLimits:
        premium-client:
          limit: 1000
          period: MINUTE
        basic-client:
          limit: 100
          period: MINUTE
      routes:
        # Route 80% of traffic to v1
        - path: /servicea
          targetUrl: http://servicea-backend-v1:8080
          weight: 80
        
        # Route 20% of traffic to v2
        - path: /servicea
          targetUrl: http://servicea-backend-v2:8080
          weight: 20

        # Route specific traffic to v2 based on headers
        - path: /servicea
          targetUrl: http://servicea-backend-v2:8080
          headers:
            x-version: v2
            x-client-id: beta-tester
```

### Admin API

- **GET** `/admin/services`: List all services.
- **GET** `/admin/services/{serviceName}`: Get a specific service.
- **POST** `/admin/services/{serviceName}`: Register a new service.
- **PUT** `/admin/services/{serviceName}`: Update an existing service.
- **DELETE** `/admin/services/{serviceName}`: Remove a service.

### Building the Project

To build and run the project:

1. Ensure you have [Maven](https://maven.apache.org/install.html) installed.
2. Navigate to the project directory.
3. Run:
   ```bash
   ./mvnw clean install
   ```
4. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

The application will be accessible at `http://localhost:8080` by default.

## Extending the Router

### Creating Custom Plugins

The router supports custom plugins for request/response processing. To create a custom plugin, implement the `RouterPlugin` interface:

```java
public interface RouterPlugin {
    Mono<ServerHttpRequest> preProcess(ServerHttpRequest request);
    Mono<ServerHttpResponse> postProcess(ServerHttpResponse response);
    int getOrder();
}
```

Example plugin implementation:

```java
@Component
public class CustomHeaderPlugin implements RouterPlugin {
    @Override
    public Mono<ServerHttpRequest> preProcess(ServerHttpRequest request) {
        return Mono.just(request.mutate()
                .header("X-Custom-Header", "custom-value")
                .build());
    }

    @Override
    public Mono<ServerHttpResponse> postProcess(ServerHttpResponse response) {
        response.getHeaders().add("X-Response-Header", "processed");
        return Mono.just(response);
    }

    @Override
    public int getOrder() {
        return 1; // plugins are executed in order (lower numbers first)
    }
}
```

### Creating Custom Routing Strategies

To implement a custom routing strategy, implement the `RouteSelectionStrategy` interface:

```java
public interface RouteSelectionStrategy {
    Optional<RouterProperties.RouteConfig> selectRoute(
        List<RouterProperties.RouteConfig> routes,
        ServerRequest request
    );
}
```

Example strategy implementation:

```java
@Component
public class GeoLocationStrategy implements RouteSelectionStrategy {
    @Override
    public Optional<RouterProperties.RouteConfig> selectRoute(
            List<RouterProperties.RouteConfig> routes,
            ServerRequest request) {
        String country = request.headers()
                .firstHeader("X-Country-Code");
        
        return routes.stream()
                .filter(route -> matchesCountry(route, country))
                .findFirst();
    }

    private boolean matchesCountry(RouterProperties.RouteConfig route,
                                 String country) {
        return route.getHeaders().getOrDefault("X-Country-Code", "*")
                .equals(country);
    }
}
```

Register your custom components in the Spring configuration:

```java
@Configuration
public class CustomConfig {
    @Bean
    public List<RouteSelectionStrategy> routingStrategies(
            HeaderBasedStrategy headerBasedStrategy,
            WeightedTrafficStrategy weightedTrafficStrategy,
            GeoLocationStrategy geoLocationStrategy) {
        return List.of(
            geoLocationStrategy,      // Try geo-location first
            headerBasedStrategy,      // Then try header matching
            weightedTrafficStrategy  // Finally, fall back to weighted distribution
        );
    }
}
```

## Docker Deployment

The service can be deployed using Docker and Docker Compose. The repository includes both a `Dockerfile` and a `docker-compose.yml` file for easy deployment.

### Building and Running with Docker

```bash
# Build the Docker image
docker build -t rest-router .

# Run the container
docker run -p 8080:8080 rest-router
```

### Using Docker Compose

The included `docker-compose.yml` file sets up both the router service and a Kafka instance for logging:

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down
```

The Docker Compose configuration includes:

- REST Router Service with configurable memory settings
- Kafka for request logging
- Volume mapping for external configuration

### Configuration with Docker

Create a `config` directory in your project root and place your `application.yml` there. The Docker Compose configuration will mount this directory into the container.

Environment variables can be adjusted in the `docker-compose.yml` file:

```yaml
environment:
  - SPRING_PROFILES_ACTIVE=prod
  - JAVA_OPTS=-Xmx512m -Xms256m
```
