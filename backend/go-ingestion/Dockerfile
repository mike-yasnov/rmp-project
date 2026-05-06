FROM golang:1.22-alpine AS build

WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /out/go-ingestion .

FROM alpine:3.20
RUN adduser -D -H app
USER app
COPY --from=build /out/go-ingestion /usr/local/bin/go-ingestion
ENTRYPOINT ["go-ingestion"]
