# Dockerfile to build CEL WASM module from Go source
#
# Build:
#   docker build -t go-cel-builder .

FROM golang:1.24-alpine AS builder

WORKDIR /build

# Copy dependency files
COPY go.mod go.sum ./

# Download dependencies
RUN go mod download

# Copy source
COPY main.go ./

# Build WASM (WASI target) with size optimization flags
RUN GOOS=wasip1 GOARCH=wasm go build -ldflags="-s -w" -o go-cel.wasm main.go
 
# Install Binaryen (wasm-opt)
ARG BINARYEN_VERSION=125
RUN apk add --no-cache wget ca-certificates && update-ca-certificates && \
    wget https://github.com/WebAssembly/binaryen/releases/download/version_${BINARYEN_VERSION}/binaryen-version_${BINARYEN_VERSION}-x86_64-linux.tar.gz && \
    tar xvf binaryen-version_${BINARYEN_VERSION}-x86_64-linux.tar.gz && \
    rm binaryen-version_${BINARYEN_VERSION}-x86_64-linux.tar.gz && \
    mv binaryen-version_${BINARYEN_VERSION} /opt/binaryen && \
    ln -s /opt/binaryen/bin/wasm-opt /usr/local/bin/wasm-opt

RUN wasm-opt go-cel.wasm -O3 --enable-bulk-memory-opt --duplicate-function-elimination -o go-cel-opt.wasm

# Final stage: minimal alpine image with just the WASM file
FROM alpine:3.19

COPY --from=builder /build/go-cel-opt.wasm /wasm/go-cel-opt.wasm

# Output WASM file to stdout when container runs
ENTRYPOINT ["cat", "/wasm/go-cel-opt.wasm"]
