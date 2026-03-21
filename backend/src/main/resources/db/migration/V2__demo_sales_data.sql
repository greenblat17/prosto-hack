-- Demo dataset: sales table with generated data

CREATE TABLE ds_demo_sales (
    year         NUMERIC,
    quarter      TEXT,
    month        TEXT,
    date         DATE,
    region       TEXT,
    city         TEXT,
    country      TEXT,
    category     TEXT,
    subcategory  TEXT,
    product      TEXT,
    brand        TEXT,
    client       TEXT,
    segment      TEXT,
    client_type  TEXT,
    channel      TEXT,
    manager      TEXT,
    status       TEXT,
    revenue      NUMERIC,
    quantity     NUMERIC,
    discount     NUMERIC,
    profit       NUMERIC,
    cost         NUMERIC,
    margin       NUMERIC
);

-- Generate 50,000 demo rows using PL/pgSQL
DO $$
DECLARE
    regions     TEXT[] := ARRAY['Москва','Санкт-Петербург','Новосибирск','Екатеринбург','Казань'];
    categories  TEXT[] := ARRAY['Электроника','Одежда','Продукты','Мебель'];
    subcats     TEXT[][] := ARRAY[
        ARRAY['Смартфоны','Ноутбуки','Планшеты'],
        ARRAY['Мужская','Женская','Детская'],
        ARRAY['Молочные','Мясные','Овощи'],
        ARRAY['Столы','Стулья','Шкафы']
    ];
    brands      TEXT[] := ARRAY['Premium','Standard','Economy'];
    segments    TEXT[] := ARRAY['B2B','B2C','Enterprise'];
    cl_types    TEXT[] := ARRAY['Новый','Постоянный','VIP'];
    channels    TEXT[] := ARRAY['Онлайн','Офлайн','Маркетплейс'];
    managers    TEXT[] := ARRAY['Иванов А.','Петрова М.','Сидоров К.','Козлова Е.','Морозов Д.'];
    statuses    TEXT[] := ARRAY['Завершён','В обработке','Отменён'];
    months      TEXT[] := ARRAY['Январь','Февраль','Март','Апрель','Май','Июнь','Июль','Август','Сентябрь','Октябрь','Ноябрь','Декабрь'];

    v_region    TEXT;
    v_cat       TEXT;
    v_cat_idx   INT;
    v_subcat    TEXT;
    v_month_idx INT;
    v_year      INT;
    v_revenue   NUMERIC;
    v_cost      NUMERIC;
    v_profit    NUMERIC;
    v_quantity  INT;
    v_discount  NUMERIC;
    v_margin    NUMERIC;
BEGIN
    FOR i IN 1..50000 LOOP
        v_region    := regions[1 + floor(random() * array_length(regions, 1))::int];
        v_cat_idx   := 1 + floor(random() * array_length(categories, 1))::int;
        v_cat       := categories[v_cat_idx];
        v_subcat    := subcats[v_cat_idx][1 + floor(random() * 3)::int];
        v_month_idx := floor(random() * 12)::int;
        v_year      := 2023 + floor(random() * 3)::int;
        v_quantity  := 1 + floor(random() * 50)::int;
        v_revenue   := round((1000 + random() * 499000)::numeric, 0);
        v_cost      := round(v_revenue * (0.3 + random() * 0.4)::numeric, 0);
        v_profit    := v_revenue - v_cost;
        v_discount  := round((random() * 30)::numeric, 2);
        v_margin    := CASE WHEN v_revenue > 0 THEN round((v_profit / v_revenue * 100)::numeric, 2) ELSE 0 END;

        INSERT INTO ds_demo_sales VALUES (
            v_year,
            'Q' || (v_month_idx / 3 + 1),
            months[v_month_idx + 1],
            make_date(v_year, v_month_idx + 1, 1 + floor(random() * 27)::int),
            v_region,
            v_region,
            'Россия',
            v_cat,
            v_subcat,
            'Продукт ' || (1 + floor(random() * 50)::int),
            brands[1 + floor(random() * array_length(brands, 1))::int],
            'Клиент ' || (1 + floor(random() * 100)::int),
            segments[1 + floor(random() * array_length(segments, 1))::int],
            cl_types[1 + floor(random() * array_length(cl_types, 1))::int],
            channels[1 + floor(random() * array_length(channels, 1))::int],
            managers[1 + floor(random() * array_length(managers, 1))::int],
            statuses[1 + floor(random() * array_length(statuses, 1))::int],
            v_revenue,
            v_quantity,
            v_discount,
            v_profit,
            v_cost,
            v_margin
        );
    END LOOP;
END $$;

-- Indexes for analytical queries
CREATE INDEX idx_demo_sales_region     ON ds_demo_sales(region);
CREATE INDEX idx_demo_sales_category   ON ds_demo_sales(category);
CREATE INDEX idx_demo_sales_month      ON ds_demo_sales(month);
CREATE INDEX idx_demo_sales_year       ON ds_demo_sales(year);
CREATE INDEX idx_demo_sales_channel    ON ds_demo_sales(channel);
CREATE INDEX idx_demo_sales_segment    ON ds_demo_sales(segment);
CREATE INDEX idx_demo_sales_manager    ON ds_demo_sales(manager);

-- Register demo dataset in metadata
INSERT INTO datasets (id, name, table_name, row_count)
VALUES ('00000000-0000-0000-0000-000000000001', 'Демо: Продажи', 'ds_demo_sales', 50000);

INSERT INTO dataset_columns (dataset_id, column_name, display_name, field_type, category, ordinal) VALUES
('00000000-0000-0000-0000-000000000001', 'year',        'Год',            'NUMBER',  'Время',      0),
('00000000-0000-0000-0000-000000000001', 'quarter',     'Квартал',        'STRING',  'Время',      1),
('00000000-0000-0000-0000-000000000001', 'month',       'Месяц',          'STRING',  'Время',      2),
('00000000-0000-0000-0000-000000000001', 'date',        'Дата',           'DATE',    'Время',      3),
('00000000-0000-0000-0000-000000000001', 'region',      'Регион',         'STRING',  'География',  4),
('00000000-0000-0000-0000-000000000001', 'city',        'Город',          'STRING',  'География',  5),
('00000000-0000-0000-0000-000000000001', 'country',     'Страна',         'STRING',  'География',  6),
('00000000-0000-0000-0000-000000000001', 'category',    'Категория',      'STRING',  'Продукт',    7),
('00000000-0000-0000-0000-000000000001', 'subcategory', 'Подкатегория',   'STRING',  'Продукт',    8),
('00000000-0000-0000-0000-000000000001', 'product',     'Продукт',        'STRING',  'Продукт',    9),
('00000000-0000-0000-0000-000000000001', 'brand',       'Бренд',          'STRING',  'Продукт',    10),
('00000000-0000-0000-0000-000000000001', 'client',      'Клиент',         'STRING',  'Клиент',     11),
('00000000-0000-0000-0000-000000000001', 'segment',     'Сегмент',        'STRING',  'Клиент',     12),
('00000000-0000-0000-0000-000000000001', 'client_type', 'Тип клиента',    'STRING',  'Клиент',     13),
('00000000-0000-0000-0000-000000000001', 'channel',     'Канал продаж',   'STRING',  'Продажи',    14),
('00000000-0000-0000-0000-000000000001', 'manager',     'Менеджер',       'STRING',  'Продажи',    15),
('00000000-0000-0000-0000-000000000001', 'status',      'Статус',         'STRING',  'Продажи',    16),
('00000000-0000-0000-0000-000000000001', 'revenue',     'Выручка',        'NUMBER',  'Метрики',    17),
('00000000-0000-0000-0000-000000000001', 'quantity',    'Количество',     'NUMBER',  'Метрики',    18),
('00000000-0000-0000-0000-000000000001', 'discount',    'Скидка',         'NUMBER',  'Метрики',    19),
('00000000-0000-0000-0000-000000000001', 'profit',      'Прибыль',        'NUMBER',  'Метрики',    20),
('00000000-0000-0000-0000-000000000001', 'cost',        'Себестоимость',  'NUMBER',  'Метрики',    21),
('00000000-0000-0000-0000-000000000001', 'margin',      'Маржа',          'NUMBER',  'Метрики',    22);
