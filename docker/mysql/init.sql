-- One database per microservice. The 'airline' user is created by the MYSQL_USER env var.
CREATE DATABASE IF NOT EXISTS flights_db   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS auth_db      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS booking_db   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS reminder_db  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON flights_db.*  TO 'airline'@'%';
GRANT ALL PRIVILEGES ON auth_db.*     TO 'airline'@'%';
GRANT ALL PRIVILEGES ON booking_db.*  TO 'airline'@'%';
GRANT ALL PRIVILEGES ON reminder_db.* TO 'airline'@'%';
FLUSH PRIVILEGES;
