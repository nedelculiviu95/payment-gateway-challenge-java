## Requirements
- JDK 17
- Docker with Docker Compose for the bank simulator and integration tests

## Template structure

src/main/ - The Spring Boot payment gateway and application configuration

src/test/ - Unit tests, JSON request tests, and bank simulator integration tests

imposters/ - contains the bank simulator configuration. Don't change this

.editorconfig - don't change this. It ensures a consistent set of rules for submissions when reformatting code

docker-compose.yml - configures the bank simulator


## API Documentation
For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**

## Running locally

Start the supplied acquiring bank simulator:

```sh
docker compose up -d
```

Start the payment-gateway from project root directory, which listens on port 8090:

```sh
./gradlew bootRun
```

The bank connection is configured with Spring properties or environment variables:

| Property | Environment variable | Default |
| --- | --- | --- |
| `bank.base-url` | `BANK_BASE_URL` | `http://localhost:8080` |
| `bank.connect-timeout` | `BANK_CONNECT_TIMEOUT` | `2s` |
| `bank.read-timeout` | `BANK_READ_TIMEOUT` | `5s` |

The application validates these settings at startup. The bank URL must include
an explicit port, such as `http://localhost:8080` or `https://bank.example:443`.

## Processing a payment

Send a request with synthetic card data:

```sh
curl -i http://localhost:8090/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "card_number": "2222405343248877",
    "expiry_month": 12,
    "expiry_year": 2030,
    "currency": "GBP",
    "amount": 1050,
    "cvv": "012"
  }'
```

Both authorized and declined payments return `201 Created`: a payment record
has been created, regardless of the bank's decision. The `Location` header points
to `GET /payment/{id}` for retrieving that record. The response body contains the
payment ID, status, last four card digits, expiry, currency, and amount; it
excludes the full card number and CVV.

A typical authorized response includes:

```http
HTTP/1.1 201 Created
Location: /payment/d8e540dd-a62d-47bd-bc0e-341e0aabcb8e
Content-Type: application/json
```

With this body:

```json
{
  "id": "d8e540dd-a62d-47bd-bc0e-341e0aabcb8e",
  "status": "Authorized",
  "cardNumberLastFour": "8877",
  "expiryMonth": 12,
  "expiryYear": 2030,
  "currency": "GBP",
  "amount": 1050
}
```

Retrieve a payment by its returned ID (replace the example UUID with the one
returned by your POST request):

```sh
curl -i http://localhost:8090/payment/d8e540dd-a62d-47bd-bc0e-341e0aabcb8e
```

Retrieval returns `200 OK` with the same payment fields.

## Validation and failures

- Card numbers must contain 14 to 19 ASCII digits; CVVs must contain 3 or 4.
- Currency must be exactly `GBP`, `USD`, or `EUR`.
- Amount is represented by a Java `Integer` in minor currency units and must be
  positive, within the signed 32-bit range (up to `2,147,483,647`).
- Expiry month must be 1 to 12 and year 1 to 9999. The combined expiry must be
  strictly later than the current UTC month. The current month is rejected.
- Missing required values and values that fail field validation are rejected.
- bank.connect-timeout and bank.read-timeout must be between 1 ms and 2,147,483,647 ms.
- Floating-point JSON numbers are rejected for integer fields before field
  validation. Other JSON conversions use Jackson's defaults; unknown fields are
  ignored.

`POST /payment` requests that fail field validation or JSON parsing return
`400 Bad Request` with
`code: "PAYMENT_REJECTED"` and `status: "Rejected"`. Field validation failures
identify the field, error code, and validation message, such as `currency` with
`UNSUPPORTED_CURRENCY` and "Currency must be GBP, USD or EUR". Invalid JSON
returns the message "The payment request JSON is invalid". These responses do
not include submitted card values, stack traces, or internal exception messages.
Neither failure calls the bank or creates a payment record.

### `POST /payment` error responses

| Failure | HTTP status | Error code | Error message |
| --- | --- | --- | --- |
| Invalid payment request fields | 400 | `PAYMENT_REJECTED` | The payment request is invalid |
| Invalid payment request JSON | 400 | `PAYMENT_REJECTED` | The payment request JSON is invalid |
| Bank unavailable | 503 | `BANK_UNAVAILABLE` | The bank is unavailable; the payment outcome may be unknown |
| Bank timeout | 504 | `BANK_TIMEOUT` | The bank request timed out; the payment outcome is unknown |
| Invalid bank response | 502 | `BANK_RESPONSE_INVALID` | The bank returned an invalid response; the payment outcome may be unknown |
| Unexpected application error | 500 | `INTERNAL_ERROR` | An unexpected error occurred |

Bank failures never become a decline or rejection ad stored in the store. A timeout can mean that the
bank processed the payment but its response was lost. The gateway does not
implement automatic authorization retries; safe retries require bank-supported
idempotency and reconciliation.

Example error response:

```http
HTTP/1.1 400 
Cache-Control: no-store
Content-Type: application/json
```

With this body:

```json
{"code":"PAYMENT_REJECTED","message":"The payment request is invalid","status":"Rejected","errors":[{"field":"cvv","code":"INVALID_FORMAT","message":"CVV must contain 3 or 4 digits"}]}
```

### `GET /payment/{id}` error responses

Retrieval reads the stored payment without calling the bank.

| Failure | HTTP status | Error code | Error message |
| --- | --- | --- | --- |
| Malformed payment ID | 400 | `INVALID_PAYMENT_ID` | Payment ID must be a valid UUID |
| Unknown payment | 404 | `PAYMENT_NOT_FOUND` | Payment not found |
| Unexpected application error | 500 | `INTERNAL_ERROR` | An unexpected error occurred |

## Assumptions

| Assumption | What the implementation does |
| --- | --- |
| Amounts must be positive | Rejects zero and negative amounts; the brief requires an integer but gives no minimum. |
| Expiry must be after the current month | Rejects cards expiring this month and uses UTC to determine the current month; this interprets the brief's requirement that expiry be in the future. |
| Currency matching is exact | Accepts only `GBP`, `USD`, and `EUR`, rejecting lowercase values and surrounding spaces; the brief leaves the choice of currencies to the implementation. |
| Values have practical limits | Limits expiry years to `9999`. |
| An authorization needs a reference code | Treats `authorized: true` without a nonblank authorization code as an invalid bank response. |
| Unknown request fields are ignored | Extra JSON fields do not cause rejection; other conversions use Jackson's defaults, except floating-point numbers cannot become integers. |
| Technical failures do not create payment records | Stores only confirmed authorized or declined results; bank failures return an error without a completed payment record. |

## Key design decisions

| Decision | Rationale |
| --- | --- |
| `POST /payment` returns the stored bank decision | Both authorized and declined payments create a record, so both return `201 Created`. The body provides the payment result immediately, and `Location: /payment/{id}` identifies the record for later retrieval. Invalid requests return `400` without calling the bank. |
| `GET /payment/{id}` retrieves a payment | Reads the stored result without calling the bank; returns `200`|
| Synchronous bank calls with typed messages | The payment flow waits for the bank's decision before responding, so a blocking client fits this Spring MVC application. `BankRestClient` uses `RestTemplate.postForEntity` to send typed requests and receive a typed response body together with its HTTP status. Spring handles JSON conversion, while the client checks for HTTP `200`, validates the decision, and maps bank failures to gateway errors. |
| Temporary storage | Records stay in memory rather than a database and disappear when the application restarts. |
| Only confirmed decisions are stored | Both `Authorized` and `Declined` payments get a record in the in-memory storage; invalid requests and bank failures do not. |
| Protected payment records via immutable Payment objects | Stored payments cannot be changed after creation and exclude the full card number and security code (CVV). |
| Integration Tests with the bank | They cover key scenarios: authorized and declined payments followed by retrieval, and the simulator's `503` response |
| Logging | Requests produce `Before request` and `After request` messages in the console, showing the method and URL path. Request bodies, headers, and query strings are excluded. Requests are not logged to files.|

### Future improvements

Possible next steps beyond this exercise:

- **Using test containers:** At the moment to run the integration tests, the bank-simulator container
  must run in order to run the integration tests.
- **Central configuration:** Keep supported currencies in shared settings alongside
  the bank URLs and timeouts, which are already configurable.
- **Improved logging:** Make requests and failures easier to trace and add file logging as well, not only console
- **Concurrency:** Make storage safe when several payments are processed at once.
- **Idempotency:** Recognise repeated requests so the same payment is not processed twice.
- **Safe retries:** Retry temporary failures only with the bank's duplicate-payment
  protection, so a customer is not charged twice.
- **Authentication and authorisation:** Verify who is calling and allow each merchant
  to access only their own payments.
- **TLS:** Require encrypted HTTPS connections for both merchant requests and bank calls.

## Verification and design

Run the unit tests:

```sh
./gradlew test
```

The tests covers request validation, payment processing, repository
behavior, stored payment rules, bank response validation and error handling,
configuration, controller delegation, and HTTP error responses. JSON request
tests verify that floating-point amounts are rejected before a bank call and
that integer amounts retain their value.

Run the integration tests against the supplied bank simulator:

```sh
docker compose up -d
./gradlew integrationTest
```

The integration tests start the gateway on a random port and use its real bank
client and in-memory repository. They cover authorized and declined payments
followed by retrieval, and the simulator's `503` response. They verify that the
simulator is running on its default ports (`8080` and management port `2525`)
and fail if it is unavailable. Only synthetic card data is used. These tests
are excluded from `./gradlew test`.
