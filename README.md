# Offer Engine Promotions POC

A Spring Boot proof-of-concept application that demonstrates an offer engine for managing promotions data. This service loads promotion data into PostgreSQL and Redis for efficient querying and caching.

## Overview

This project showcases a sample offer engine that:
- Manages promotional offers and related data
- Loads data into PostgreSQL for persistent storage
- Caches data in Redis for high-performance access
- Provides REST APIs to interact with the offer engine
- Demonstrates batch processing capabilities for large-scale data ingestion

## Technology Stack

- **Framework:** Spring Boot 3.5.15
- **Language:** Java 21
- **Build Tool:** Maven
- **Database:** PostgreSQL
- **Cache:** Redis
- **Key Dependencies:**
  - Spring Web - REST API support
  - Spring Batch - Batch processing and data loading
  - Spring Data Redis - Redis integration
  - Spring JDBC - Database connectivity
  - PostgreSQL Driver - PostgreSQL database driver

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- PostgreSQL (for database)
- Redis (for caching)

## Getting Started

### Installation

1. Clone the repository:
```bash
git clone https://github.com/rajukasturi/offer-engine-promotions-poc.git
cd offer-engine-promotions-poc
```

2. Configure your database and Redis connections in `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/offers_db
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.redis.host=localhost
spring.redis.port=6379
```

3. Build the project:
```bash
./mvnw clean build
```

### Running the Application

Start the application:
```bash
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080` by default.

## Project Structure

```
offer-engine-promotions-poc/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/sample/offer_engine_promotions/
│   │   │       ├── OfferEnginePromotionsApplication.java
│   │   │       ├── evaluation/          # Offer evaluation logic
│   │   │       ├── generator/           # Data generation utilities
│   │   │       └── loader/              # Data loading and batch processing
│   │   └── resources/
│   │       └── application.properties   # Application configuration
│   └── test/
│       └── java/
├── .mvn/                                # Maven wrapper
├── mvnw                                 # Maven wrapper script (Unix/Linux)
├── mvnw.cmd                             # Maven wrapper script (Windows)
├── pom.xml                              # Maven configuration
└── README.md
```

## Features

- **REST APIs** for managing and querying offers
- **Batch Processing** for loading large volumes of promotion data
- **PostgreSQL Integration** for reliable data persistence
- **Redis Caching** for improved performance
- **Spring Boot Starters** for simplified configuration
- **Offer Evaluation** engine for processing promotional offers
- **Data Generator** utilities for test data creation

## Testing

Run the test suite:
```bash
./mvnw test
```

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for bugs and feature requests.

## License

This project is currently unlicensed. Please refer to your organization's guidelines for usage.

## Notes

This is a proof-of-concept project. It may require additional development and hardening for production use.
