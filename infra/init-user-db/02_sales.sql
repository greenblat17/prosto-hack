CREATE TABLE sales.orders (
    order_id SERIAL PRIMARY KEY,
    order_date DATE NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    region VARCHAR(50) NOT NULL,
    city VARCHAR(80) NOT NULL,
    product_category VARCHAR(50) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    total_amount NUMERIC(14,2) NOT NULL,
    discount_pct NUMERIC(5,2) DEFAULT 0,
    payment_method VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    manager VARCHAR(80)
);

CREATE TABLE sales.customers (
    customer_id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150),
    phone VARCHAR(30),
    region VARCHAR(50),
    city VARCHAR(80),
    segment VARCHAR(30) NOT NULL,
    registration_date DATE NOT NULL,
    is_active BOOLEAN DEFAULT true,
    total_orders INTEGER DEFAULT 0,
    lifetime_value NUMERIC(14,2) DEFAULT 0
);

INSERT INTO sales.orders (order_date, customer_name, region, city, product_category, product_name, quantity, unit_price, total_amount, discount_pct, payment_method, status, manager)
SELECT
    DATE '2023-01-01' + (random() * 900)::int AS order_date,
    (ARRAY['Иванов А.А.','Петрова М.В.','Сидоров К.Н.','Козлова Е.Д.','Морозов И.П.','Волкова Н.С.','Новиков Д.А.','Соколова А.Р.','Лебедев П.И.','Федорова О.Л.','Попов В.Г.','Кузнецова Т.Б.','Михайлов С.Е.','Андреева Ю.К.','Смирнов Р.Н.'])[1 + (random()*14)::int],
    (ARRAY['Москва','Санкт-Петербург','Новосибирск','Екатеринбург','Казань','Краснодар','Самара','Ростов-на-Дону','Уфа','Воронеж'])[1 + (random()*9)::int],
    (ARRAY['Центральный','Северный','Южный','Западный','Восточный','Пригород'])[1 + (random()*5)::int],
    (ARRAY['Электроника','Одежда','Продукты','Мебель','Книги','Спорт','Авто','Бытовая техника'])[1 + (random()*7)::int],
    (ARRAY['Ноутбук','Смартфон','Телевизор','Куртка','Кроссовки','Стол','Кресло','Шкаф','Учебник','Роман','Мяч','Велосипед','Масло','Шины','Пылесос','Холодильник','Молоко','Хлеб','Рис','Макароны'])[1 + (random()*19)::int],
    1 + (random() * 20)::int,
    100 + (random() * 50000)::int / 100.0,
    0, -- will be computed
    (random() * 25)::int,
    (ARRAY['Карта','Наличные','Перевод','Кредит','Рассрочка'])[1 + (random()*4)::int],
    (ARRAY['Выполнен','В обработке','Отменён','Доставляется','Возврат'])[1 + (random()*4)::int],
    (ARRAY['Алексеев','Борисова','Григорьев','Дмитриева','Егоров','Жукова'])[1 + (random()*5)::int]
FROM generate_series(1, 100000);

UPDATE sales.orders SET total_amount = ROUND(quantity * unit_price * (1 - discount_pct / 100.0), 2);

CREATE INDEX idx_orders_date ON sales.orders(order_date);
CREATE INDEX idx_orders_region ON sales.orders(region);
CREATE INDEX idx_orders_category ON sales.orders(product_category);
CREATE INDEX idx_orders_status ON sales.orders(status);

INSERT INTO sales.customers (name, email, phone, region, city, segment, registration_date, is_active, total_orders, lifetime_value)
SELECT
    (ARRAY['Иванов','Петров','Сидоров','Козлов','Морозов','Волков','Новиков','Соколов','Лебедев','Федоров','Попов','Кузнецов','Михайлов','Андреев','Смирнов'])[1 + (random()*14)::int]
    || ' ' ||
    (ARRAY['А.','Б.','В.','Г.','Д.','Е.','И.','К.','М.','Н.','О.','П.','Р.','С.','Т.'])[1 + (random()*14)::int]
    || (ARRAY['А.','Б.','В.','Г.','Д.'])[1 + (random()*4)::int],
    'user' || n || '@example.com',
    '+7' || (9000000000 + (random() * 999999999)::bigint)::text,
    (ARRAY['Москва','Санкт-Петербург','Новосибирск','Екатеринбург','Казань','Краснодар','Самара','Ростов-на-Дону','Уфа','Воронеж'])[1 + (random()*9)::int],
    (ARRAY['Центральный','Северный','Южный','Западный','Восточный'])[1 + (random()*4)::int],
    (ARRAY['Корпоративный','Розничный','VIP','Оптовый'])[1 + (random()*3)::int],
    DATE '2020-01-01' + (random() * 1500)::int,
    random() > 0.15,
    (random() * 200)::int,
    (random() * 5000000)::int / 100.0
FROM generate_series(1, 5000) AS n;
