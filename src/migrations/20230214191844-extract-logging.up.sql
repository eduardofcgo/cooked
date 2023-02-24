create table extract(
    account varchar(16),
    url varchar(2048) not null,
    item jsonb,
    created timestamp default now(),
    foreign key(account) references account(username)
)
