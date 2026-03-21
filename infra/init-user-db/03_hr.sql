CREATE TABLE hr.employees (
    employee_id SERIAL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    department VARCHAR(50) NOT NULL,
    position VARCHAR(80) NOT NULL,
    hire_date DATE NOT NULL,
    salary NUMERIC(12,2) NOT NULL,
    bonus NUMERIC(10,2) DEFAULT 0,
    is_remote BOOLEAN DEFAULT false,
    city VARCHAR(80),
    age INTEGER,
    gender VARCHAR(10)
);

INSERT INTO hr.employees (full_name, department, position, hire_date, salary, bonus, is_remote, city, age, gender)
SELECT
    (ARRAY['Иванов','Петров','Сидоров','Козлов','Морозов','Волков','Новиков','Соколов','Лебедев','Федоров'])[1 + (random()*9)::int]
    || ' ' ||
    (ARRAY['Алексей','Мария','Дмитрий','Елена','Сергей','Ольга','Андрей','Наталья','Павел','Юлия'])[1 + (random()*9)::int]
    || ' ' ||
    (ARRAY['Александрович','Викторовна','Николаевич','Дмитриевна','Игоревич'])[1 + (random()*4)::int],
    (ARRAY['IT','Продажи','Маркетинг','Финансы','HR','Логистика','Юридический','Производство'])[1 + (random()*7)::int],
    (ARRAY['Менеджер','Специалист','Старший специалист','Руководитель','Аналитик','Разработчик','Дизайнер','Бухгалтер','Юрист','Директор'])[1 + (random()*9)::int],
    DATE '2015-01-01' + (random() * 3500)::int,
    30000 + (random() * 270000)::int,
    (random() * 100000)::int,
    random() > 0.6,
    (ARRAY['Москва','Санкт-Петербург','Новосибирск','Екатеринбург','Казань','Краснодар'])[1 + (random()*5)::int],
    22 + (random() * 43)::int,
    (ARRAY['М','Ж'])[1 + (random()*1)::int]
FROM generate_series(1, 2000);
