#!/bin/sh

curl --silent --show-error --include \
  --request GET 'http://localhost:8090/payment/9a44645c-fb30-457f-beb6-0d2e04652781' \
  --header 'Content-Type: application/json'