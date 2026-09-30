#!/bin/sh
# Demo CA only. Usage: generate-certs.sh [output-dir]  (default /certs)
set -eu
OUT=${1:-/certs}
if [ -f "$OUT/ca.crt" ]; then
  exit 0
fi
command -v openssl >/dev/null 2>&1 || apk add --no-cache openssl
mkdir -p "$OUT"
cd "$OUT"

openssl req -x509 -newkey rsa:2048 -sha256 -days 365 -nodes \
  -keyout ca.key -out ca.crt \
  -subj "/CN=demo-ca"

openssl req -newkey rsa:2048 -nodes -keyout server.key -out server.csr \
  -subj "/CN=server"
cat > server.ext <<'EOF'
subjectAltName=DNS:server,DNS:localhost,IP:127.0.0.1
extendedKeyUsage=serverAuth
keyUsage=digitalSignature,keyEncipherment
basicConstraints=CA:FALSE
EOF
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out server.crt -days 365 -sha256 -extfile server.ext
openssl pkcs8 -topk8 -nocrypt -in server.key -out server.pkcs8.pem

cat > client.ext <<'EOF'
extendedKeyUsage=clientAuth
keyUsage=digitalSignature
basicConstraints=CA:FALSE
EOF
openssl req -newkey rsa:2048 -nodes -keyout client.key -out client.csr \
  -subj "/CN=demo-client"
openssl x509 -req -in client.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out client.crt -days 365 -sha256 -extfile client.ext
openssl pkcs8 -topk8 -nocrypt -in client.key -out client.pkcs8.pem

openssl req -x509 -newkey rsa:2048 -sha256 -days 365 -nodes \
  -keyout stranger-ca.key -out stranger-ca.crt \
  -subj "/CN=stranger-ca"
openssl req -newkey rsa:2048 -nodes -keyout stranger.key -out stranger.csr \
  -subj "/CN=stranger"
openssl x509 -req -in stranger.csr -CA stranger-ca.crt -CAkey stranger-ca.key -CAcreateserial \
  -out stranger.crt -days 365 -sha256 -extfile client.ext
openssl pkcs8 -topk8 -nocrypt -in stranger.key -out stranger.pkcs8.pem

rm -f ./*.csr ./*.ext ./*.srl
chmod 644 ./*.crt ./*.pem ./*.key
