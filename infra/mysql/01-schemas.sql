CREATE DATABASE IF NOT EXISTS identity;
CREATE DATABASE IF NOT EXISTS messaging;
CREATE DATABASE IF NOT EXISTS analytics;

CREATE USER IF NOT EXISTS 'halozalo'@'%' IDENTIFIED BY 'halozalo';
GRANT ALL ON identity.* TO 'halozalo'@'%';
GRANT ALL ON messaging.* TO 'halozalo'@'%';
GRANT ALL ON analytics.* TO 'halozalo'@'%';
FLUSH PRIVILEGES;
