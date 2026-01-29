#!/bin/bash

# Kafka Topic Setup Script for Loyalty Service
# This script creates Kafka topics with proper configuration for exactly-once semantics

KAFKA_HOST=${KAFKA_HOST:-localhost:9092}
TOPIC_NAME=${TOPIC_NAME:-loyalty.checkin}

echo "=========================================="
echo "Kafka Topic Setup for Loyalty Service"
echo "=========================================="
echo "Kafka Host: $KAFKA_HOST"
echo "Topic Name: $TOPIC_NAME"
echo ""

# Function to create topic
create_topic() {
    local partitions=$1
    local replication_factor=$2
    local retention_ms=$3
    local segment_ms=$4
    
    echo "Creating topic: $TOPIC_NAME"
    echo "  Partitions: $partitions"
    echo "  Replication Factor: $replication_factor"
    echo "  Retention: $retention_ms ms ($(($retention_ms / 86400000)) days)"
    echo "  Segment: $segment_ms ms"
    echo ""
    
    kafka-topics.sh --create \
        --bootstrap-server $KAFKA_HOST \
        --topic $TOPIC_NAME \
        --partitions $partitions \
        --replication-factor $replication_factor \
        --config retention.ms=$retention_ms \
        --config segment.ms=$segment_ms \
        --config min.insync.replicas=1 \
        --config compression.type=snappy \
        --config cleanup.policy=delete \
        --if-not-exists
    
    if [ $? -eq 0 ]; then
        echo "✓ Topic created successfully"
    else
        echo "✗ Failed to create topic"
        exit 1
    fi
}

# Function to describe topic
describe_topic() {
    echo ""
    echo "Topic description:"
    kafka-topics.sh --describe \
        --bootstrap-server $KAFKA_HOST \
        --topic $TOPIC_NAME
}

# Main execution
echo "=========================================="
echo "Configuration Options:"
echo "=========================================="
echo "1. Development (1M events/day) - 4 partitions"
echo "2. Production (10M events/day) - 8 partitions"
echo "3. High Load (50M events/day) - 16 partitions"
echo "4. Custom"
echo ""
read -p "Select configuration [1-4]: " choice

case $choice in
    1)
        echo "Creating topic for Development (1M events/day)..."
        create_topic 4 1 604800000 86400000  # 7 days retention, 1 day segments
        ;;
    2)
        echo "Creating topic for Production (10M events/day)..."
        create_topic 8 2 604800000 86400000  # 7 days retention, 1 day segments
        ;;
    3)
        echo "Creating topic for High Load (50M events/day)..."
        create_topic 16 3 604800000 86400000  # 7 days retention, 1 day segments
        ;;
    4)
        read -p "Enter number of partitions: " partitions
        read -p "Enter replication factor: " replication
        read -p "Enter retention days: " retention_days
        retention_ms=$((retention_days * 86400000))
        segment_ms=86400000
        echo "Creating custom topic..."
        create_topic $partitions $replication $retention_ms $segment_ms
        ;;
    *)
        echo "Invalid choice. Exiting."
        exit 1
        ;;
esac

describe_topic

echo ""
echo "=========================================="
echo "Topic setup completed successfully!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Start loyalty-service (producer)"
echo "2. Start Flink consumer job"
echo "3. Monitor with: kafka-console-consumer.sh --bootstrap-server $KAFKA_HOST --topic $TOPIC_NAME --from-beginning"
