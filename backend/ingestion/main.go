package main

import (
	"context"
	"encoding/binary"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/ClickHouse/clickhouse-go/v2/lib/driver"
)

// Структура должна точно соответствовать kernel/quotes_ioctl.h
// struct quote {
//     char symbol[8];      // 8 bytes
//     __u32 price_int;     // 4 bytes
//     __u32 price_frac;    // 4 bytes
//     __s32 change_bp;     // 4 bytes
//     __u64 timestamp;     // 8 bytes
// };
// Total size: 28 bytes.
const RECORD_SIZE = 28

type Quote struct {
	Symbol    string
	PriceInt  uint32
	PriceFrac uint32
	ChangeBp  int32
	Timestamp uint64
}

func parseQuote(data []byte) (*Quote, error) {
	if len(data) < RECORD_SIZE {
		return nil, fmt.Errorf("data too short: %d", len(data))
	}

	// Symbol: char[8], нужно обрезать нулевые байты
	symbolBytes := data[0:8]
	symbol := ""
	for _, b := range symbolBytes {
		if b == 0 {
			break
		}
		symbol += string(b)
	}

	priceInt := binary.LittleEndian.Uint32(data[8:12])
	priceFrac := binary.LittleEndian.Uint32(data[12:16])
	changeBp := int32(binary.LittleEndian.Uint32(data[16:20]))
	timestamp := binary.LittleEndian.Uint64(data[20:28])

	return &Quote{
		Symbol:    symbol,
		PriceInt:  priceInt,
		PriceFrac: priceFrac,
		ChangeBp:  changeBp,
		Timestamp: timestamp,
	}, nil
}

func connectClickHouse() (driver.Conn, error) {
	conn, err := clickhouse.Open(&clickhouse.Options{
		Addr: []string{"localhost:9000"},
		Auth: clickhouse.Auth{
			Database: "default",
			Username: "default",
			Password: "",
		},
		// Важно для стабильности
		MaxOpenConns: 10,
		MaxIdleConns: 5,
		ConnMaxLifetime: time.Hour,
	})
	if err != nil {
		return nil, err
	}

	if err := conn.Ping(context.Background()); err != nil {
		return nil, err
	}
	return conn, nil
}

func createTable(conn driver.Conn) error {
	err := conn.Exec(context.Background(), `
		CREATE TABLE IF NOT EXISTS quotes (
			ticker String,
			price Float64,
			volume UInt64,
			timestamp DateTime64(3)
		) ENGINE = MergeTree()
		ORDER BY (ticker, timestamp)
	`)
	return err
}

func main() {
	log.Println("Starting Go Ingestion Service...")

	// 1. Connect to ClickHouse
	conn, err := connectClickHouse()
	if err != nil {
		log.Fatalf("Failed to connect to ClickHouse: %v. Is Docker running?", err)
	}
	defer conn.Close()
	log.Println("Connected to ClickHouse")

	// 2. Ensure Table Exists
	if err := createTable(conn); err != nil {
		log.Fatalf("Failed to create table: %v", err)
	}
	log.Println("Table 'quotes' ready")

	// 3. Open Device
	devPath := os.Getenv("QUOTES_DEV")
	if devPath == "" {
		devPath = "/dev/quotes"
	}

	file, err := os.Open(devPath)
	if err != nil {
		log.Fatalf("Failed to open device %s: %v. Is the kernel module loaded? (sudo insmod ...)", devPath, err)
	}
	defer file.Close()
	log.Printf("Opened device %s", devPath)

	// 4. Read Loop
	buffer := make([]byte, RECORD_SIZE)
	batch := make([]Quote, 0, 100)

	// Таймер для периодического сброса батча (например, каждые 500мс)
	flushTicker := time.NewTicker(500 * time.Millisecond)
	defer flushTicker.Stop()

	ctx := context.Background()

	for {
		select {
		case <-flushTicker.C:
			if len(batch) > 0 {
				if err := flushBatch(ctx, conn, batch); err != nil {
					log.Printf("Error flushing batch: %v", err)
				} else {
					log.Printf("Flushed %d quotes", len(batch))
				}
				batch = batch[:0]
			}
		default:
			// Устанавливаем таймаут на чтение, чтобы можно было прервать цикл при необходимости
			// Но для простоты используем блокирующее чтение с проверкой ошибки
			n, err := file.Read(buffer)
			if err != nil {
				log.Printf("Error reading from device: %v", err)
				time.Sleep(1 * time.Second) // Retry delay
				continue
			}

			if n != RECORD_SIZE {
				log.Printf("Incomplete record read: %d bytes (expected %d)", n, RECORD_SIZE)
				continue
			}

			q, err := parseQuote(buffer)
			if err != nil {
				log.Printf("Error parsing quote: %v", err)
				continue
			}

			batch = append(batch, *q)

			// Immediate flush if batch is large
			if len(batch) >= 100 {
				if err := flushBatch(ctx, conn, batch); err != nil {
					log.Printf("Error flushing batch: %v", err)
				} else {
					log.Printf("Flushed %d quotes (full batch)", len(batch))
				}
				batch = batch[:0]
			}
		}
	}
}

func flushBatch(ctx context.Context, conn driver.Conn, batch []Quote) error {
	batchInsert, err := conn.PrepareBatch(ctx, "INSERT INTO quotes (ticker, price, volume, timestamp)")
	if err != nil {
		return err
	}

	for _, q := range batch {
		// Calculate full price: price_int + price_frac / 100
		price := float64(q.PriceInt) + float64(q.PriceFrac)/100.0

		// Convert ktime_get_real_ns (nanoseconds) to time.Time
		t := time.Unix(0, int64(q.Timestamp))

		// Volume is not in the struct, set to 0 or random if needed
		err := batchInsert.Append(q.Symbol, price, uint64(0), t)
		if err != nil {
			return err
		}
	}

	return batchInsert.Send()
}
