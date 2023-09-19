# Instructions for candidates

This is the Java version of the Payment Gateway challenge. If you haven't already read this [README.md](https://github.com/cko-recruitment/) on the details of this exercise, please do so now.

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
Timeouts must be between 1 ms and 2,147,483,647 ms.

The HTTP request factory does not automatically follow redirects for bank POST
requests. The gateway does not implement automatic authorization retries.

The bank client uses `RestTemplate.postForEntity` with typed request and response
objects. Spring's message converters handle JSON and content types. The client
requires HTTP `200` and a valid bank decision; bank `5xx` errors map to
unavailability, while `4xx` and response conversion failures map to an invalid
bank response. There is no custom response-body size limit.

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

A typical result is:

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

Retrieval returns `200 OK` with the same payment fields. An unknown ID returns
`404 Not Found`; malformed UUIDs return `400 Bad Request`.

## Validation and failures

- Card numbers must contain 14 to 19 ASCII digits; CVVs must contain 3 or 4.
- Currency must be exactly `GBP`, `USD`, or `EUR`.
- Amount is represented by a Java `Integer` in minor currency units and must be
  positive, within the signed 32-bit range (up to `2,147,483,647`).
- Expiry month must be 1 to 12 and year 1 to 9999. The combined expiry must be
  strictly later than the current UTC month. The current month is rejected.
- Missing required values and values that fail field validation are rejected.

Floating-point JSON numbers are rejected for integer fields before field
validation. Other JSON conversions use Jackson's defaults; unknown fields are
ignored.

Requests that fail field validation or JSON parsing return `400` with
`status: "Rejected"` and safe error details. They do not call the bank or create a
payment. Positive amounts and strict future-month expiry are documented
interpretations of the challenge.

| Failure | HTTP status | Error code |
| --- | --- | --- |
| Invalid payment request | 400 | `PAYMENT_REJECTED` |
| Malformed payment ID | 400 | `INVALID_PAYMENT_ID` |
| Unknown payment | 404 | `PAYMENT_NOT_FOUND` |
| Bank unavailable | 503 | `BANK_UNAVAILABLE` |
| Bank timeout | 504 | `BANK_TIMEOUT` |
| Invalid bank response | 502 | `BANK_RESPONSE_INVALID` |
| Unexpected application error | 500 | `INTERNAL_ERROR` |

Bank failures never become a decline or rejection. A timeout can mean that the
bank processed the payment but its response was lost. The gateway does not
implement automatic authorization retries; safe retries require bank-supported
idempotency and reconciliation.

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

| Decision | Reason to document |
| --- | --- |
| One simple application | Keeps the exercise easy to follow, with separate responsibilities for receiving requests, checking details, contacting the bank, and storing results. |
| `POST /payment` processes a payment | Checks the payment details, waits for the bank's decision, saves it, and returns a `201 Created` with payment details with a link for retrieval; invalid details return `400` without calling the bank. Both an authorized payment and a declined payment return `201 Created`, with a
`Location` header and a complete payment result|
| `GET /payment/{id}` retrieves a payment | Reads the stored result without calling the bank; returns `200` when found, `404` when missing, or `400` for an invalid ID. |
| Temporary storage | Records stay in memory rather than a database and disappear when the application restarts. |
| Only confirmed decisions are stored | Both `Authorized` and `Declined` payments get a record in the in-memory storage; invalid requests and bank failures do not. |
| Protected payment records via immutable Payment objects | Stored payments cannot be changed after creation and exclude the full card number and security code (CVV). |
| Checks match the brief | Keeps validation within the exercise's scope without adding extra card-number verification rules. |
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
