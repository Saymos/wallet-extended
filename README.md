# Cubeia Wallet Service

A Spring Boot-based wallet service that provides REST APIs for account creation, balance checks, and transfers between accounts.

## Project Overview

This application implements a simple wallet service with the following features:

- Create new accounts with zero balance
- Check account balances
- Transfer funds between accounts
- View transaction history for an account

The implementation ensures thread safety during concurrent transfers using pessimistic locking.

## Technologies

- Java 21
- Spring Boot 3.4.5
- Spring Data JPA
- H2 Database
- Validation
- Springdoc OpenAPI
- JaCoCo for test coverage

## Build Instructions

To build the application, run:

```bash
mvn clean verify
```

This will compile the code, run the tests (including integration tests), and generate a JaCoCo test coverage report at `target/site/jacoco/index.html`.

## Run Instructions

To run the application:

```bash
java -jar target/wallet-0.0.1-SNAPSHOT.jar
```

Or using Maven:

```bash
mvn spring-boot:run
```

The API will be available at http://localhost:8080

## API Documentation

The API documentation is available via Swagger UI at:

http://localhost:8080/swagger-ui.html

## API Endpoints

### Create Account

Creates a new account with zero balance.

```bash
curl -X POST http://localhost:8080/accounts -H "Content-Type: application/json"
```

### Get Account Balance

Retrieves the balance of an account.

```bash
curl -X GET http://localhost:8080/accounts/{accountId}/balance
```

### Transfer Funds

Transfers funds from one account to another.

```bash
curl -X POST http://localhost:8080/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1,
    "toAccountId": 2,
    "amount": 100.00
  }'
```

### Get Account Transactions

Retrieves all transactions involving an account.

```bash
curl -X GET http://localhost:8080/accounts/{accountId}/transactions
```

## Implementation Notes

- Pessimistic locking is used to ensure thread safety during concurrent transfers
- Comprehensive validation and error handling for API requests
- Detailed error responses with appropriate HTTP status codes
- Full test suite including unit tests and integration tests with concurrency testing 