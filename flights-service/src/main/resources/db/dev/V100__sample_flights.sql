-- DEV ONLY (loaded by the 'dev' profile): sample flights departing 7-9 days from first startup.
INSERT INTO flights (flight_number, airplane_id, departure_airport_id, arrival_airport_id, departure_time, arrival_time,
                     price, boarding_gate, total_seats, available_seats, created_at, updated_at)
SELECT 'AI-101', p.id, d.id, a.id,
       DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 7 DAY),
       DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 7 DAY) + INTERVAL 170 MINUTE,
       5200.00, 'A12', p.capacity, p.capacity, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM airplanes p, airports d, airports a
WHERE p.model_number = 'Boeing 737' AND d.code = 'BLR' AND a.code = 'DEL';

INSERT INTO flights (flight_number, airplane_id, departure_airport_id, arrival_airport_id, departure_time, arrival_time,
                     price, boarding_gate, total_seats, available_seats, created_at, updated_at)
SELECT 'AI-102', p.id, d.id, a.id,
       DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 8 DAY),
       DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 8 DAY) + INTERVAL 175 MINUTE,
       5600.00, 'B04', p.capacity, p.capacity, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM airplanes p, airports d, airports a
WHERE p.model_number = 'AirBus A320' AND d.code = 'DEL' AND a.code = 'BLR';

INSERT INTO flights (flight_number, airplane_id, departure_airport_id, arrival_airport_id, departure_time, arrival_time,
                     price, boarding_gate, total_seats, available_seats, created_at, updated_at)
SELECT '6E-201', p.id, d.id, a.id,
       DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 7 DAY) + INTERVAL 4 HOUR,
       DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 7 DAY) + INTERVAL 6 HOUR + INTERVAL 40 MINUTE,
       4300.00, 'C07', p.capacity, p.capacity, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM airplanes p, airports d, airports a
WHERE p.model_number = 'Boeing 777' AND d.code = 'CCU' AND a.code = 'BOM';

INSERT INTO flights (flight_number, airplane_id, departure_airport_id, arrival_airport_id, departure_time, arrival_time,
                     price, boarding_gate, total_seats, available_seats, created_at, updated_at)
SELECT '6E-305', p.id, d.id, a.id,
       DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 9 DAY),
       DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 9 DAY) + INTERVAL 80 MINUTE,
       2900.00, 'A03', p.capacity, p.capacity, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM airplanes p, airports d, airports a
WHERE p.model_number = 'AirBus A330' AND d.code = 'BLR' AND a.code = 'MAA';
