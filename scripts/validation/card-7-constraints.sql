-- Run only against card-7.compose.yml's disposable database, after card-7.ps1.
-- psql variables: tripId and expenseId. All attempted writes are rolled back.
BEGIN;
CREATE TEMP TABLE validation_context AS SELECT :'tripId'::uuid AS trip_id, :'expenseId'::uuid AS expense_id;
DO $$
DECLARE
    selected_trip uuid;
    selected_expense uuid;
BEGIN
    SELECT trip_id,expense_id INTO selected_trip,selected_expense FROM validation_context;
    IF NOT EXISTS (SELECT 1 FROM budgets.transport_expenses WHERE id=selected_expense AND trip_id=selected_trip)
        THEN RAISE EXCEPTION 'Validation fixture is missing'; END IF;
    BEGIN
        UPDATE budgets.transport_expenses SET actual_amount=-1 WHERE id=selected_expense;
        RAISE EXCEPTION 'Negative amount was accepted';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS negative amount'; END;
    BEGIN
        UPDATE budgets.transport_expenses SET actual_currency='USD',actual_amount_usd=999 WHERE id=selected_expense;
        RAISE EXCEPTION 'Inconsistent USD amount was accepted';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS USD consistency'; END;
    BEGIN
        UPDATE budgets.transport_expenses SET actual_conversion_note=NULL WHERE id=selected_expense;
        RAISE EXCEPTION 'Foreign conversion without provenance was accepted';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS conversion provenance'; END;
    BEGIN
        UPDATE budgets.transport_expenses SET leg_id=gen_random_uuid() WHERE id=selected_expense;
        RAISE EXCEPTION 'Invalid leg ownership was accepted';
    EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'PASS leg ownership'; END;
    BEGIN
        UPDATE budgets.transport_expenses SET category='HOTELS' WHERE id=selected_expense;
        RAISE EXCEPTION 'Non-transport category was accepted';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS transport categories'; END;
    BEGIN
        INSERT INTO budgets.transport_expenses
        SELECT (jsonb_populate_record(NULL::budgets.transport_expenses,
            to_jsonb(e)||jsonb_build_object('id',gen_random_uuid()))).*
        FROM budgets.transport_expenses e WHERE e.id=selected_expense;
        RAISE EXCEPTION 'Duplicate accounting reference was accepted';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS unique accounting reference'; END;
    BEGIN
        UPDATE budgets.budget_limits SET amount_usd=-1 WHERE trip_id=selected_trip;
        RAISE EXCEPTION 'Negative cap was accepted';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS non-negative caps'; END;
END $$;
ROLLBACK;
SELECT '7 database constraint checks passed';
