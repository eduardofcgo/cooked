alter table account alter column private drop default;
--;;
alter table account alter column private type int using private::int;
--;;
alter table account alter column private set default 0;
