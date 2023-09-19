#!/bin/sh

curl --silent --show-error --include \
  --request POST 'http://localhost:8090/payment' \
  --header 'Content-Type: application/json' \
  --data-raw '{
    "card_number": "2222405343248873",
    "expiry_month": 5,
    "expiry_year": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }'
