-- SQL Script to add boolean columns to the transactions table
-- MS SQL Server syntax

-- Add novelty column to the transactions table
ALTER TABLE transactions
ADD novelty BIT NOT NULL DEFAULT 0;

-- Add matrix column to the transactions table
ALTER TABLE transactions
ADD matrix BIT NOT NULL DEFAULT 0;

-- Add core column to the transactions table
ALTER TABLE transactions
ADD core BIT NOT NULL DEFAULT 0;

-- The BIT data type is used to represent boolean values in MS SQL Server
-- Default value is set to 0 (FALSE)
-- NOT NULL constraint ensures the columns always have a value
