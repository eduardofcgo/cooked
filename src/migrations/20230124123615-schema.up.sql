create table session_store(
  session_id varchar(36) primary key,
  value bytea
);
--;;
create table account(
  username varchar(16) primary key,
  password varchar(1000) not null
);
--;;
create table item(
  id varchar(36) primary key,
  account varchar(16) not null,
  url varchar(2048),
  title varchar(512) not null,
  description varchar(10000),
  extra varchar(10000),
  image_url varchar(2048),
  video_url varchar(2048),
  created timestamp default now(),
  foreign key(account) references account(username) on delete cascade
);
--;;
create unique index idx_item_account_url on item(account, url);
--;;
create table item_keyword(
  keyword varchar(50) not null,
  item_id varchar(36) not null,
  primary key(item_id, keyword),
  foreign key(item_id) references item(id) on delete cascade
);
--;;
