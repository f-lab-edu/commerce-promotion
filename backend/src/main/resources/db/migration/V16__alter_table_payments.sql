-- payments 테이블에 payment_key null 허용
ALTER TABLE payments MODIFY COLUMN payment_key varchar(20);
