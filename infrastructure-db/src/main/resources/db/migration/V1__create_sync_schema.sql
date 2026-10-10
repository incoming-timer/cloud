create table timers (
    id uuid primary key,
    user_id uuid not null,
    title varchar(200) not null,
    duration_ms bigint not null check (duration_ms > 0),
    repeat_enabled boolean not null default false,
    state_type varchar(16) not null check (state_type in ('paused', 'running')),
    remaining_ms bigint,
    started_at timestamptz,
    expires_at timestamptz,
    version bigint not null check (version > 0),
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    deleted_at timestamptz,
    constraint timers_state_fields_check check (
        (state_type = 'paused' and remaining_ms is not null and remaining_ms >= 0 and started_at is null and expires_at is null)
        or
        (state_type = 'running' and remaining_ms is null and started_at is not null and expires_at is not null and expires_at >= started_at)
    )
);

create index timers_user_updated_idx on timers (user_id, updated_at desc);
create index timers_user_active_idx on timers (user_id, id) where deleted_at is null;

create table timer_operations (
    user_id uuid not null,
    operation_id uuid not null,
    timer_id uuid not null,
    device_id uuid not null,
    operation_type varchar(16) not null check (
        operation_type in ('create', 'edit', 'start', 'pause', 'resume', 'reset', 'complete', 'delete')
    ),
    expected_version bigint check (expected_version >= 0),
    occurred_at timestamptz not null,
    payload jsonb not null default '{}'::jsonb,
    response_status integer not null check (response_status between 200 and 599),
    response_body jsonb not null,
    created_at timestamptz not null default current_timestamp,
    primary key (user_id, operation_id),
    constraint timer_operations_timer_unique unique (user_id, operation_id, timer_id)
);

create index timer_operations_timer_created_idx on timer_operations (user_id, timer_id, created_at desc);

create table change_log (
    change_seq bigint generated always as identity primary key,
    user_id uuid not null,
    operation_id uuid not null,
    timer_id uuid not null,
    record jsonb not null,
    created_at timestamptz not null default current_timestamp,
    constraint change_log_operation_fk foreign key (user_id, operation_id, timer_id)
        references timer_operations (user_id, operation_id, timer_id),
    constraint change_log_user_sequence_unique unique (user_id, change_seq)
);

create index change_log_user_cursor_idx on change_log (user_id, change_seq);

create table outbox_events (
    id uuid primary key,
    user_id uuid not null,
    change_seq bigint not null,
    event_type varchar(64) not null,
    payload jsonb not null,
    created_at timestamptz not null default current_timestamp,
    published_at timestamptz,
    attempt_count integer not null default 0 check (attempt_count >= 0),
    next_attempt_at timestamptz not null default current_timestamp,
    constraint outbox_events_change_fk foreign key (user_id, change_seq)
        references change_log (user_id, change_seq)
);

create unique index outbox_events_change_unique on outbox_events (change_seq);
create index outbox_events_pending_idx on outbox_events (next_attempt_at, created_at)
    where published_at is null;
