-- V014: 多端统一签署 — 增加 sign_source 字段
ALTER TABLE contracts ADD COLUMN sign_source VARCHAR(16) DEFAULT NULL COMMENT '签署发起端: H5/MINI/APP';
