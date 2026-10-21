GO ?= go
BIN_DIR := bin
BIN := $(BIN_DIR)/dcc
PKG := github.com/open-banking-labs/dcc/dcc-cli

VERSION ?= $(shell git describe --tags --always --dirty 2>/dev/null || echo dev)
COMMIT ?= $(shell git rev-parse --short HEAD 2>/dev/null || echo none)
DATE ?= $(shell date -u +%Y-%m-%dT%H:%M:%SZ)

LDFLAGS := -s -w \
	-X $(PKG)/internal/build.Version=$(VERSION) \
	-X $(PKG)/internal/build.Commit=$(COMMIT) \
	-X $(PKG)/internal/build.Date=$(DATE)

.PHONY: all build test vet fmt lint install snapshot clean

all: build

build:
	@mkdir -p $(BIN_DIR)
	$(GO) build -trimpath -ldflags "$(LDFLAGS)" -o $(BIN) ./cmd/dcc

test:
	$(GO) test ./...

vet:
	$(GO) vet ./...

fmt:
	gofmt -w .

lint: vet
	@test -z "$$(gofmt -l .)" || (echo "gofmt needed:"; gofmt -l .; exit 1)

install:
	$(GO) install -trimpath -ldflags "$(LDFLAGS)" ./cmd/dcc

snapshot:
	goreleaser build --snapshot --clean

clean:
	rm -rf $(BIN_DIR) dist
