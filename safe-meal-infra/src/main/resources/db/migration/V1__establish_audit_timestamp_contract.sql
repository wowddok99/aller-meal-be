do $$
begin
    execute format('alter database %I set timezone to ''UTC''', current_database());
end
$$;

create function set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = current_timestamp;
    return new;
end;
$$;
