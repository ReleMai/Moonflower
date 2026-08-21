create table if not exists accounts (
    id text primary key,
    name text not null,
    username text not null,
    encrypted_secret text not null,
    character_name text,
    created_at text not null,
    updated_at text not null
);

create table if not exists bots (
    id text primary key,
    name text not null,
    account_id text,
    client_install_path text not null,
    preferred_character text,
    preferred_world text,
    profile_name text,
    launch_command text,
    status text not null,
    registration_secret text,
    takeover_active integer not null default 0,
    last_state_json text,
    created_at text not null,
    updated_at text not null
);

create table if not exists task_presets (
    id text primary key,
    name text not null,
    action_type text not null,
    params_json text not null,
    created_at text not null
);

create table if not exists route_presets (
    id text primary key,
    name text not null,
    route_json text not null,
    created_at text not null
);

create table if not exists tasks (
    id text primary key,
    bot_id text not null,
    action_type text not null,
    params_json text not null,
    status text not null,
    queued_at text not null,
    started_at text,
    completed_at text,
    error_message text
);

create table if not exists screenshots (
    id text primary key,
    bot_id text not null,
    file_name text not null,
    media_type text not null,
    saved integer not null default 1,
    metadata_json text not null,
    created_at text not null
);

create table if not exists audit_events (
    id integer primary key autoincrement,
    bot_id text,
    actor text not null,
    event_type text not null,
    details_json text not null,
    created_at text not null
);

create table if not exists bot_activity_events (
    id integer primary key autoincrement,
    bot_id text not null,
    source text not null,
    category text not null,
    message text not null,
    details_json text not null,
    created_at text not null
);

create table if not exists media_clips (
    id text primary key,
    bot_id text not null,
    file_name text not null,
    media_type text not null,
    trigger_type text not null,
    reason text,
    duration_seconds integer not null,
    metadata_json text not null,
    created_at text not null
);
