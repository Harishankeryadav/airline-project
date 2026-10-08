-- Reference data: cities, airports (with IATA codes) and the airplane fleet from the original Node seeders.
INSERT INTO cities (name, created_at, updated_at) VALUES
    ('Bengaluru', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('Mysuru',    UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('Delhi',     UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('Mumbai',    UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('Kolkata',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('Chennai',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('Hyderabad', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO airports (name, code, address, city_id, created_at, updated_at)
SELECT 'Kempegowda International Airport', 'BLR', 'Devanahalli, Bengaluru', c.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM cities c WHERE c.name = 'Bengaluru';
INSERT INTO airports (name, code, address, city_id, created_at, updated_at)
SELECT 'Mysuru Airport', 'MYQ', 'Mandakalli, Mysuru', c.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM cities c WHERE c.name = 'Mysuru';
INSERT INTO airports (name, code, address, city_id, created_at, updated_at)
SELECT 'Indira Gandhi International Airport', 'DEL', 'New Delhi', c.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM cities c WHERE c.name = 'Delhi';
INSERT INTO airports (name, code, address, city_id, created_at, updated_at)
SELECT 'Chhatrapati Shivaji Maharaj International Airport', 'BOM', 'Andheri East, Mumbai', c.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM cities c WHERE c.name = 'Mumbai';
INSERT INTO airports (name, code, address, city_id, created_at, updated_at)
SELECT 'Netaji Subhas Chandra Bose International Airport', 'CCU', 'Dum Dum, Kolkata', c.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM cities c WHERE c.name = 'Kolkata';
INSERT INTO airports (name, code, address, city_id, created_at, updated_at)
SELECT 'Chennai International Airport', 'MAA', 'Meenambakkam, Chennai', c.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM cities c WHERE c.name = 'Chennai';
INSERT INTO airports (name, code, address, city_id, created_at, updated_at)
SELECT 'Rajiv Gandhi International Airport', 'HYD', 'Shamshabad, Hyderabad', c.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM cities c WHERE c.name = 'Hyderabad';

INSERT INTO airplanes (model_number, capacity, created_at, updated_at) VALUES
    ('Boeing 737',   300, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('AirBus A320',  350, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('Boeing 777',   400, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('Boeing 747',   320, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('AirBus A330',  370, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
