@echo off
REM Kafka Topic Setup Script for Loyalty Service (Windows)
REM This script creates Kafka topics with proper configuration for exactly-once semantics

setlocal enabledelayedexpansion

set KAFKA_HOST=localhost:9092
set TOPIC_NAME=loyalty.checkin

if not "%~1"=="" set KAFKA_HOST=%~1
if not "%~2"=="" set TOPIC_NAME=%~2

echo ==========================================
echo Kafka Topic Setup for Loyalty Service
echo ==========================================
echo Kafka Host: %KAFKA_HOST%
echo Topic Name: %TOPIC_NAME%
echo.

echo ==========================================
echo Configuration Options:
echo ==========================================
echo 1. Development (1M events/day) - 4 partitions
echo 2. Production (10M events/day) - 8 partitions
echo 3. High Load (50M events/day) - 16 partitions
echo 4. Custom
echo.

set /p choice="Select configuration [1-4]: "

if "%choice%"=="1" (
    echo Creating topic for Development (1M events/day^)...
    set PARTITIONS=4
    set REPLICATION=1
    set RETENTION_MS=604800000
    set SEGMENT_MS=86400000
    goto create_topic
)

if "%choice%"=="2" (
    echo Creating topic for Production (10M events/day^)...
    set PARTITIONS=8
    set REPLICATION=2
    set RETENTION_MS=604800000
    set SEGMENT_MS=86400000
    goto create_topic
)

if "%choice%"=="3" (
    echo Creating topic for High Load (50M events/day^)...
    set PARTITIONS=16
    set REPLICATION=3
    set RETENTION_MS=604800000
    set SEGMENT_MS=86400000
    goto create_topic
)

if "%choice%"=="4" (
    set /p PARTITIONS="Enter number of partitions: "
    set /p REPLICATION="Enter replication factor: "
    set /p RETENTION_DAYS="Enter retention days: "
    set /a RETENTION_MS=!RETENTION_DAYS! * 86400000
    set SEGMENT_MS=86400000
    goto create_topic
)

echo Invalid choice. Exiting.
exit /b 1

:create_topic
echo.
echo Creating topic: %TOPIC_NAME%
echo   Partitions: %PARTITIONS%
echo   Replication Factor: %REPLICATION%
echo   Retention: %RETENTION_MS% ms
echo   Segment: %SEGMENT_MS% ms
echo.

kafka-topics.bat --create ^
    --bootstrap-server %KAFKA_HOST% ^
    --topic %TOPIC_NAME% ^
    --partitions %PARTITIONS% ^
    --replication-factor %REPLICATION% ^
    --config retention.ms=%RETENTION_MS% ^
    --config segment.ms=%SEGMENT_MS% ^
    --config min.insync.replicas=1 ^
    --config compression.type=snappy ^
    --config cleanup.policy=delete ^
    --if-not-exists

if %errorlevel% equ 0 (
    echo.
    echo Topic created successfully!
) else (
    echo.
    echo Failed to create topic
    exit /b 1
)

echo.
echo Topic description:
kafka-topics.bat --describe ^
    --bootstrap-server %KAFKA_HOST% ^
    --topic %TOPIC_NAME%

echo.
echo ==========================================
echo Topic setup completed successfully!
echo ==========================================
echo.
echo Next steps:
echo 1. Start loyalty-service (producer^)
echo 2. Start Flink consumer job
echo 3. Monitor with: kafka-console-consumer.bat --bootstrap-server %KAFKA_HOST% --topic %TOPIC_NAME% --from-beginning

endlocal
