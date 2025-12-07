create database netdisk_db;
use netdisk_db;

create table if not exists public.sys_user
(
    user_id     bigserial
        primary key,
    username    varchar(50)  not null
        unique,
    password    varchar(100) not null,
    total_space bigint    default 1073741824,
    used_space  bigint    default 0,
    created_at  timestamp default CURRENT_TIMESTAMP
);

alter table public.sys_user
    owner to postgres;

create table if not exists public.sys_log
(
    log_id     bigserial
        primary key,
    user_id    bigint      not null,
    operation  varchar(50) not null,
    details    text,
    created_at timestamp default CURRENT_TIMESTAMP,
    client_ip  varchar(128),
    username   varchar(50) not null,
    file_id    bigint      not null
);

comment on column public.sys_log.client_ip is '操作时的客户端ip';

alter table public.sys_log
    owner to postgres;

create index if not exists sys_log_user_id_index
    on public.sys_log (user_id);

create index if not exists sys_log_file_id_index
    on public.sys_log (file_id);

create table if not exists public.sys_file
(
    file_id    bigserial
        primary key,
    real_path  varchar(512),
    file_size  bigint    default 0,
    file_type  varchar(50),
    created_at timestamp default CURRENT_TIMESTAMP,
    updated_at timestamp default CURRENT_TIMESTAMP
);

alter table public.sys_file
    owner to postgres;

create table if not exists public.sys_user_file
(
    id             bigserial
        primary key,
    user_id        bigint                                          not null
        references public.sys_user,
    file_id        bigint                                          not null
        references public.sys_file,
    role           varchar(30) default 'VIEWER'::character varying not null,
    file_name      varchar(255)                                    not null,
    parent_file_id bigint      default 0,
    is_folder      boolean     default false,
    created_at     timestamp   default CURRENT_TIMESTAMP,
    updated_at     timestamp   default CURRENT_TIMESTAMP,
    constraint sys_user_file_user_id_parent_id_file_name_key
        unique (user_id, parent_file_id, file_name)
);

alter table public.sys_user_file
    owner to postgres;

create index if not exists idx_user_parent
    on public.sys_user_file (user_id, parent_file_id);

