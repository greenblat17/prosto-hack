CREATE TABLE logistics.shipments (
    shipment_id SERIAL PRIMARY KEY,
    order_id INTEGER,
    ship_date DATE NOT NULL,
    delivery_date DATE,
    carrier VARCHAR(50) NOT NULL,
    origin_city VARCHAR(80) NOT NULL,
    destination_city VARCHAR(80) NOT NULL,
    weight_kg NUMERIC(8,2),
    shipping_cost NUMERIC(10,2),
    status VARCHAR(30) NOT NULL,
    is_express BOOLEAN DEFAULT false
);

INSERT INTO logistics.shipments (order_id, ship_date, delivery_date, carrier, origin_city, destination_city, weight_kg, shipping_cost, status, is_express)
SELECT
    (random() * 100000)::int + 1,
    DATE '2023-01-01' + (random() * 900)::int AS sd,
    DATE '2023-01-01' + (random() * 900)::int + (1 + (random()*14)::int),
    (ARRAY['СДЭК','Почта России','Деловые Линии','DPD','Boxberry','Яндекс Доставка'])[1 + (random()*5)::int],
    (ARRAY['Москва','Санкт-Петербург','Новосибирск','Екатеринбург','Казань','Краснодар'])[1 + (random()*5)::int],
    (ARRAY['Москва','Санкт-Петербург','Новосибирск','Екатеринбург','Казань','Краснодар','Самара','Ростов-на-Дону','Уфа','Воронеж','Пермь','Тюмень'])[1 + (random()*11)::int],
    0.5 + (random() * 50)::numeric(8,2),
    100 + (random() * 5000)::int / 100.0 * 100,
    (ARRAY['Доставлено','В пути','Ожидает отправки','Возврат','Потеряно'])[1 + (random()*4)::int],
    random() > 0.7
FROM generate_series(1, 50000);

CREATE INDEX idx_shipments_date ON logistics.shipments(ship_date);
CREATE INDEX idx_shipments_carrier ON logistics.shipments(carrier);
CREATE INDEX idx_shipments_status ON logistics.shipments(status);
